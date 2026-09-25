package com.dzgylxt.service.impl.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalFlowConfigService;
import com.dzgylxt.approval.ApproverResolver;
import com.dzgylxt.approval.WorkflowApprovalGateway;
import com.dzgylxt.entity.approval.ApprovalNode;
import com.dzgylxt.entity.approval.ApprovalNodeDef;
import com.dzgylxt.entity.approval.ApprovalRecord;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.enums.ApprovalStatus;
import com.dzgylxt.mapper.approval.ApprovalNodeMapper;
import com.dzgylxt.mapper.approval.ApprovalRecordMapper;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.vo.approval.ApprovalDoneVO;
import com.dzgylxt.vo.approval.ApprovalTaskDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批单据服务（P4 扩展：工作台 todo/done 查询、候选人过滤、详情组装）。
 *
 * <p><b>待办候选人过滤（规格 §3.2 服务端强制）</b>：SQL 先按"用户角色可审的
 * flow_key × node_code"粗过滤分页（ROLE/USER 类型精确；DEPT_HEAD_OF_APPLICANT
 * 依赖任务 applicantId 逐任务解析，页内做精确二次过滤，total 以 SQL 粗过滤计数为准——
 * 该类型占比小且仅可能多计不可漏计，页内不会漏）。</p>
 */
@Slf4j
@Service
public class ApprovalTaskServiceImpl extends ServiceImpl<ApprovalTaskMapper, ApprovalTask> {

    private final ApprovalNodeMapper approvalNodeMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ObjectProvider<ApprovalFlowConfigService> flowConfigProvider;
    private final ObjectProvider<ApproverResolver> resolverProvider;

    public ApprovalTaskServiceImpl(ApprovalNodeMapper approvalNodeMapper,
                                   ApprovalRecordMapper approvalRecordMapper,
                                   ObjectProvider<ApprovalFlowConfigService> flowConfigProvider,
                                   ObjectProvider<ApproverResolver> resolverProvider) {
        this.approvalNodeMapper = approvalNodeMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.flowConfigProvider = flowConfigProvider;
        this.resolverProvider = resolverProvider;
    }

