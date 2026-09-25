package com.dzgylxt.controller.approval;

import com.dzgylxt.approval.ApprovalBizTypes;
import com.dzgylxt.approval.ApprovalFlowConfigService;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.approval.ApprovalFlowDef;
import com.dzgylxt.entity.approval.ApprovalNodeDef;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程配置管理（P4 设计 §6.2，菜单 1003，仅 approval:config 授权）。
 *
 * <p>保存后 {@link ApprovalFlowConfigService#invalidate} 主动失效缓存（保存即生效，
 * AC#3 不重启）；flow_version 随保存 +1，仅影响新任务——在途任务按提交时节点快照走完（§2.9）。</p>
 */
@RestController
@RequestMapping("/api/v1/approvals/flows")
public class ApprovalFlowDefController {

    private static final String PERM_CONFIG =
            "isAuthenticated() and @authz.hasPerm(authentication,'approval:config')";

    private final ApprovalFlowConfigService flowConfigService;

    public ApprovalFlowDefController(ApprovalFlowConfigService flowConfigService) {
        this.flowConfigService = flowConfigService;
    }

    /** 流定义列表（含节点树）。 */
    @PreAuthorize(PERM_CONFIG)
    @GetMapping
    public R<List<FlowVO>> list() {
        List<FlowVO> result = new ArrayList<>();
        for (ApprovalFlowConfigService.CachedFlow flow : flowConfigService.listAllFlows()) {
            if (flow.getFlowDef() == null) {
                continue;
            }
            result.add(toVo(flow));
        }
        return R.ok(result);
    }

    /** 新建流（bizType 唯一校验；仅限 8 个已知 bizType，防拼写漂移）。 */
    @PreAuthorize(PERM_CONFIG)
    @PostMapping
    public R<Long> create(@RequestBody ApprovalFlowDef def) {
        if (def.getFlowKey() == null || def.getFlowKey().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "flowKey 必填");
        }
        if (!ApprovalBizTypes.all().contains(def.getFlowKey())) {
            throw new BizException(ResultCode.PARAM_ERROR, "非法 flowKey（仅限 8 个已知 bizType）");
        }
        if (flowConfigService.getFlow(def.getFlowKey()) != null) {
            throw new BizException(ResultCode.DATA_CONFLICT, "流程已存在：" + def.getFlowKey());
        }
        def.setBizType(def.getFlowKey());
        flowConfigService.saveFlow(def);
        return R.ok(def.getId());
    }

    /** 改名/启停（停用后新任务拒绝创建，在途不受影响）。 */
    @PreAuthorize(PERM_CONFIG)
    @PutMapping("/{flowKey}")
    public R<Boolean> update(@PathVariable String flowKey, @RequestBody ApprovalFlowDef def) {
        ApprovalFlowConfigService.CachedFlow existing = flowConfigService.getFlow(flowKey);
        if (existing == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "流程不存在：" + flowKey);
        }
        ApprovalFlowDef patch = existing.getFlowDef();
        if (def.getFlowName() != null) {
            patch.setFlowName(def.getFlowName());
        }
        if (def.getEnabled() != null) {
            patch.setEnabled(def.getEnabled());
        }
        if (def.getRemark() != null) {
            patch.setRemark(def.getRemark());
        }
        flowConfigService.saveFlow(patch);
        return R.ok(true);
    }

    /** 节点编辑（角色/区间/签类型）；保存后缓存失效；仅影响新任务。 */
    @PreAuthorize(PERM_CONFIG)
    @PutMapping("/{flowKey}/nodes/{nodeId}")
    public R<Boolean> updateNode(@PathVariable String flowKey, @PathVariable Long nodeId,
                                 @RequestBody ApprovalNodeDef nodeDef) {
        ApprovalFlowConfigService.CachedFlow existing = flowConfigService.getFlow(flowKey);
        if (existing == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "流程不存在：" + flowKey);
        }
        ApprovalNodeDef target = existing.getNodeDefs().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "节点不存在：" + nodeId));
        if (nodeDef.getApproverType() != null) {
            target.setApproverType(nodeDef.getApproverType());
        }
        if (nodeDef.getApproverValue() != null || nodeDef.getApproverType() != null) {
            target.setApproverValue(nodeDef.getApproverValue());
        }
        if (nodeDef.getAmountMin() != null) {
            target.setAmountMin(nodeDef.getAmountMin());
        }
        if (nodeDef.getAmountMax() != null) {
            target.setAmountMax(nodeDef.getAmountMax());
        }
        if (nodeDef.getSignType() != null) {
            target.setSignType(nodeDef.getSignType());
        }
        if (nodeDef.getFreeReview() != null) {
            target.setFreeReview(nodeDef.getFreeReview());
        }
        if (nodeDef.getEnabled() != null) {
            target.setEnabled(nodeDef.getEnabled());
        }
        flowConfigService.updateNode(target);
        return R.ok(true);
    }

    private FlowVO toVo(ApprovalFlowConfigService.CachedFlow flow) {
        FlowVO vo = new FlowVO();
        vo.setFlowDef(flow.getFlowDef());
        vo.setNodes(flowConfigService.listNodes(flow.getFlowDef().getFlowKey()));
        return vo;
    }

    /** 流定义 + 节点树响应体。 */
    public static class FlowVO {
        private ApprovalFlowDef flowDef;
        private List<ApprovalNodeDef> nodes;

        public ApprovalFlowDef getFlowDef() {
            return flowDef;
        }

        public void setFlowDef(ApprovalFlowDef flowDef) {
            this.flowDef = flowDef;
        }

        public List<ApprovalNodeDef> getNodes() {
            return nodes;
        }

        public void setNodes(List<ApprovalNodeDef> nodes) {
            this.nodes = nodes;
        }
    }
}
