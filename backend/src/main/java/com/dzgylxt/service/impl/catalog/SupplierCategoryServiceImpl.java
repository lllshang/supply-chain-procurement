package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.TreeUtils;
import com.dzgylxt.entity.catalog.Supplier;
import com.dzgylxt.entity.catalog.SupplierCategory;
import com.dzgylxt.mapper.catalog.SupplierCategoryMapper;
import com.dzgylxt.mapper.catalog.SupplierMapper;
import com.dzgylxt.service.ISupplierCategoryService;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
import com.dzgylxt.vo.supplier.SupplierCategorySaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 供应商三级分类服务实现。 */
@Service
public class SupplierCategoryServiceImpl extends ServiceImpl<SupplierCategoryMapper, SupplierCategory>
        implements ISupplierCategoryService {

    private static final int STATUS_VALID = 0;
    private static final int STATUS_INVALID = 1;
    private static final int MAX_LEVEL = 3;

    private final SupplierMapper supplierMapper;

    public SupplierCategoryServiceImpl(SupplierMapper supplierMapper) {
        this.supplierMapper = supplierMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(SupplierCategorySaveReqVO req) {
        if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "编码与名称必填");
        }
        if (baseMapper.existsByCode(req.getCode(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "分类编码已存在：" + req.getCode());
        }
        long parentId = req.getParentId() == null ? 0L : req.getParentId();
        int level = 1;
        String parentPath = "";
        if (parentId != 0L) {
            SupplierCategory parent = getById(parentId);
            if (parent == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "父分类不存在：" + parentId);
            }
            if (parent.getLevel() == null || parent.getLevel() >= MAX_LEVEL) {
                throw new BizException(ResultCode.BIZ_ERROR, "最多支持三级分类，父节点不可再挂子节点");
            }
            level = parent.getLevel() + 1;
            parentPath = parent.getTreePath() == null ? "" : parent.getTreePath();
        }
        SupplierCategory entity = new SupplierCategory();
        entity.setParentId(parentId);
        entity.setLevel(level);
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setStatus(req.getStatus() == null ? STATUS_VALID : req.getStatus());
        entity.setTreePath("");
        save(entity);
        entity.setTreePath(parentPath + "/" + entity.getId());
        updateById(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long id, SupplierCategorySaveReqVO req) {
        SupplierCategory entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "分类不存在：" + id);
        }
        if (StringUtils.hasText(req.getCode())
                && !req.getCode().equals(entity.getCode())
                && baseMapper.existsByCode(req.getCode(), id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "分类编码已存在：" + req.getCode());
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
        List<SupplierCategory> all = list(new LambdaQueryWrapper<SupplierCategory>()
                .orderByAsc(SupplierCategory::getLevel)
                .orderByAsc(SupplierCategory::getId));
        List<CategoryTreeNodeVO> nodes = new ArrayList<>();
        for (SupplierCategory c : all) {
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
        SupplierCategory entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "分类不存在：" + id);
        }
        Long refCount = supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCategoryId, id));
        if (refCount != null && refCount > 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "分类被供应商引用，不可置无效");
        }
        entity.setStatus(STATUS_INVALID);
        updateById(entity);
    }
}
