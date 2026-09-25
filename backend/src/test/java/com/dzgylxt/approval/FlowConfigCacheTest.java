package com.dzgylxt.approval;

import com.dzgylxt.entity.approval.ApprovalFlowDef;
import com.dzgylxt.entity.approval.ApprovalNodeDef;
import com.dzgylxt.mapper.approval.ApprovalFlowDefMapper;
import com.dzgylxt.mapper.approval.ApprovalNodeDefMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 流程配置缓存单测（设计 §2.3 / 规格 §3.1 AC#3）：
 * 整链缓存命中（不回源）+ 保存后主动失效（不改代码不重启生效）+ 空值防穿透。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowConfigCacheTest {

    @Mock
    private ApprovalFlowDefMapper flowDefMapper;
    @Mock
    private ApprovalNodeDefMapper nodeDefMapper;

    private ApprovalFlowConfigService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalFlowConfigService(nodeDefMapper);
        ReflectionTestUtils.setField(service, "baseMapper", flowDefMapper);

        ApprovalFlowDef def = new ApprovalFlowDef();
        def.setId(1L);
        def.setFlowKey("CONTRACT");
        def.setBizType("CONTRACT");
        def.setFlowVersion(1);
        def.setEnabled(1);
        // ServiceImpl.getOne(wrapper, false) → BaseMapper.selectOne(wrapper, false)（Mockito 拦截默认方法）
        when(flowDefMapper.selectOne(any(), org.mockito.Mockito.anyBoolean())).thenReturn(def);

        ApprovalNodeDef n1 = new ApprovalNodeDef();
        n1.setFlowKey("CONTRACT");
        n1.setNodeCode("N1");
        n1.setSeq(1);
        n1.setEnabled(1);
        when(nodeDefMapper.selectList(any())).thenReturn(List.of(n1));
    }

    /** 缓存命中：两次 getFlow 仅一次回源 DB。 */
    @Test
    void getFlow_cached_secondCallNoDbHit() {
        ApprovalFlowConfigService.CachedFlow first = service.getFlow("CONTRACT");
        ApprovalFlowConfigService.CachedFlow second = service.getFlow("CONTRACT");

        assertNotNull(first.getFlowDef());
        assertEquals(first.getFlowDef(), second.getFlowDef());
        verify(flowDefMapper, times(1)).selectOne(any(), org.mockito.Mockito.anyBoolean());
    }

    /** AC#3：保存后主动失效——不重启即读到新配置。 */
    @Test
    void invalidate_nextReadReloads() {
        service.getFlow("CONTRACT");
        service.invalidate("CONTRACT");
        service.getFlow("CONTRACT");

        verify(flowDefMapper, times(2)).selectOne(any(), org.mockito.Mockito.anyBoolean());
    }

    /** 空值防穿透：无配置流程返回 null（缓存空链 30s，同样只回源一次）。 */
    @Test
    void getFlow_missing_emptyCached() {
        when(flowDefMapper.selectOne(any(), org.mockito.Mockito.anyBoolean())).thenReturn(null);

        assertNull(service.getFlow("NO_SUCH_FLOW"));
        assertNull(service.getFlow("NO_SUCH_FLOW"));
        verify(flowDefMapper, times(1)).selectOne(any(), org.mockito.Mockito.anyBoolean());
    }

    /** saveFlow 更新后 flow_version+1 且主动失效缓存（保存即生效）。 */
    @Test
    void saveFlow_invalidatesCache() {
        service.getFlow("CONTRACT");
        ApprovalFlowDef update = new ApprovalFlowDef();
        update.setFlowKey("CONTRACT");
        update.setBizType("CONTRACT");
        update.setEnabled(0);
        when(flowDefMapper.updateById(any(com.dzgylxt.entity.approval.ApprovalFlowDef.class))).thenReturn(1);
        service.saveFlow(update);

        assertEquals(2, update.getFlowVersion(), "配置变更 flow_version 应 +1");
        // saveFlow 内部已失效 → 下次读取回源（getOne 1 次 + 失效后 load 1 次 = 累计 3 次回源）
        service.getFlow("CONTRACT");
        verify(flowDefMapper, times(3)).selectOne(any(), org.mockito.Mockito.anyBoolean());
    }
}
