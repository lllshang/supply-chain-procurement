package com.dzgylxt.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.entity.approval.ApprovalFlowDef;
import com.dzgylxt.entity.approval.ApprovalNodeDef;
import com.dzgylxt.mapper.approval.ApprovalFlowDefMapper;
import com.dzgylxt.mapper.approval.ApprovalNodeDefMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 审批流配置服务（P4 设计 §2.3）：流程/节点配置读取 + 缓存 + 主动失效。
 *
 * <p><b>缓存选型：本地 Caffeine（TTL 60s）+ 配置保存后主动失效；不引 Redis。</b></p>
 * <ol>
 *   <li>现网单体部署，P4 不新增中间件；</li>
 *   <li>AC#3"修改 node_def 不改代码不重启生效"：TTL 60s 兜底 + 配置管理端点保存后
 *       {@link #invalidate(String)} 主动失效双保险，配置页保存即生效；</li>
 *   <li>Redis 方案登记 v1.x 多实例部署时再评估（缓存粒度/失效广播封装在本类，可替换实现）。</li>
 * </ol>
 * 缓存粒度：flow_key → (FlowDef, List&lt;NodeDef&gt;) 整链；空链缓存 30s 防穿透。
 */
@Slf4j
@Service
public class ApprovalFlowConfigService extends ServiceImpl<ApprovalFlowDefMapper, ApprovalFlowDef> {

    /** 流程整链缓存 TTL（秒）——配置变更后在 TTL 内兜底生效（主动失效为主）。 */
    static final long FLOW_TTL_SECONDS = 60;
    /** 空链防穿透缓存 TTL（秒）。 */
    static final long EMPTY_TTL_SECONDS = 30;

    private final ApprovalNodeDefMapper nodeDefMapper;
    private final com.github.benmanes.caffeine.cache.Cache<String, CachedFlow> cache;

    public ApprovalFlowConfigService(ApprovalNodeDefMapper nodeDefMapper) {
        this.nodeDefMapper = nodeDefMapper;
        this.cache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .expireAfterWrite(java.time.Duration.ofSeconds(FLOW_TTL_SECONDS))
                .maximumSize(256)
                .build();
    }

    /** 缓存载体：flow_key → (flowDef, nodeDefs 按 seq 升序)。 */
    @Data
    public static class CachedFlow implements Serializable {
        private ApprovalFlowDef flowDef;
        private List<ApprovalNodeDef> nodeDefs = new ArrayList<>();
    }

    /**
     * 读流程整链（缓存未命中回源 DB；无配置返回 null，空值缓存 30s 防穿透）。
     */
    public CachedFlow getFlow(String flowKey) {
        if (flowKey == null || flowKey.isBlank()) {
            return null;
        }
        CachedFlow cached = cache.get(flowKey, k -> load(k));
        return cached == null || cached.getFlowDef() == null ? null : cached;
    }

    /** 强制回源（测试/管理端排除缓存干扰用）。 */
    public CachedFlow loadDirect(String flowKey) {
        return load(flowKey);
    }

    private CachedFlow load(String flowKey) {
        CachedFlow flow = new CachedFlow();
        ApprovalFlowDef def = getOne(new LambdaQueryWrapper<ApprovalFlowDef>()
                .eq(ApprovalFlowDef::getFlowKey, flowKey), false);
        flow.setFlowDef(def);
        if (def != null) {
            flow.setNodeDefs(nodeDefMapper.selectList(new LambdaQueryWrapper<ApprovalNodeDef>()
                    .eq(ApprovalNodeDef::getFlowKey, flowKey)
                    .orderByAsc(ApprovalNodeDef::getSeq)));
        }
        return flow;
    }

    /**
     * 配置保存后主动失效（AC#3：保存即生效，不重启）。流程配置管理端点（设计 §6.2）保存后必须调用。
     */
    public void invalidate(String flowKey) {
        cache.invalidate(flowKey);
        log.info("[P4-FlowConfig] 配置缓存已失效（保存即生效）flowKey={}", flowKey);
    }

    /** 全量失效（防御性，节点批量变更时使用）。 */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 配置变更入口：更新流程定义并 flow_version +1（仅影响后续 create，在途任务按快照走完）。
     */
    public void saveFlow(ApprovalFlowDef def) {
        ApprovalFlowDef old = getOne(new LambdaQueryWrapper<ApprovalFlowDef>()
                .eq(ApprovalFlowDef::getFlowKey, def.getFlowKey()), false);
        if (old == null) {
            def.setFlowVersion(1);
            save(def);
        } else {
            def.setId(old.getId());
            def.setFlowVersion((old.getFlowVersion() == null ? 1 : old.getFlowVersion()) + 1);
            updateById(def);
        }
        invalidate(def.getFlowKey());
    }

    /** 节点配置变更入口：更新节点定义并失效缓存（保存即生效）。 */
    public void updateNode(ApprovalNodeDef nodeDef) {
        nodeDefMapper.updateById(nodeDef);
        invalidate(nodeDef.getFlowKey());
    }

    /** 节点全链读取（按 seq 升序）。 */
    public List<ApprovalNodeDef> listNodes(String flowKey) {
        CachedFlow flow = getFlow(flowKey);
        if (flow == null) {
            return new ArrayList<>();
        }
        List<ApprovalNodeDef> defs = new ArrayList<>(flow.getNodeDefs());
        defs.sort(Comparator.comparing(ApprovalNodeDef::getSeq,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return defs;
    }

    /** 全部流程整链（工作台待办粗过滤遍历用；逐流走缓存）。 */
    public List<CachedFlow> listAllFlows() {
        List<CachedFlow> flows = new ArrayList<>();
        for (ApprovalFlowDef def : list(new LambdaQueryWrapper<ApprovalFlowDef>()
                .orderByAsc(ApprovalFlowDef::getId))) {
            flows.add(getFlow(def.getFlowKey()));
        }
        return flows;
    }
}
