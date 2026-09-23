package com.dzgylxt.service.impl.purchase;

import com.dzgylxt.approval.ApprovalDecision;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ParamException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyItemMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyMapper;
import com.dzgylxt.service.IBudgetSoftCheckService;
import com.dzgylxt.vo.purchase.ApplyItemReqVO;
import com.dzgylxt.vo.purchase.ApplySaveReqVO;
import com.dzgylxt.vo.purchase.BudgetCheckResultVO;
import org.junit.jupiter.api.BeforeEach;
import com.dzgylxt.enums.ProductStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购申请服务单元测试（P2-T02，设计 §2.1 状态机与预算软校验）。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseApplyServiceTest {

    @Mock
    private PurchaseApplyItemMapper itemMapper;

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private UnitConversionMapper unitConversionMapper;

    @Mock
    private ApprovalGateway approvalGateway;

    @Mock
    private IBudgetSoftCheckService budgetSoftCheckService;

    @Mock
    private com.dzgylxt.service.IBudgetOccupyService budgetOccupyService;

    private PurchaseApplyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PurchaseApplyServiceImpl();
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "skuMapper", skuMapper);
        ReflectionTestUtils.setField(service, "unitConversionMapper", unitConversionMapper);
        ReflectionTestUtils.setField(service, "approvalGateway", approvalGateway);
        ReflectionTestUtils.setField(service, "budgetSoftCheckService", budgetSoftCheckService);
        ReflectionTestUtils.setField(service, "budgetOccupyService", budgetOccupyService);
        // 驳回释放路径：默认无占用余额
        org.mockito.Mockito.lenient()
                .when(budgetOccupyService.occupiedTotal(any(), any())).thenReturn(BigDecimal.ZERO);
        org.mockito.Mockito.lenient()
                .when(budgetOccupyService.release(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        ReflectionTestUtils.setField(service, "businessNoGenerator", new BusinessNoGenerator(null) {
            @Override
            public String nextNo(String prefix) {
                return prefix + "-TEST-000001";
            }
        });
        PurchaseApplyMapper applyMapper = mock(PurchaseApplyMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", applyMapper);
    }

    /** 工具：配置 applyMapper 的行为并返回该 mock。 */
    private PurchaseApplyMapper applyMapper() {
        return (PurchaseApplyMapper) ReflectionTestUtils.getField(service, "baseMapper");
    }

    /** 有效 SKU + 换算率 1:12（箱→瓶）时创建申请，明细逐行落换算快照。 */
    @Test
    void createApply_snapshotsConversionPerItem() {
        Sku sku = new Sku();
        sku.setId(9L);
        sku.setStatus(ProductStatus.NORMAL);
        sku.setBaseUnit("PCS");
        sku.setPurchaseUnit("BOX");
        when(skuMapper.selectById(9L)).thenReturn(sku);
        UnitConversion conv = new UnitConversion();
        conv.setRate(new BigDecimal("12"));
        when(unitConversionMapper.selectCurrentEffective(eq(9L), eq("BOX"), any(LocalDateTime.class)))
                .thenReturn(conv);
        when(applyMapper().insert(any(PurchaseApply.class))).thenReturn(1);
        when(itemMapper.insert(any(PurchaseApplyItem.class))).thenReturn(1);

        ApplySaveReqVO req = new ApplySaveReqVO();
        req.setTitle("测试申请");
        ApplyItemReqVO item = new ApplyItemReqVO();
        item.setSkuId(9L);
        item.setQty(new BigDecimal("10"));
        item.setPriceEstimate(new BigDecimal("5"));
        req.setItems(List.of(item));

        service.createApply(req);

        ArgumentCaptor<PurchaseApplyItem> captor = ArgumentCaptor.forClass(PurchaseApplyItem.class);
        verify(itemMapper).insert(captor.capture());
        PurchaseApplyItem saved = captor.getValue();
        assertEquals("BOX", saved.getPurchaseUnit());
        assertEquals(new BigDecimal("12"), saved.getConvRateSnapshot(), "换算快照应取当前生效版本");
        assertEquals(0, saved.getQtyInBaseUnit().compareTo(new BigDecimal("120")), "基本单位数量 = qty × rate");
        assertEquals(0, saved.getApplyQty().compareTo(new BigDecimal("120")));
        assertEquals(0, saved.getRemainQty().compareTo(new BigDecimal("120")));
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getOrderedQty()));
        assertNotNull(saved.getVersion());
    }

    /** 提交（P3 硬控 行 1）：预算占用成功 → PURCHASE_PENDING + budget_status=1 + 采购审批。 */
    @Test
    void submit_occupySucceeds_startsPurchaseApproval() {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(1L);
        apply.setDeptId(1L);
        apply.setApplyNo("CG-TEST-000001");
        apply.setStatus(PurchaseApplyStatus.DRAFT);
        when(applyMapper().selectById(1L)).thenReturn(apply);

        PurchaseApplyItem item = new PurchaseApplyItem();
        item.setApplyId(1L);
        item.setSkuId(9L);
        item.setQtyInPurchaseUnit(BigDecimal.TEN);
        item.setPriceEstimate(new BigDecimal("100"));
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        when(budgetOccupyService.occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.ok(new BigDecimal("1000"), List.of(5001L)));
        when(approvalGateway.create(any(ApprovalTaskSpec.class))).thenReturn(77L);
        when(applyMapper().updateById(any(PurchaseApply.class))).thenReturn(1);

        service.submit(1L);

        assertEquals(PurchaseApplyStatus.PURCHASE_PENDING, apply.getStatus());
        assertEquals(1, apply.getBudgetStatus(), "占用成功 budget_status=1");
        ArgumentCaptor<ApprovalTaskSpec> spec = ArgumentCaptor.forClass(ApprovalTaskSpec.class);
        verify(approvalGateway).create(spec.capture());
        assertEquals("PURCHASE_APPLY", spec.getValue().getBizType());
        assertEquals(1L, spec.getValue().getBizId());
    }

    /** 提交（P3 硬控 行 2）：预算不足 → 拦截停 BUDGET_PENDING + BUDGET 升级审批（先占后审禁止）。 */
    @Test
    void submit_overBudget_blockedAndEscalated() {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(1L);
        apply.setDeptId(1L);
        apply.setApplyNo("CG-TEST-000001");
        apply.setStatus(PurchaseApplyStatus.DRAFT);
        when(applyMapper().selectById(1L)).thenReturn(apply);

        PurchaseApplyItem item = new PurchaseApplyItem();
        item.setApplyId(1L);
        item.setSkuId(9L);
        item.setQtyInPurchaseUnit(BigDecimal.TEN);
        item.setPriceEstimate(new BigDecimal("100"));
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        com.dzgylxt.vo.budget.OccupyResultVO blocked =
                com.dzgylxt.vo.budget.OccupyResultVO.blocked(new BigDecimal("400"),
                        new BigDecimal("600"), "当月预算余额不足");
        when(budgetOccupyService.occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(blocked);
        when(approvalGateway.create(any(ApprovalTaskSpec.class))).thenReturn(88L);
        when(applyMapper().updateById(any(PurchaseApply.class))).thenReturn(1);

        service.submit(1L);

        assertEquals(PurchaseApplyStatus.BUDGET_PENDING, apply.getStatus(), "超支拦截停 BUDGET_PENDING");
        assertEquals(2, apply.getBudgetStatus());
        ArgumentCaptor<ApprovalTaskSpec> spec = ArgumentCaptor.forClass(ApprovalTaskSpec.class);
        verify(approvalGateway).create(spec.capture());
        assertEquals("BUDGET", spec.getValue().getBizType(), "发 BUDGET 升级审批");
        assertEquals(1L, spec.getValue().getBizId());
        assertTrue(spec.getValue().getPayloadJson().contains("overAmount"));
    }

    /** 提交状态机：APPROVED 单不可重复提交。 */
    @Test
    void submit_approvedApplyRejected() {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(1L);
        apply.setStatus(PurchaseApplyStatus.APPROVED);
        when(applyMapper().selectById(1L)).thenReturn(apply);

        var e = assertThrows(com.dzgylxt.common.BizException.class, () -> service.submit(1L));
        assertEquals(ResultCode.STATUS_INVALID.getCode(), e.getCode());
        verify(approvalGateway, never()).create(any());
    }

    /** 审批回调通过：PURCHASE_PENDING → APPROVED。 */
    @Test
    void onApproved_pendingToApproved() {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(1L);
        apply.setStatus(PurchaseApplyStatus.PURCHASE_PENDING);
        when(applyMapper().selectById(1L)).thenReturn(apply);
        when(applyMapper().updateById(any(PurchaseApply.class))).thenReturn(1);

        service.onApproved(77L, 1L, "ok");
        assertEquals(PurchaseApplyStatus.APPROVED, apply.getStatus());
    }

    /** 审批回调驳回：PURCHASE_PENDING → REJECTED（可修改重提）。 */
    @Test
    void onRejected_pendingToRejected() {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(1L);
        apply.setStatus(PurchaseApplyStatus.PURCHASE_PENDING);
        when(applyMapper().selectById(1L)).thenReturn(apply);
        when(applyMapper().updateById(any(PurchaseApply.class))).thenReturn(1);

        service.onRejected(77L, 1L, "金额不合理");
        assertEquals(PurchaseApplyStatus.REJECTED, apply.getStatus());
    }

    /** 回调幂等兜底：非 PURCHASE_PENDING 状态不重复推进。 */
    @Test
    void onApproved_nonPendingIgnored() {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(1L);
        apply.setStatus(PurchaseApplyStatus.APPROVED);
        when(applyMapper().selectById(1L)).thenReturn(apply);

        service.onApproved(77L, 1L, "ok");
        verify(applyMapper(), never()).updateById(any(PurchaseApply.class));
    }

    /** 参数校验：缺标题 → 4000。 */
    @Test
    void createApply_blankTitle_paramError() {
        ApplySaveReqVO req = new ApplySaveReqVO();
        req.setTitle(" ");
        var e = assertThrows(ParamException.class, () -> service.createApply(req));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), e.getCode());
    }

    /** 参数校验：停用 SKU 拒绝入明细。 */
    @Test
    void createApply_disabledSku_paramError() {
        Sku sku = new Sku();
        sku.setId(9L);
        sku.setStatus(ProductStatus.DISABLED);
        when(skuMapper.selectById(9L)).thenReturn(sku);
        when(applyMapper().insert(any(PurchaseApply.class))).thenReturn(1);

        ApplySaveReqVO req = new ApplySaveReqVO();
        req.setTitle("测试");
        ApplyItemReqVO item = new ApplyItemReqVO();
        item.setSkuId(9L);
        item.setQty(BigDecimal.ONE);
        req.setItems(List.of(item));

        var e = assertThrows(com.dzgylxt.common.BizException.class, () -> service.createApply(req));
        assertTrue(e.getMessage().contains("停用"));
        verify(itemMapper, never()).insert(any(PurchaseApplyItem.class));
    }
}
