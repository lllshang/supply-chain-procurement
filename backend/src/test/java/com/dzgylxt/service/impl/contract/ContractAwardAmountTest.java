package com.dzgylxt.service.impl.contract;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.purchase.AwardItem;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.contract.ContractSaveReqVO;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import org.junit.jupiter.api.BeforeAll;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2 合同金额校验单测：合同金额 = 定标金额（可校验）。
 *
 * <p>R2 回退拆标后删除"按 award_item 供应商份额校验"（审计 #14 衍生补丁）——
 * 校验口径恢复为「合同金额 == Σ 该定标单全部明细金额」。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractAwardAmountTest {

    private static final long AWARD_ID = 800001L;
    private static final long SUPPLIER_A = 2102245025428721666L;

    @Mock
    private ISupplierService supplierService;

    @Mock
    private AwardItemMapper awardItemMapper;

    @Mock
    private com.dzgylxt.mapper.contract.ContractPriceItemMapper contractPriceItemMapper;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    private ContractServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯单测环境无 MP 启动流程，手动注册实体 lambda 缓存（LambdaQueryWrapper 依赖）
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.dzgylxt.entity.purchase.AwardItem.class);
    }

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new ContractServiceImpl());
        ReflectionTestUtils.setField(service, "supplierService", supplierService);
        ReflectionTestUtils.setField(service, "awardItemMapper", awardItemMapper);
        ReflectionTestUtils.setField(service, "contractPriceItemMapper", contractPriceItemMapper);
        ReflectionTestUtils.setField(service, "businessNoGenerator", businessNoGenerator);

        SupplierAdmissionVO ok = new SupplierAdmissionVO();
        ok.setQualified(true);
        when(supplierService.getAdmission(any(Long.class))).thenReturn(ok);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("HT-202609-000001");
        doReturn(true).when(service).save(any(Contract.class));

        // 单中标供应商：两明细行 88×144 + 90×144 = 25632（R2：不再按供应商过滤）
        AwardItem a = new AwardItem();
        a.setAwardId(AWARD_ID);
        a.setSupplierId(SUPPLIER_A);
        a.setPrice(new BigDecimal("88"));
        a.setQtyInBaseUnit(new BigDecimal("144"));
        AwardItem b = new AwardItem();
        b.setAwardId(AWARD_ID);
        b.setSupplierId(SUPPLIER_A);
        b.setPrice(new BigDecimal("90"));
        b.setQtyInBaseUnit(new BigDecimal("144"));
        when(awardItemMapper.selectList(any())).thenReturn(List.of(a, b));
    }

    /** 合同金额 = 定标金额（Σ 全部明细，R2 无份额概念）→ 登记成功。 */
    @Test
    void createContract_amountEqualsAwardTotal_accepted() {
        ContractSaveReqVO req = req(SUPPLIER_A, new BigDecimal("25632"));
        service.createContract(req);

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(service).save(captor.capture());
        assertEquals(new BigDecimal("25632"), captor.getValue().getAmount());
    }

    /** 合同金额 ≠ 定标金额 → 拒绝（提示"合同金额必须等于定标金额"）。 */
    @Test
    void createContract_wrongAmount_rejected() {
        ContractSaveReqVO req = req(SUPPLIER_A, new BigDecimal("12672"));
        BizException e = assertThrows(BizException.class, () -> service.createContract(req));
        assertTrue(e.getMessage().contains("合同金额必须等于定标金额"), e.getMessage());
    }

    private ContractSaveReqVO req(long supplierId, BigDecimal amount) {
        ContractSaveReqVO req = new ContractSaveReqVO();
        req.setSupplierId(supplierId);
        req.setAwardId(AWARD_ID);
        req.setTitle("定标合同");
        req.setContractType(ItemType.MATERIAL);
        req.setAmount(amount);
        req.setValidFrom(LocalDate.of(2026, 9, 22));
        req.setValidTo(LocalDate.of(2027, 9, 21));
        return req;
    }

    // ---------------- P3c-A1：合同价格清单生成（双来源继承） ----------------

    /** AC④：定标 2 SKU → 合同价格清单 2 行，source=1 且单价/数量与定标一致。 */
    @Test
    void generatePriceItems_fromAward_inheritsTwoRows() {
        service.generatePriceItems(9001L, AWARD_ID, null);

        ArgumentCaptor<com.dzgylxt.entity.contract.ContractPriceItem> captor =
                ArgumentCaptor.forClass(com.dzgylxt.entity.contract.ContractPriceItem.class);
        verify(contractPriceItemMapper, Mockito.times(2)).insert(captor.capture());
        List<com.dzgylxt.entity.contract.ContractPriceItem> rows = captor.getAllValues();
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).getSourceType(), "定标继承 source=1");
        assertEquals(new BigDecimal("88"), rows.get(0).getUnitPrice());
        assertEquals(new BigDecimal("144"), rows.get(0).getQty());
        assertEquals(9001L, rows.get(0).getContractId());
        assertEquals(new BigDecimal("90"), rows.get(1).getUnitPrice());
    }

    /** 无定标 + 手工明细 → source=2；缺单价拒绝。 */
    @Test
    void generatePriceItems_manual_sourceType2_andValidates() {
        ContractSaveReqVO.PriceItemVO vo = new ContractSaveReqVO.PriceItemVO();
        vo.setSkuId(5001L);
        vo.setUnitPrice(new BigDecimal("88.5"));
        vo.setQty(new BigDecimal("3"));
        service.generatePriceItems(9002L, null, List.of(vo));

        ArgumentCaptor<com.dzgylxt.entity.contract.ContractPriceItem> captor =
                ArgumentCaptor.forClass(com.dzgylxt.entity.contract.ContractPriceItem.class);
        verify(contractPriceItemMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getSourceType(), "手工维护 source=2");
        assertEquals(new BigDecimal("88.50"), captor.getValue().getUnitPrice());

        ContractSaveReqVO.PriceItemVO bad = new ContractSaveReqVO.PriceItemVO();
        bad.setSkuId(5002L);   // 缺单价
        assertThrows(BizException.class, () -> service.generatePriceItems(9003L, null, List.of(bad)));
        // 覆盖式：重建前先清旧清单（重提/改单场景不残留）
    }

    /** 无定标且无手工明细 → 不生成清单（该合同免价格校验，走额度闸兜底）。 */
    @Test
    void generatePriceItems_none_noRows() {
        service.generatePriceItems(9004L, null, null);
        verify(contractPriceItemMapper, Mockito.never())
                .insert(any(com.dzgylxt.entity.contract.ContractPriceItem.class));
    }
}
