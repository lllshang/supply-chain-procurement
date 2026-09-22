package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.TreeUtils;
import com.dzgylxt.entity.catalog.ProductCategory;
import com.dzgylxt.entity.catalog.Spu;
import com.dzgylxt.mapper.catalog.ProductCategoryMapper;
import com.dzgylxt.mapper.catalog.SpuMapper;
import com.dzgylxt.service.IProductCategoryService;
import com.dzgylxt.vo.catalog.ProductCategorySaveReqVO;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 商品三级品类服务实现。 */
@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
        implements IProductCategoryService {

    /** 有效状态。 */
    private static final int STATUS_VALID = 0;
    /** 无效状态。 */
    private static final int STATUS_INVALID = 1;
    private static final int MAX_LEVEL = 3;

    private final SpuMapper spuMapper;

    public ProductCategoryServiceImpl(SpuMapper spuMapper) {
        this.spuMapper = spuMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(ProductCategorySaveReqVO req) {
        if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "编码与名称必填");
        }
        if (baseMapper.existsByCode(req.getCode(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "品类编码已存在：" + req.getCode());
        }
        long parentId = req.getParentId() == null ? 0L : req.getParentId();
        int level = 1;
        String parentPath = "";
        if (parentId != 0L) {
            ProductCategory parent = getById(parentId);
            if (parent == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "父品类不存在：" + parentId);
            }
            if (parent.getLevel() == null || parent.getLevel() >= MAX_LEVEL) {
                throw new BizException(ResultCode.BIZ_ERROR, "最多支持三级品类，父节点不可再挂子节点");
            }
            level = parent.getLevel() + 1;
            parentPath = parent.getTreePath() == null ? "" : parent.getTreePath();
        }
        ProductCategory entity = new ProductCategory();
        entity.setParentId(parentId);
        entity.setLevel(level);
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setStatus(req.getStatus() == null ? STATUS_VALID : req.getStatus());
        entity.setTreePath("");
        save(entity);
        // 维护 tree_path（依赖雪花 id）
        entity.setTreePath(parentPath + "/" + entity.getId());
        updateById(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long id, ProductCategorySaveReqVO req) {
        ProductCategory entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "品类不存在：" + id);
        }
        if (StringUtils.hasText(req.getCode())
                && !req.getCode().equals(entity.getCode())
                && baseMapper.existsByCode(req.getCode(), id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "品类编码已存在：" + req.getCode());
        }
        // 被 SPU 引用时不允许改动编码（关键字段）
        if (StringUtils.hasText(req.getCode())
                && !req.getCode().equals(entity.getCode())
                && isReferencedBySpu(id)) {
            throw new BizException(ResultCode.BIZ_ERROR, "品类被 SPU 引用，不可修改编码");
        }
        if (StringUtils.hasText(req.getCode())) {
            entity.setCode(req.getCode());
        }
        if (StringUtils.hasText(req.getName())) {
            entity.setName(req.getName());
        }
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        updateById(entity);
    }

    @Override
    public List<CategoryTreeNodeVO> tree() {
        List<ProductCategory> all = list(new LambdaQueryWrapper<ProductCategory>()
                .orderByAsc(ProductCategory::getLevel)
                .orderByAsc(ProductCategory::getId));
        List<CategoryTreeNodeVO> nodes = new ArrayList<>();
        for (ProductCategory c : all) {
            CategoryTreeNodeVO node = new CategoryTreeNodeVO();
            node.setId(c.getId());
            node.setParentId(c.getParentId());
            node.setCode(c.getCode());
            node.setName(c.getName());
            node.setLevel(c.getLevel());
            node.setStatus(c.getStatus());
            nodes.add(node);
        }
        return TreeUtils.build(nodes);
    }

    @Override
    public void invalidate(Long id) {
        ProductCategory entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "品类不存在：" + id);
        }
        entity.setStatus(STATUS_INVALID);
        updateById(entity);
    }

    @Override
    public void assertLeaf(Long categoryId) {
        if (categoryId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "品类不可为空");
        }
        ProductCategory category = getById(categoryId);
        if (category == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "品类不存在：" + categoryId);
        }
        if (category.getLevel() == null || category.getLevel() != MAX_LEVEL) {
            throw new BizException(ResultCode.BIZ_ERROR, "SPU 必须挂在三级（叶子）品类节点");
        }
        if (baseMapper.existsChildren(categoryId)) {
            throw new BizException(ResultCode.BIZ_ERROR, "该品类存在子节点，非叶子节点");
        }
    }

    private boolean isReferencedBySpu(Long categoryId) {
        Long count = spuMapper.selectCount(new LambdaQueryWrapper<Spu>()
                .eq(Spu::getCategoryId, categoryId));
        return count != null && count > 0;
    }
}
