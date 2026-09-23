package com.dzgylxt.service.impl.contract;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.purchase.AwardItem;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.contract.ContractSaveReqVO;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import com.dzgylxt.enums.ItemType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合同登记金额按"该供应商定标明细份额"校验单测（QA #22 修复锁定）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractPerSupplierAmountTest {

    private static final long AWARD_ID = 800001L;
    private static final long SUPPLIER_A = 2102245025428721666L;
    private static final long SUPPLIER_B = 2102245025428721667L;

    @Mock
    private ISupplierService supplierService;

    @Mock
    private AwardItemMapper awardItemMapper;

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
        ReflectionTestUtils.setField(service, "businessNoGenerator", businessNoGenerator);

        SupplierAdmissionVO ok = new SupplierAdmissionVO();
        ok.setQualified(true);
        when(supplierService.getAdmission(any(Long.class))).thenReturn(ok);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("HT-202609-000001");
        // save 依赖 MyBatis-Plus baseMapper，单测以 stub 代替（落库行为由运行时冒烟覆盖）
        doReturn(true).when(service).save(any(Contract.class));

        // 拆分定标：A 88×144=12672、B 90×144=12960，单总额 25632。
        // mock 端模拟 DB 行为：wrapper 带 supplier_id 过滤 → 仅返回该供应商明细（修复后）；
        // 若实现退化为无过滤（回归），返回全部 → 请求份额金额将不匹配而暴露。
        AwardItem a = new AwardItem();
        a.setAwardId(AWARD_ID);
        a.setSupplierId(SUPPLIER_A);
        a.setPrice(new BigDecimal("88"));
        a.setQtyInBaseUnit(new BigDecimal("144"));
        AwardItem b = new AwardItem();
        b.setAwardId(AWARD_ID);
        b.setSupplierId(SUPPLIER_B);
        b.setPrice(new BigDecimal("90"));
        b.setQtyInBaseUnit(new BigDecimal("144"));
        when(awardItemMapper.selectList(any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.Wrapper<AwardItem> w = inv.getArgument(0);
            String seg = w == null ? "" : String.valueOf(w.getSqlSegment());
            return seg.contains("supplier_id") ? List.of(a) : List.of(a, b);
        });
    }

    /** 校验 wrapper 必须带 supplier_id 过滤（否则 Σ 单总额 → 拆分定标全部被拒，QA #22）。 */
    @Test
    void createContract_awardCheck_filtersBySupplier() {
        ContractSaveReqVO req = req(SUPPLIER_A, new BigDecimal("12672"));
        service.createContract(req);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AwardItem>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(awardItemMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("supplier_id"),
                "定标金额核对必须按 supplier_id 过滤（QA #22）");
    }

    /** 按真实份额登记（A=12672）成功且合同落库金额=份额。 */
    @Test
    void createContract_supplierShare_accepted() {
        ContractSaveReqVO req = req(SUPPLIER_A, new BigDecimal("12672"));
        service.createContract(req);

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(service).save(captor.capture());
        assertEquals(new BigDecimal("12672"), captor.getValue().getAmount());
    }

    /** 金额≠该供应商份额（含误填单总额 25632）→ 4000 拒绝。 */
    @Test
    void createContract_wrongAmount_rejected() {
        ContractSaveReqVO req = req(SUPPLIER_A, new BigDecimal("25632"));
        BizException e = assertThrows(BizException.class, () -> service.createContract(req));
        assertTrue(e.getMessage().contains("该供应商定标明细金额"), e.getMessage());
    }

    private ContractSaveReqVO req(long supplierId, BigDecimal amount) {
        ContractSaveReqVO req = new ContractSaveReqVO();
        req.setSupplierId(supplierId);
        req.setAwardId(AWARD_ID);
        req.setTitle("拆分定标合同");
        req.setContractType(ItemType.MATERIAL);
        req.setAmount(amount);
        req.setValidFrom(LocalDate.of(2026, 9, 22));
        req.setValidTo(LocalDate.of(2027, 9, 21));
        return req;
    }
}
