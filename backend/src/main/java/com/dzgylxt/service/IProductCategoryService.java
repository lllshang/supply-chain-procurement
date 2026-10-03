package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.ProductCategory;
import com.dzgylxt.vo.catalog.ProductCategorySaveReqVO;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;

import java.util.List;
import java.util.Map;

/**
 * 商品三级品类服务。
 */
public interface IProductCategoryService extends IService<ProductCategory> {

    /** 新增品类：校验 code 唯一、level 与 parent 一致、维护 tree_path。 */
    Long createCategory(ProductCategorySaveReqVO req);

    /** 编辑品类：校验被引用不可改关键字段。 */
    void updateCategory(Long id, ProductCategorySaveReqVO req);

    /** 返回三级品类树。 */
    List<CategoryTreeNodeVO> tree();

    /** 置无效（被引用不可物理删，仅可 status=1）。 */
    void invalidate(Long id);

    /** 供 SPU 保存时校验"必须是叶子节点（level=3 且无子节点）"。 */
    void assertLeaf(Long categoryId);

    /** 递归统计每个品类下（含下级）关联的有效 SPU 数量，返回 categoryId -> count。 */
    Map<Long, Long> productCounts();
}
