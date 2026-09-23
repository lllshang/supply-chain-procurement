package com.dzgylxt.service.impl.purchase;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.enums.InquiryStatus;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.mapper.purchase.AwardMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.vo.purchase.AwardSaveReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2 定标单中标供应商单测（审计修正 R2 锁定）：
 * 一询价单一中标供应商——跨供应商明细拒绝；单头 supplier_id = 唯一中标供应商；
 * 同一询价禁止重复定标。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AwardSingleSupplierTest {

    private static final long INQUIRY_ID = 700001L;
    private static final long SKU_ID = 2102245024849907713L;
    private static final long SUPPLIER_A = 2102245025428721666L;
    private static final long SUPPLIER_B = 2102245025428721667L;

    @Mock
    private InquiryMapper inquiryMapper;

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private UnitConversionMapper unitConversionMapper;

    @Mock
    private AwardItemMapper awardItemMapper;

    @Mock
    private AwardMapper awardMapper;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    private AwardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new AwardServiceImpl());
        ReflectionTestUtils.setField(service, "inquiryMapper", inquiryMapper);
        ReflectionTestUtils.setField(service, "skuMapper", skuMapper);
        ReflectionTestUtils.setField(service, "unitConversionMapper", unitConversionMapper);
        ReflectionTestUtils.setField(service, "awardItemMapper", awardItemMapper);
        ReflectionTestUtils.setField(service, "businessNoGenerator", businessNoGenerator);
        // ServiceImpl.count() 走 baseMapper.selectCount（R2 唯一中标查重）
        ReflectionTestUtils.setField(service, "baseMapper", awardMapper);

        Inquiry inquiry = new Inquiry();
        inquiry.setId(INQUIRY_ID);
        inquiry.setStatus(InquiryStatus.CLOSED);
        inquiry.setApplyId(1L);
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(inquiry);
        when(awardMapper.selectCount(any())).thenReturn(0L);

        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setStatus(ProductStatus.NORMAL);
        sku.setBaseUnit("PCS");
        sku.setPurchaseUnit("BOX");
        when(skuMapper.selectById(SKU_ID)).thenReturn(sku);
        when(skuMapper.selectById(anyLong())).thenReturn(sku);
        when(unitConversionMapper.selectCurrentEffective(anyLong(), anyString(), any())).thenReturn(null);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("DB-202609-000001");
        when(awardItemMapper.insert(any(com.dzgylxt.entity.purchase.AwardItem.class))).thenReturn(1);
        doReturn(true).when(service).save(any(Award.class));
        doReturn(true).when(service).updateById(any(Award.class));
    }

    /** R2：按 SKU 拆多供应商（A/B 混排）→ 拒绝（一询价单一中标供应商）。 */
    @Test
    void createAward_multiSupplier_rejected() {
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(INQUIRY_ID);
        req.setItems(List.of(
                item(SKU_ID, SUPPLIER_A, new BigDecimal("88")),
                item(2102245024849907714L, SUPPLIER_B, new BigDecimal("90"))));

        BizException e = assertThrows(BizException.class, () -> service.createAward(req));
        assertTrue(e.getMessage().contains("一询价单一中标供应商"), e.getMessage());
    }

    /** R2：单供应商多明细行 → 单头 supplier_id = 唯一中标供应商，金额 = Σ 明细（基本单位口径）。 */
    @Test
    void createAward_singleSupplier_headerIsTheSupplier() {
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(INQUIRY_ID);
        req.setItems(List.of(
                item(SKU_ID, SUPPLIER_A, new BigDecimal("88")),
                item(2102245024849907714L, SUPPLIER_A, new BigDecimal("90"))));

        service.createAward(req);

        ArgumentCaptor<Award> captor = ArgumentCaptor.forClass(Award.class);
        verify(service).save(captor.capture());
        assertEquals(SUPPLIER_A, captor.getValue().getSupplierId(),
                "R2：单头 supplier_id = 唯一中标供应商");
    }

    /** R2：同一询价已存在定标 → 重复定标拒绝。 */
    @Test
    void createAward_duplicateInquiry_rejected() {
        when(awardMapper.selectCount(any())).thenReturn(1L);
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(INQUIRY_ID);
        req.setItems(List.of(item(SKU_ID, SUPPLIER_A, new BigDecimal("88"))));

        BizException e = assertThrows(BizException.class, () -> service.createAward(req));
        assertTrue(e.getMessage().contains("已存在定标单"), e.getMessage());
    }

    private AwardSaveReqVO.AwardItemVO item(long skuId, long supplierId, BigDecimal price) {
        AwardSaveReqVO.AwardItemVO vo = new AwardSaveReqVO.AwardItemVO();
        vo.setSkuId(skuId);
        vo.setSupplierId(supplierId);
        vo.setPrice(price);
        vo.setQty(BigDecimal.valueOf(12));
        return vo;
    }
}
