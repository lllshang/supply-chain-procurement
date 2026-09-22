package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.entity.catalog.Supplier;
import com.dzgylxt.mapper.catalog.SupplierCategoryMapper;
import com.dzgylxt.mapper.catalog.SupplierMapper;
import com.dzgylxt.mapper.catalog.SupplierQualMapper;
import com.dzgylxt.vo.supplier.SupplierPageReqVO;
import com.dzgylxt.vo.supplier.SupplierPageRespVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 供应商分页查询回归测试（回归 #12）。
 *
 * <p>缺陷复现：当分页结果中所有 {@code supplier_category_id} 均为 NULL 时，
 * 原实现以 {@code Map.of()} 作为空映射，随后 {@code nameMap.get(null)} 抛 NullPointerException，
 * 对外表现为 HTTP 500 / code 1000。本测试确保该场景不再抛异常且分类名称为空。</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierPageTest {

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
        ReflectionTestUtils.setField(service, "baseMapper", supplierMapper);
    }

    private Supplier supplier(Long id, String name, Long categoryId) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setName(name);
        supplier.setSupplierCategoryId(categoryId);
        return supplier;
    }

    /** 全部分类为空时不得抛 NPE，categoryName 应为 null。 */
    @Test
    @SuppressWarnings("unchecked")
    void pageSupplier_allNullCategory_doesNotThrow() {
        Page<Supplier> mocked = new Page<>(1, 10, 1);
        mocked.setRecords(List.of(supplier(1L, "无分类供应商", null)));
        when(supplierMapper.selectPage(any(Page.class), any())).thenReturn(mocked);

        SupplierPageReqVO req = new SupplierPageReqVO();
        req.setCurrent(1L);
        req.setSize(10L);

        IPage<SupplierPageRespVO> result = service.pageSupplier(req);

        assertEquals(1, result.getRecords().size());
        assertEquals("无分类供应商", result.getRecords().get(0).getName());
        assertNull(result.getRecords().get(0).getCategoryName(), "无分类时分类名称应为 null");
    }

    /** 混合场景：一条有分类、一条无分类，均应正常返回。 */
    @Test
    @SuppressWarnings("unchecked")
    void pageSupplier_mixedNullCategory_doesNotThrow() {
        Page<Supplier> mocked = new Page<>(1, 10, 2);
        mocked.setRecords(List.of(
                supplier(1L, "有分类", 7L),
                supplier(2L, "无分类", null)));
        when(supplierMapper.selectPage(any(Page.class), any())).thenReturn(mocked);
        when(supplierCategoryMapper.selectBatchIds(any())).thenReturn(
                List.of(categoryOf(7L, "制造商")));

        SupplierPageReqVO req = new SupplierPageReqVO();
        req.setCurrent(1L);
        req.setSize(10L);

        IPage<SupplierPageRespVO> result = service.pageSupplier(req);

        assertEquals(2, result.getRecords().size());
        assertEquals("制造商", result.getRecords().get(0).getCategoryName());
        assertNull(result.getRecords().get(1).getCategoryName());
    }

    private com.dzgylxt.entity.catalog.SupplierCategory categoryOf(Long id, String name) {
        com.dzgylxt.entity.catalog.SupplierCategory category =
                new com.dzgylxt.entity.catalog.SupplierCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
