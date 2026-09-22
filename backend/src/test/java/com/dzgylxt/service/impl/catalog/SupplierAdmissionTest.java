package com.dzgylxt.service.impl.catalog;

import com.dzgylxt.entity.catalog.Supplier;
import com.dzgylxt.entity.catalog.SupplierQual;
import com.dzgylxt.enums.BlacklistFlag;
import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.enums.QualStatus;
import com.dzgylxt.mapper.catalog.SupplierCategoryMapper;
import com.dzgylxt.mapper.catalog.SupplierMapper;
import com.dzgylxt.mapper.catalog.SupplierQualMapper;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 供应商准入判定（R-X-01 / docs/P1主数据设计.md §2.3.3）单元测试。
 *
 * <p>文档口径：准入要求存在 {@code status=1} 且“未过期”的必要资质；“未过期” = VALID + EXPIRING
 * （R-SUP-04 三态；R-SUP-05 到期预警仅提示、不阻断采购闭环），仅 EXPIRED 阻断。</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierAdmissionTest {

    private static final long SUPPLIER_ID = 100L;
    private static final int WARN_DAYS = 30;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private SupplierQualMapper supplierQualMapper;

    @Mock
    private SupplierCategoryMapper supplierCategoryMapper;

    private SupplierServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SupplierServiceImpl(supplierQualMapper, supplierCategoryMapper);
        // ServiceImpl.baseMapper 与 qualWarnDays(@Value) 在纯单测中不会被 Spring 注入，手动设置。
        ReflectionTestUtils.setField(service, "baseMapper", supplierMapper);
        ReflectionTestUtils.setField(service, "qualWarnDays", WARN_DAYS);
    }

    /** 正常合作、非黑名单供应商。 */
    private Supplier normalSupplier() {
        Supplier supplier = new Supplier();
        supplier.setId(SUPPLIER_ID);
        supplier.setCoopStatus(CoopStatus.NORMAL);
        supplier.setIsBlacklist(BlacklistFlag.NO);
        return supplier;
    }

    /** 已通过审核（status=1）的资质，指定到期时间。 */
    private SupplierQual approvedQual(LocalDateTime expireAt) {
        SupplierQual qual = new SupplierQual();
        qual.setSupplierId(SUPPLIER_ID);
        qual.setStatus(QualStatus.APPROVED);
        qual.setExpireAt(expireAt);
        return qual;
    }

    @Test
    void qualified_whenOnlyExpiringQual() {
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(normalSupplier());
        // 15 天后到期，落在预警窗口（<= 30 天）→ EXPIRING
        when(supplierQualMapper.selectBySupplier(SUPPLIER_ID))
                .thenReturn(List.of(approvedQual(LocalDateTime.now().plusDays(15))));

        SupplierAdmissionVO vo = service.getAdmission(SUPPLIER_ID);

        assertEquals("EXPIRING", vo.getQualValidity());
        assertTrue(vo.getQualified(), "仅有 EXPIRING（未过期）资质应判定为可准入");
        assertTrue(vo.getReasons().isEmpty(), "可准入时不应存在阻断原因");
    }

    @Test
    void qualified_whenValidQual() {
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(normalSupplier());
        when(supplierQualMapper.selectBySupplier(SUPPLIER_ID))
                .thenReturn(List.of(approvedQual(LocalDateTime.now().plusDays(365))));

        SupplierAdmissionVO vo = service.getAdmission(SUPPLIER_ID);

        assertEquals("VALID", vo.getQualValidity());
        assertTrue(vo.getQualified());
    }

    @Test
    void notQualified_whenExpiredQual() {
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(normalSupplier());
        when(supplierQualMapper.selectBySupplier(SUPPLIER_ID))
                .thenReturn(List.of(approvedQual(LocalDateTime.now().minusDays(1))));

        SupplierAdmissionVO vo = service.getAdmission(SUPPLIER_ID);

        assertEquals("EXPIRED", vo.getQualValidity());
        assertFalse(vo.getQualified(), "仅 EXPIRED 资质应阻断准入");
        assertTrue(vo.getReasons().contains("无有效资质"));
    }

    @Test
    void qualified_whenExpiringAndExpiredMixed() {
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(normalSupplier());
        // 一条 EXPIRED + 一条 EXPIRING：整体取最优（EXPIRING），仍可准入
        when(supplierQualMapper.selectBySupplier(SUPPLIER_ID))
                .thenReturn(List.of(
                        approvedQual(LocalDateTime.now().minusDays(1)),
                        approvedQual(LocalDateTime.now().plusDays(10))));

        SupplierAdmissionVO vo = service.getAdmission(SUPPLIER_ID);

        assertEquals("EXPIRING", vo.getQualValidity());
        assertTrue(vo.getQualified());
    }

    @Test
    void notQualified_whenNoApprovedQual() {
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(normalSupplier());
        SupplierQual pending = approvedQual(LocalDateTime.now().plusDays(365));
        pending.setStatus(QualStatus.PENDING);
        when(supplierQualMapper.selectBySupplier(SUPPLIER_ID)).thenReturn(List.of(pending));

        SupplierAdmissionVO vo = service.getAdmission(SUPPLIER_ID);

        assertFalse(vo.getQualified(), "未通过审核（status!=1）的资质不计入准入");
        assertTrue(vo.getReasons().contains("无有效资质"));
    }

    @Test
    void notQualified_whenBlacklisted() {
        Supplier blacklisted = normalSupplier();
        blacklisted.setIsBlacklist(BlacklistFlag.YES);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(blacklisted);
        when(supplierQualMapper.selectBySupplier(SUPPLIER_ID))
                .thenReturn(List.of(approvedQual(LocalDateTime.now().plusDays(365))));

        SupplierAdmissionVO vo = service.getAdmission(SUPPLIER_ID);

        assertFalse(vo.getQualified(), "黑名单应阻断准入");
        assertTrue(vo.getReasons().contains("已列入黑名单"));
    }
}
