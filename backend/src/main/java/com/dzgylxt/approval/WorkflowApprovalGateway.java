package com.dzgylxt.approval;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.approval.ApprovalNode;
import com.dzgylxt.entity.approval.ApprovalNodeDef;
import com.dzgylxt.entity.approval.ApprovalRecord;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.approval.ApprovalFlowConfigService.CachedFlow;
import com.dzgylxt.enums.ApprovalStatus;
import com.dzgylxt.enums.ApprovalNodeStatus;
import com.dzgylxt.mapper.approval.ApprovalNodeMapper;
import com.dzgylxt.mapper.approval.ApprovalRecordMapper;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.security.LoginUser;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.INoticeService;
import com.dzgylxt.service.impl.approval.ApprovalTaskServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真实审批引擎（P4 设计 §2，表驱动节点流转 + 角色解析审批人 + 待办/意见/并发校验/审计/站内通知）。
 *
 * <p><b>契约不变声明（设计 §2.1 铁律）</b>：{@link ApprovalGateway#create/callback} 签名零变更，
 * 本类以 {@code @Primary} 替换 {@link LocalApprovalGateway}（后者降级为测试桩）；
 * {@link ApprovalTaskSpec}/{@link ApprovalDecision}/{@link ApprovalCallbackHandler}
 * 与 8 个 bizType 业务发起点零改动。</p>
 *
 * <p><b>节点流转状态机（设计 §2.5）</b>：</p>
 * <ol>
 *   <li>create：读配置（无配置/停用 fail-fast）→ 按 payloadJson.amount 匹配节点链 →
 *       落 task（IN_PROGRESS + applicant/amount/flow_version）→ 逐节点写 approval_node 快照 →
 *       afterCommit 通知首节点候选人；</li>
 *   <li>callback：终态幂等忽略 → 候选人校验（非候选 2004 不留痕）→ 驳回（意见必填，节点链全 SKIPPED，
 *       分发 onRejected，通知申请人）/ 通过（节点行条件更新 → 未完推进下一节点不分发 handler，
 *       已完 task=APPROVED 分发 onApproved，通知申请人）；</li>
 *   <li>并发防重审（设计 §2.6）：task/node 双 version 条件更新，冲突 DATA_CONFLICT(3002)；
 *       状态机前置校验 + 幂等契约三层双保险；</li>
 *   <li>在途任务按提交时配置走完（设计 §2.9）：节点链结构冻结于快照，候选人运行期实时解析。</li>
 * </ol>
 *
 * <p><b>终态留痕口径（偏差登记）</b>：本仓库无 operation_log 表/底座（grep 实证），
 * 终态审计以 approval_record（含 approver_name 快照）+ approval_node 状态行承载，
 * 审计面覆盖 AC（两级审批 ≥2 条 record）。</p>
 */
@Slf4j
@Primary
@Service
public class WorkflowApprovalGateway implements ApprovalGateway {

    /** approval_node.status：待审。 */
    public static final ApprovalNodeStatus NODE_PENDING = ApprovalNodeStatus.PENDING;
    /** approval_node.status：已审结（通过时回填 approver/action）。 */
    public static final ApprovalNodeStatus NODE_DONE = ApprovalNodeStatus.DONE;
    /** approval_node.status：任务驳回后被跳过（未走到）。 */
    public static final ApprovalNodeStatus NODE_SKIPPED = ApprovalNodeStatus.SKIPPED;

    /** approval_node.sign_type：或签（默认）。 */
    public static final String SIGN_ANY = "ANY";
    /** approval_node.sign_type：会签（预留，Q13）。 */
    public static final String SIGN_ALL = "ALL";

    /** approval_record.action 常量。 */
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_REJECT = "REJECT";

    private final ApprovalFlowConfigService flowConfigService;
    private final ApproverResolver approverResolver;
    private final ApprovalTaskServiceImpl approvalTaskService;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final ApprovalNodeMapper approvalNodeMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ObjectProvider<ApprovalCallbackHandler> handlers;
    private final INoticeService noticeService;

    public WorkflowApprovalGateway(ApprovalFlowConfigService flowConfigService,
                                   ApproverResolver approverResolver,
                                   ApprovalTaskServiceImpl approvalTaskService,
                                   ApprovalTaskMapper approvalTaskMapper,
                                   ApprovalNodeMapper approvalNodeMapper,
                                   ApprovalRecordMapper approvalRecordMapper,
                                   ObjectProvider<ApprovalCallbackHandler> handlers,
                                   INoticeService noticeService) {
        this.flowConfigService = flowConfigService;
        this.approverResolver = approverResolver;
        this.approvalTaskService = approvalTaskService;
        this.approvalTaskMapper = approvalTaskMapper;
        this.approvalNodeMapper = approvalNodeMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.handlers = handlers;
        this.noticeService = noticeService;
    }

    // ==================== create（发起，事务内） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ApprovalTaskSpec spec) {
        // 1. 配置 fail-fast：无配置或停用 → 不留幽灵任务
        CachedFlow flow = flowConfigService.getFlow(spec.getBizType());
        if (flow == null || flow.getFlowDef() == null) {
            throw new BizException(ResultCode.STATUS_INVALID, "审批流未配置：" + spec.getBizType());
        }
        if (!Integer.valueOf(1).equals(flow.getFlowDef().getEnabled())) {
            throw new BizException(ResultCode.STATUS_INVALID, "审批流已停用，新任务拒绝创建：" + spec.getBizType());
        }
        // 2. 金额区间匹配节点集（§1.2.2 语义），按 seq 升序构成节点链；链空 fail-fast
        BigDecimal amount = extractAmount(spec.getPayloadJson());
        List<ApprovalNodeDef> chain = matchChain(flow.getNodeDefs(), amount);
        if (chain.isEmpty()) {
            throw new BizException(ResultCode.STATUS_INVALID,
                    "审批流无命中节点（金额区间不匹配）：" + spec.getBizType());
        }
        // 3. 落任务：IN_PROGRESS（CREATED 态仅内部瞬时，对外语义从第一节点即有待办）
        LoginUser creator = UserContext.get();
        ApprovalTask task = new ApprovalTask();
        task.setBizType(spec.getBizType());
        task.setBizId(spec.getBizId());
        task.setFlowKey(spec.getBizType());
        task.setStatus(ApprovalStatus.IN_PROGRESS);
        task.setCurrentNode(chain.get(0).getNodeCode());
        task.setPayloadJson(spec.getPayloadJson());
        task.setRemark(spec.getTitle());
        task.setApplicant(spec.getApplicant() != null ? spec.getApplicant()
                : (creator == null ? null : creator.getUsername()));
        task.setApplicantId(creator == null ? null : creator.getId());
        task.setAmount(amount);
        task.setFlowVersion(flow.getFlowDef().getFlowVersion() == null ? 1 : flow.getFlowDef().getFlowVersion());
        task.setVersion(0);
        approvalTaskService.save(task);
        // 4. 逐节点写快照行（node_code/seq/sign_type 冻结；候选人运行期实时解析）
        for (ApprovalNodeDef def : chain) {
            ApprovalNode snapshot = new ApprovalNode();
            snapshot.setTaskId(task.getId());
            snapshot.setNodeCode(def.getNodeCode());
            snapshot.setNodeDef(def.getNodeName());
            snapshot.setSeq(def.getSeq());
            snapshot.setSignType(def.getSignType() == null ? SIGN_ANY : def.getSignType());
            snapshot.setStatus(NODE_PENDING);
            snapshot.setVersion(0);
            approvalNodeMapper.insert(snapshot);
        }
        // 5. afterCommit 通知首节点候选人（事务解耦，通知失败不回滚）
        ApprovalNodeDef firstDef = chain.get(0);
        Long taskId = task.getId();
        String title = spec.getTitle();
        String bizType = spec.getBizType();
        Long applicantId = task.getApplicantId();
        afterCommit(() -> notifyNodeCandidates(taskId, firstDef, applicantId,
                "您有新审批待办：" + safe(title)));
        return taskId;
    }

    // ==================== callback（审批动作，事务内） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void callback(Long taskId, ApprovalDecision decision, String comment) {
        ApprovalTask task = approvalTaskMapper.selectById(taskId);
        if (task == null) {
            // QA #29 契约延续：不存在的任务必须显式失败（不允许"幽灵成功"）
            throw new BizException(ResultCode.NOT_FOUND, "审批任务不存在：" + taskId);
        }
        // 幂等：仅 CREATED / IN_PROGRESS 可流转，终态重复回调静默忽略（契约原样保留）
        if (task.getStatus() != ApprovalStatus.CREATED
                && task.getStatus() != ApprovalStatus.IN_PROGRESS) {
            return;
        }
        // 驳回意见必填校验上移至网关（Controller 既有校验保留为第一道）
        if (decision == ApprovalDecision.REJECTED
                && (comment == null || comment.isBlank())) {
            throw new BizException(ResultCode.PARAM_ERROR, "驳回必须填写审批意见");
        }

        ApprovalNode node = findNodeSnapshot(taskId, task.getCurrentNode());
        if (node == null) {
            // 存量兼容：Local 桩时代创建的在途任务无节点快照行 → 沿用终态直转 + 分发（回归保障）
            legacyTerminalize(task, decision, comment);
            return;
        }

        // 候选人校验（§2.4）：非候选 FORBIDDEN 且不留痕（AC①）；候选人运行期实时解析
        ApprovalNodeDef nodeDef = findNodeDef(task.getFlowKey(), task.getCurrentNode());
        if (nodeDef == null) {
            throw new BizException(ResultCode.STATUS_INVALID,
                    "节点定义缺失，无法解析审批人：flowKey=" + task.getFlowKey()
                            + " nodeCode=" + task.getCurrentNode());
        }
        Set<Long> candidates = approverResolver.resolveCandidates(nodeDef, task.getApplicantId());
        Long approver = UserContext.getCurrentUserId();
        if (approver == null || !candidates.contains(approver)) {
            throw new BizException(ResultCode.FORBIDDEN, "您不是当前节点候选人，无权审批");
        }

        if (decision == ApprovalDecision.REJECTED) {
            reject(task, node, approver, comment);
        } else {
            approve(task, node, nodeDef, candidates, approver, comment);
        }
    }

    /** 驳回：任务终态 → 全节点未完成行 SKIPPED → record 留痕 → 分发 onRejected → 通知申请人。 */
    private void reject(ApprovalTask task, ApprovalNode node, Long approver, String comment) {
        int updated = approvalTaskMapper.casTransition(task.getId(), task.getVersion(),
                ApprovalStatus.REJECTED, task.getCurrentNode(), task.getRemark());
        if (updated == 0) {
            throw new BizException(ResultCode.DATA_CONFLICT, "单据已被处理");
        }
        // 全节点未完成行置 SKIPPED（不走后续节点，规格 §3.3；重提=业务方重新 create 新任务）
        approvalNodeMapper.update(null, new LambdaUpdateWrapper<ApprovalNode>()
                .eq(ApprovalNode::getTaskId, task.getId())
                .eq(ApprovalNode::getStatus, NODE_PENDING)
                .ne(ApprovalNode::getId, node.getId())
                .set(ApprovalNode::getStatus, NODE_SKIPPED));
        insertRecord(task, node, ACTION_REJECT, approver, comment);
        dispatch(task, false, comment);
        Long applicantId = task.getApplicantId();
        String title = task.getRemark();
        String bizType = task.getBizType();
        Long taskId = task.getId();
        String summary = safe(comment);
        afterCommit(() -> noticeService.send(applicantId,
                safe(title) + " 已驳回", "您的审批单「" + safe(title) + "」被驳回。意见：" + summary,
                bizType, taskId));
    }

    /** 通过：节点行条件更新 →（会签计数）→ 推进下一节点 / 终态分发。 */
    private void approve(ApprovalTask task, ApprovalNode node, ApprovalNodeDef nodeDef,
                         Set<Long> candidates, Long approver, String comment) {
        // 节点行乐观锁条件更新（version + 仅 PENDING 可完成）——或签竞争恰一成功一 3002
        int nodeUpdated = approvalNodeMapper.casComplete(node.getId(), node.getVersion(),
                approver, ACTION_APPROVE, comment);
        if (nodeUpdated == 0) {
            throw new BizException(ResultCode.DATA_CONFLICT, "单据已被处理");
        }
        insertRecord(task, node, ACTION_APPROVE, approver, comment);

        // 会签（ALL）：全部候选审批方推进（按去重 approve 人数 = 候选人数）；种子全 ANY（Q13 预留）
        if (SIGN_ALL.equals(node.getSignType()) && candidates.size() > 1) {
            long distinctApproves = approvalRecordMapper.selectCount(
                    new LambdaQueryWrapper<ApprovalRecord>()
                            .eq(ApprovalRecord::getTaskId, task.getId())
                            .eq(ApprovalRecord::getNodeId, node.getId())
                            .eq(ApprovalRecord::getAction, ACTION_APPROVE));
            if (distinctApproves < candidates.size()) {
                log.info("[P4-Workflow] 会签未齐，节点保持 PENDING taskId={} node={} {}/{}",
                        task.getId(), node.getNodeCode(), distinctApproves, candidates.size());
                return;
            }
        }

        // 节点链未完 → 推进下一节点（task 条件更新 version+1），不分发 handler
        ApprovalNode next = findNextNode(task.getId(), node.getSeq());
        if (next != null) {
            int updated = approvalTaskMapper.casTransition(task.getId(), task.getVersion(),
                    ApprovalStatus.IN_PROGRESS, next.getNodeCode(), task.getRemark());
            if (updated == 0) {
                throw new BizException(ResultCode.DATA_CONFLICT, "单据已被处理");
            }
            ApprovalNodeDef nextDef = findNodeDef(task.getFlowKey(), next.getNodeCode());
            Long taskId = task.getId();
            String title = task.getRemark();
            String bizType = task.getBizType();
            Long applicantId = task.getApplicantId();
            int passedSeq = node.getSeq() == null ? 1 : node.getSeq();
            afterCommit(() -> {
                if (nextDef != null) {
                    notifyNodeCandidates(taskId, nextDef, applicantId,
                            safe(title) + " 已过第 " + passedSeq + " 节点，待您审批");
                } else {
                    log.warn("[P4-Workflow] 下一节点定义缺失，无法通知 taskId={} nodeCode={}",
                            taskId, next.getNodeCode());
                }
            });
            return;
        }

        // 节点链已完 → task=APPROVED → 分发 onApproved（同事务，语义不变）→ 通知申请人
        int updated = approvalTaskMapper.casTransition(task.getId(), task.getVersion(),
                ApprovalStatus.APPROVED, task.getCurrentNode(), task.getRemark());
        if (updated == 0) {
            throw new BizException(ResultCode.DATA_CONFLICT, "单据已被处理");
        }
        dispatch(task, true, comment);
        Long applicantId = task.getApplicantId();
        String title = task.getRemark();
        String bizType = task.getBizType();
        Long taskId = task.getId();
        afterCommit(() -> noticeService.send(applicantId,
                safe(title) + " 已通过", "您的审批单「" + safe(title) + "」已全部审批通过。",
                bizType, taskId));
    }

    // ==================== 存量兼容（Local 桩时代在途任务，无节点快照行） ====================

    /**
     * 存量任务终态直转：无 approval_node 快照行的在途任务（Local 桩时代遗留）沿用
     * 幂等终态 + handler 分发语义，保障升级日存量任务不被卡死（回归保障，偏差登记）。
     */
    private void legacyTerminalize(ApprovalTask task, ApprovalDecision decision, String comment) {
        log.warn("[P4-Workflow] 任务无节点快照行，走存量兼容终态直转 taskId={} bizType={}",
                task.getId(), task.getBizType());
        boolean approved = decision == ApprovalDecision.APPROVED;
        int updated = approvalTaskMapper.casTransition(task.getId(), task.getVersion(),
                approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED,
                task.getCurrentNode(), task.getRemark());
        if (updated == 0) {
            throw new BizException(ResultCode.DATA_CONFLICT, "单据已被处理");
        }
        insertRecord(task, null, approved ? ACTION_APPROVE : ACTION_REJECT,
                UserContext.getCurrentUserId(), comment);
        dispatch(task, approved, comment);
        Long applicantId = task.getApplicantId();
        String title = task.getRemark();
        String bizType = task.getBizType();
        Long taskId = task.getId();
        afterCommit(() -> noticeService.send(applicantId, safe(title)
                        + (approved ? " 已通过" : " 已驳回"), null, bizType, taskId));
    }

    // ==================== 内部工具 ====================

    /** 按 bizType 查找业务回调处理器（无注册时仅流转任务本身）。 */
    private void dispatch(ApprovalTask task, boolean approved, String comment) {
        ApprovalCallbackHandler handler = findHandler(task.getBizType());
        if (handler == null) {
            return;
        }
        if (approved) {
            handler.onApproved(task.getId(), task.getBizId(), comment);
        } else {
            handler.onRejected(task.getId(), task.getBizId(), comment);
        }
    }

    private ApprovalCallbackHandler findHandler(String bizType) {
        Map<String, ApprovalCallbackHandler> map = new HashMap<>();
        for (ApprovalCallbackHandler h : handlers) {
            map.putIfAbsent(h.bizType(), h);
        }
        return map.get(bizType);
    }

    /** 金额摘要提取：payloadJson.amount（解析失败/缺失返回 null）。 */
    private BigDecimal extractAmount(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return JSONUtil.parseObj(payloadJson).getBigDecimal("amount");
        } catch (Exception e) {
            log.warn("[P4-Workflow] payloadJson.amount 解析失败（忽略金额摘要）: {}", payloadJson);
            return null;
        }
    }

    /**
     * 金额区间匹配（§1.2.2 语义）：(amount_min IS NULL OR amount ≥ min)
     * AND (amount_max IS NULL OR amount < max)；amount 为 null 时仅恒生效节点命中。
     */
    private List<ApprovalNodeDef> matchChain(List<ApprovalNodeDef> defs, BigDecimal amount) {
        List<ApprovalNodeDef> chain = new java.util.ArrayList<>();
        for (ApprovalNodeDef def : defs) {
            if (!Integer.valueOf(1).equals(def.getEnabled())) {
                continue;
            }
            boolean minOk = def.getAmountMin() == null
                    || (amount != null && amount.compareTo(def.getAmountMin()) >= 0);
            boolean maxOk = def.getAmountMax() == null
                    || (amount != null && amount.compareTo(def.getAmountMax()) < 0);
            if (minOk && maxOk) {
                chain.add(def);
            }
        }
        chain.sort(java.util.Comparator.comparing(ApprovalNodeDef::getSeq,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return chain;
    }

    private ApprovalNode findNodeSnapshot(Long taskId, String nodeCode) {
        List<ApprovalNode> rows = approvalNodeMapper.selectList(
                new LambdaQueryWrapper<ApprovalNode>()
                        .eq(ApprovalNode::getTaskId, taskId)
                        .eq(ApprovalNode::getNodeCode, nodeCode)
                        .orderByAsc(ApprovalNode::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ApprovalNode findNextNode(Long taskId, Integer currentSeq) {
        List<ApprovalNode> rows = approvalNodeMapper.selectList(
                new LambdaQueryWrapper<ApprovalNode>()
                        .eq(ApprovalNode::getTaskId, taskId)
                        .eq(ApprovalNode::getStatus, NODE_PENDING)
                        .gt(currentSeq == null, ApprovalNode::getSeq, 0)
                        .orderByAsc(ApprovalNode::getSeq));
        for (ApprovalNode row : rows) {
            if (currentSeq == null || row.getSeq() == null || row.getSeq() > currentSeq) {
                return row;
            }
        }
        return null;
    }

    private ApprovalNodeDef findNodeDef(String flowKey, String nodeCode) {
        if (flowKey == null) {
            return null;
        }
        for (ApprovalNodeDef def : flowConfigService.listNodes(flowKey)) {
            if (def.getNodeCode() != null && def.getNodeCode().equals(nodeCode)) {
                return def;
            }
        }
        return null;
    }

    private void insertRecord(ApprovalTask task, ApprovalNode node, String action,
                              Long approver, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setTaskId(task.getId());
        record.setNodeId(node == null ? null : node.getId());
        record.setApprover(approver);
        record.setApproverName(UserContext.getCurrentUsername());
        record.setAction(action);
        record.setComment(comment);
        approvalRecordMapper.insert(record);
    }

    /** 通知指定节点候选审批人（每人一条；候选人实时解析）。 */
    private void notifyNodeCandidates(Long taskId, ApprovalNodeDef nodeDef,
                                      Long applicantId, String content) {
        try {
            Set<Long> candidates = approverResolver.resolveCandidates(nodeDef, applicantId);
            for (Long userId : new LinkedHashSet<>(candidates)) {
                noticeService.send(userId, "审批待办提醒", content, nodeDef.getFlowKey(), taskId);
            }
        } catch (Exception e) {
            // 通知失败不回滚审批事务（AC③）：afterCommit 内兜底 try-catch
            log.warn("[P4-Workflow] 节点候选人通知失败 taskId={} nodeCode={}",
                    taskId, nodeDef.getNodeCode(), e);
        }
    }

    /** afterCommit 回调（无事务时直接执行，兼容单测/REQUIRES_NEW 外部模板）。 */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
