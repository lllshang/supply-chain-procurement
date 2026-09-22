package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.SupplierCategory;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
import com.dzgylxt.vo.supplier.SupplierCategorySaveReqVO;

import java.util.List;

/**
 * 供应商三级分类服务。
 */
public interface ISupplierCategoryService extends IService<SupplierCategory> {

    Long createCategory(SupplierCategorySaveReqVO req);

    void updateCategory(Long id, SupplierCategorySaveReqVO req);

    List<CategoryTreeNodeVO> tree();

    void invalidate(Long id);
}
