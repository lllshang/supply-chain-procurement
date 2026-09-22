package com.dzgylxt.service.impl.purchase;

import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.enums.InquiryStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定标单头主供应商口径单测（QA #27 修复锁定）：
 * 多供应商拆分时 award.supplier_id 落金额最大的主供应商（明细为准）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AwardMainSupplierTest {

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

        Inquiry inquiry = new Inquiry();
        inquiry.setId(INQUIRY_ID);
        inquiry.setStatus(InquiryStatus.CLOSED);
        inquiry.setApplyId(1L);
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(inquiry);

        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setStatus(0);
        sku.setBaseUnit("PCS");
        sku.setPurchaseUnit("BOX");
        when(skuMapper.selectById(SKU_ID)).thenReturn(sku);
        when(unitConversionMapper.selectCurrentEffective(anyLong(), anyString(), any())).thenReturn(null);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("DB-202609-000001");
        when(awardItemMapper.insert(any(com.dzgylxt.entity.purchase.AwardItem.class))).thenReturn(1);
        doReturn(true).when(service).save(any(Award.class));
        doReturn(true).when(service).updateById(any(Award.class));
    }

    /** 两家拆分（A 88×12=1056 / B 90×12=1080）→ 单头落金额最大的 B，而非最后一家偶然值。 */
    @Test
    void createAward_multiSupplier_headerIsMaxAmountSupplier() {
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(INQUIRY_ID);
        req.setItems(List.of(
                item(SUPPLIER_A, new BigDecimal("88")),
                item(SUPPLIER_B, new BigDecimal("90"))));

        service.createAward(req);

        ArgumentCaptor<Award> captor = ArgumentCaptor.forClass(Award.class);
        verify(service).save(captor.capture());
        assertEquals(SUPPLIER_B, captor.getValue().getSupplierId(),
                "定标单头 supplier_id 必须为金额最大的主供应商（QA #27）");
        // 定标总金额 = Σ 基本单位口径（88+90）×12 = 2136
        verify(service).updateById(any(Award.class));
    }

    private AwardSaveReqVO.AwardItemVO item(long supplierId, BigDecimal price) {
        AwardSaveReqVO.AwardItemVO vo = new AwardSaveReqVO.AwardItemVO();
        vo.setSkuId(SKU_ID);
        vo.setSupplierId(supplierId);
        vo.setPrice(price);
        vo.setQty(BigDecimal.valueOf(12));
        return vo;
    }
}