    /**
     * 待办分页（服务端候选人过滤）。单测注入 mock 时经 ObjectProvider 延迟取依赖。
     *
     * @param userId   当前用户 id
     * @param roles    当前用户角色编码集
     * @param username 当前用户名
     * @param bizType  业务类型筛选（可空）
     */
    public IPage<ApprovalTask> pageTodo(Long userId, Set<String> roles, String username,
                                        String bizType, long current, long size) {
        Set<String> flowKeys = new HashSet<>();
        Set<String> nodeCodes = new HashSet<>();
        boolean hasDeptHeadType = collectCandidateNodeKeys(userId, roles, username, flowKeys, nodeCodes);
        Page<ApprovalTask> page = new Page<>(current, size);
        if (flowKeys.isEmpty()) {
            return page;
        }
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<ApprovalTask>()
                .in(ApprovalTask::getFlowKey, flowKeys)
                .in(ApprovalTask::getCurrentNode, nodeCodes)
                .in(ApprovalTask::getStatus, ApprovalStatus.CREATED, ApprovalStatus.IN_PROGRESS)
                .orderByDesc(ApprovalTask::getId);
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(ApprovalTask::getBizType, bizType);
        }
        IPage<ApprovalTask> result = page(page, wrapper);
        // DEPT_HEAD_OF_APPLICANT 类型页内精确二次过滤（申请人部门链逐任务解析）
        if (hasDeptHeadType) {
            List<ApprovalTask> filtered = result.getRecords().stream()
                    .filter(t -> isCandidate(t, userId))
                    .toList();
            result.setRecords(filtered);
        }
        return result;
    }

    /** 已办分页：approval_record WHERE approver=当前用户 + 任务头冗余。 */
    public IPage<ApprovalDoneVO> pageDone(Long userId, String bizType, long current, long size) {
        IPage<ApprovalRecord> recordPage = approvalRecordMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApprover, userId)
                        .orderByDesc(ApprovalRecord::getId));
        List<Long> taskIds = recordPage.getRecords().stream()
                .map(ApprovalRecord::getTaskId).distinct().toList();
        Map<Long, ApprovalTask> taskMap = taskIds.isEmpty() ? Map.of()
                : listByIds(taskIds).stream().collect(Collectors.toMap(ApprovalTask::getId, t -> t));
        IPage<ApprovalDoneVO> result = recordPage.convert(r -> {
            ApprovalDoneVO vo = new ApprovalDoneVO();
            vo.setRecordId(r.getId());
            vo.setTaskId(r.getTaskId());
            vo.setAction(r.getAction());
            vo.setComment(r.getComment());
            vo.setApproverName(r.getApproverName());
            vo.setApprovedAt(r.getCreatedAt());
            ApprovalTask task = taskMap.get(r.getTaskId());
            if (task != null) {
                if (bizType == null || bizType.isBlank() || bizType.equals(task.getBizType())) {
                    vo.setBizType(task.getBizType());
                    vo.setBizId(task.getBizId());
                    vo.setTitle(task.getRemark());
                } else {
                    // bizType 筛选不命中：行保留但业务字段置空（前端按空跳过渲染）
                    vo.setBizType(null);
                }
            }
            return vo;
        });
        if (bizType != null && !bizType.isBlank()) {
            result.getRecords().removeIf(v -> v.getBizType() == null);
            result.setTotal(result.getRecords().size());
        }
        return result;
    }

    /** 任务详情：任务头 + 节点链时间轴 + 审批记录 + 候选人可见性。 */
    public ApprovalTaskDetailVO detail(Long taskId, Long userId, Set<String> roles, String username) {
        ApprovalTask task = getById(taskId);
        if (task == null) {
            return null;
        }
        ApprovalTaskDetailVO vo = new ApprovalTaskDetailVO();
        vo.setTaskId(task.getId());
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setTitle(task.getRemark());
        vo.setApplicant(task.getApplicant());
        vo.setAmount(task.getAmount());
        vo.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        vo.setCurrentNode(task.getCurrentNode());
        vo.setCreatedAt(task.getCreatedAt());

        List<ApprovalNode> nodes = approvalNodeMapper.selectList(
                new LambdaQueryWrapper<ApprovalNode>()
                        .eq(ApprovalNode::getTaskId, taskId)
                        .orderByAsc(ApprovalNode::getSeq));
        Map<String, ApprovalNodeDef> defMap = nodeDefMap(task.getFlowKey());
        List<ApprovalTaskDetailVO.NodeItem> nodeItems = new ArrayList<>();
        for (ApprovalNode node : nodes) {
            ApprovalTaskDetailVO.NodeItem item = new ApprovalTaskDetailVO.NodeItem();
            item.setNodeId(node.getId());
            item.setNodeCode(node.getNodeCode());
            ApprovalNodeDef def = defMap.get(node.getNodeCode());
            item.setNodeName(def != null && def.getNodeName() != null ? def.getNodeName() : node.getNodeDef());
            item.setSeq(node.getSeq());
            item.setSignType(node.getSignType());
            item.setStatus(node.getStatus());
            item.setApprover(node.getApprover());
            item.setAction(node.getAction());
            item.setComment(node.getComment());
            nodeItems.add(item);
        }
        vo.setNodes(nodeItems);

        vo.setRecords(approvalRecordMapper.selectList(
                        new LambdaQueryWrapper<ApprovalRecord>()
                                .eq(ApprovalRecord::getTaskId, taskId)
                                .orderByAsc(ApprovalRecord::getId))
                .stream().map(r -> {
                    ApprovalTaskDetailVO.RecordItem item = new ApprovalTaskDetailVO.RecordItem();
                    item.setNodeId(r.getNodeId());
                    item.setApprover(r.getApprover());
                    item.setApproverName(r.getApproverName());
                    item.setAction(r.getAction());
                    item.setComment(r.getComment());
                    item.setCreatedAt(r.getCreatedAt());
                    return item;
                }).toList());

        // 候选人可见性：当前节点候选人可审批
        ApprovalNode current = nodes.stream()
                .filter(n -> n.getNodeCode() != null && n.getNodeCode().equals(task.getCurrentNode()))
                .findFirst().orElse(null);
        boolean active = task.getStatus() == ApprovalStatus.CREATED
                || task.getStatus() == ApprovalStatus.IN_PROGRESS;
        vo.setCanApprove(active && current != null && isCandidate(task, userId));
        return vo;
    }

    /** 精确候选人判定（逐任务：DEPT_HEAD_OF_APPLICANT 依赖 applicantId 解析）。 */
    public boolean isCandidate(ApprovalTask task, Long userId) {
        if (userId == null) {
            return false;
        }
        ApprovalFlowConfigService flowConfig = flowConfigProvider.getIfAvailable();
        ApproverResolver resolver = resolverProvider.getIfAvailable();
        if (flowConfig == null || resolver == null) {
            return false;
        }
        for (ApprovalNodeDef def : flowConfig.listNodes(task.getFlowKey())) {
            if (def.getNodeCode() != null && def.getNodeCode().equals(task.getCurrentNode())) {
                return resolver.resolveCandidates(def, task.getApplicantId()).contains(userId);
            }
        }
        return false;
    }

    /**
     * 收集"当前用户可审"的 flow_key × node_code 集合（SQL 粗过滤键）。
     *
     * @return 是否存在 DEPT_HEAD_OF_APPLICANT 类型候选节点（需页内精确过滤）
     */
    private boolean collectCandidateNodeKeys(Long userId, Set<String> roles, String username,
                                             Set<String> flowKeys, Set<String> nodeCodes) {
        ApprovalFlowConfigService flowConfig = flowConfigProvider.getIfAvailable();
        ApproverResolver resolver = resolverProvider.getIfAvailable();
        if (flowConfig == null || resolver == null || userId == null) {
            return false;
        }
        boolean hasDeptHeadType = false;
        Set<String> roleSet = roles == null ? Set.of() : roles;
        for (ApprovalFlowConfigService.CachedFlow flow : flowConfig.listAllFlows()) {
            if (flow.getFlowDef() == null) {
                continue;
            }
            for (ApprovalNodeDef def : flow.getNodeDefs()) {
                if (!Integer.valueOf(1).equals(def.getEnabled())) {
                    continue;
                }
                String type = def.getApproverType();
                if (ApproverResolver.TYPE_ROLE.equals(type) && def.getApproverValue() != null
                        && roleSet.contains(def.getApproverValue())) {
                    flowKeys.add(flow.getFlowDef().getFlowKey());
                    nodeCodes.add(def.getNodeCode());
                } else if (ApproverResolver.TYPE_USER.equals(type)
                        && def.getApproverValue() != null && def.getApproverValue().equals(username)) {
                    flowKeys.add(flow.getFlowDef().getFlowKey());
                    nodeCodes.add(def.getNodeCode());
                } else if (ApproverResolver.TYPE_DEPT_HEAD_OF_APPLICANT.equals(type)
                        && (roleSet.contains("DEPT_HEAD") || roleSet.contains("PURCHASE_DEPT"))) {
                    // 上溯链/兜底角色可能命中：纳入粗过滤，页内精确判定
                    flowKeys.add(flow.getFlowDef().getFlowKey());
                    nodeCodes.add(def.getNodeCode());
                    hasDeptHeadType = true;
                }
            }
        }
        return hasDeptHeadType;
    }

    private Map<String, ApprovalNodeDef> nodeDefMap(String flowKey) {
        ApprovalFlowConfigService flowConfig = flowConfigProvider.getIfAvailable();
        if (flowConfig == null) {
            return Map.of();
        }
        return flowConfig.listNodes(flowKey).stream()
                .collect(Collectors.toMap(ApprovalNodeDef::getNodeCode, d -> d, (a, b) -> a));
    }
}
