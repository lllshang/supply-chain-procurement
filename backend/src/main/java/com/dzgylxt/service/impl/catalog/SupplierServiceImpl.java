package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.QualValidityCalculator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Supplier;
import com.dzgylxt.entity.catalog.SupplierCategory;
import com.dzgylxt.entity.catalog.SupplierQual;
import com.dzgylxt.enums.BlacklistFlag;
import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.enums.QualStatus;
import com.dzgylxt.enums.QualValidity;
import com.dzgylxt.enums.SupplierSource;
import com.dzgylxt.enums.SupplierStatus;
import com.dzgylxt.mapper.catalog.SupplierCategoryMapper;
import com.dzgylxt.mapper.catalog.SupplierMapper;
import com.dzgylxt.mapper.catalog.SupplierQualMapper;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import com.dzgylxt.vo.supplier.SupplierPageReqVO;
import com.dzgylxt.vo.supplier.SupplierPageRespVO;
import com.dzgylxt.vo.supplier.SupplierSaveReqVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 供应商服务实现。 */
@Service
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements ISupplierService {

    private final SupplierQualMapper supplierQualMapper;
    private final SupplierCategoryMapper supplierCategoryMapper;

    @Value("${app.supplier.qual-warn-days:30}")
    private int qualWarnDays;

    public SupplierServiceImpl(SupplierQualMapper supplierQualMapper,
                               SupplierCategoryMapper supplierCategoryMapper) {
        this.supplierQualMapper = supplierQualMapper;
        this.supplierCategoryMapper = supplierCategoryMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSupplier(SupplierSaveReqVO req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "供应商名称必填");
        }
        if (StringUtils.hasText(req.getCreditCode()) && baseMapper.existsByCreditCode(req.getCreditCode(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "统一社会信用代码已存在：" + req.getCreditCode());
        }
        if (req.getSupplierCategoryId() != null && supplierCategoryMapper.selectById(req.getSupplierCategoryId()) == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商分类不存在：" + req.getSupplierCategoryId());
        }
        Supplier entity = new Supplier();
        copy(req, entity);
        entity.setStatus(SupplierStatus.PENDING);
        entity.setCoopStatus(CoopStatus.NORMAL);
        entity.setIsBlacklist(BlacklistFlag.NO);
        entity.setSource(toSource(req.getSource()));
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSupplier(Long id, SupplierSaveReqVO req) {
        Supplier entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商不存在：" + id);
        }
        if (StringUtils.hasText(req.getCreditCode())
                && !req.getCreditCode().equals(entity.getCreditCode())
                && baseMapper.existsByCreditCode(req.getCreditCode(), id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "统一社会信用代码已存在：" + req.getCreditCode());
        }
        if (req.getSupplierCategoryId() != null
                && supplierCategoryMapper.selectById(req.getSupplierCategoryId()) == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商分类不存在：" + req.getSupplierCategoryId());
        }
        copy(req, entity);
        if (req.getSource() != null) {
            entity.setSource(toSource(req.getSource()));
        }
        updateById(entity);
    }

    @Override
    public IPage<SupplierPageRespVO> pageSupplier(SupplierPageReqVO req) {
        Page<Supplier> page = new Page<>(req.getCurrent(), req.getSize());
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getName())) {
            wrapper.like(Supplier::getName, req.getName());
        }
        if (req.getCategoryId() != null) {
            wrapper.eq(Supplier::getSupplierCategoryId, req.getCategoryId());
        }
        if (StringUtils.hasText(req.getLevel())) {
            wrapper.eq(Supplier::getLevel, req.getLevel());
        }
        if (req.getCoopStatus() != null) {
            wrapper.eq(Supplier::getCoopStatus, req.getCoopStatus());
        }
        if (req.getBlacklist() != null) {
            wrapper.eq(Supplier::getIsBlacklist, req.getBlacklist());
        }
        wrapper.orderByDesc(Supplier::getUpdatedAt);
        IPage<Supplier> result = page(page, wrapper);

        List<Long> categoryIds = result.getRecords().stream()
                .map(Supplier::getSupplierCategoryId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nameMap = categoryIds.isEmpty() ? Map.of()
                : supplierCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(SupplierCategory::getId, SupplierCategory::getName, (a, b) -> a));

        List<SupplierPageRespVO> records = new ArrayList<>();
        for (Supplier s : result.getRecords()) {
            SupplierPageRespVO vo = new SupplierPageRespVO();
            vo.setId(s.getId());
            vo.setName(s.getName());
            vo.setCreditCode(s.getCreditCode());
            vo.setLevel(s.getLevel());
            vo.setSupplierCategoryId(s.getSupplierCategoryId());
            vo.setCategoryName(nameMap.get(s.getSupplierCategoryId()));
            vo.setCoopStatus(s.getCoopStatus() == null ? null : s.getCoopStatus().getValue());
            vo.setIsBlacklist(s.getIsBlacklist() == null ? null : s.getIsBlacklist().getValue());
            vo.setSource(s.getSource() == null ? null : s.getSource().getValue());
            vo.setStatus(s.getStatus() == null ? null : s.getStatus().getValue());
            vo.setUpdatedAt(s.getUpdatedAt());
            records.add(vo);
        }
        Page<SupplierPageRespVO> respPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        respPage.setRecords(records);
        return respPage;
    }

    @Override
    public void updateCoopStatus(Long id, CoopStatus status) {
        Supplier entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商不存在：" + id);
        }
        if (status == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "合作状态必填");
        }
        entity.setCoopStatus(status);
        updateById(entity);
    }

    @Override
    public void markBlacklist(Long id, boolean blacklist) {
        Supplier entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商不存在：" + id);
        }
        entity.setIsBlacklist(blacklist ? BlacklistFlag.YES : BlacklistFlag.NO);
        updateById(entity);
    }

    @Override
    public SupplierAdmissionVO getAdmission(Long supplierId) {
        Supplier supplier = getById(supplierId);
        if (supplier == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商不存在：" + supplierId);
        }
        List<String> reasons = new ArrayList<>();
        boolean coopOk = supplier.getCoopStatus() == CoopStatus.NORMAL;
        if (!coopOk) {
            reasons.add("合作状态非正常：" + (supplier.getCoopStatus() == null ? "未知" : supplier.getCoopStatus().getDesc()));
        }
        boolean notBlack = supplier.getIsBlacklist() != BlacklistFlag.YES;
        if (!notBlack) {
            reasons.add("已列入黑名单");
        }
        List<SupplierQual> quals = supplierQualMapper.selectBySupplier(supplierId);
        // 文档口径（R-X-01 / §2.3.3）：准入需存在 status=1 且“未过期”的必要资质；
        // “未过期” = VALID + EXPIRING（R-SUP-04 三态；R-SUP-05 到期预警仅提示、不阻断采购闭环），
        // 仅 EXPIRED 阻断。黑名单 / coop_status 判定保持不变。
        boolean hasUnexpiredQual = false;
        QualValidity overall = null;
        for (SupplierQual qual : quals) {
            if (qual.getStatus() != QualStatus.APPROVED) {
                continue;
            }
            QualValidity validity = QualValidityCalculator.compute(qual.getExpireAt(), qualWarnDays);
            if (validity == QualValidity.VALID || validity == QualValidity.EXPIRING) {
                hasUnexpiredQual = true;
            }
            overall = better(overall, validity);
        }
        if (!hasUnexpiredQual) {
            reasons.add("无有效资质");
        }
        SupplierAdmissionVO vo = new SupplierAdmissionVO();
        vo.setSupplierId(supplierId);
        vo.setCoopStatus(supplier.getCoopStatus() == null ? null : supplier.getCoopStatus().getValue());
        vo.setIsBlacklist(supplier.getIsBlacklist() == null ? null : supplier.getIsBlacklist().getValue());
        vo.setQualValidity(overall == null ? "NONE" : overall.name());
        vo.setQualified(coopOk && notBlack && hasUnexpiredQual);
        vo.setReasons(reasons);
        return vo;
    }

    /** 取更"好"的有效期状态：VALID &gt; EXPIRING &gt; EXPIRED。 */
    private QualValidity better(QualValidity current, QualValidity candidate) {
        if (current == null) {
            return candidate;
        }
        return current.ordinal() <= candidate.ordinal() ? current : candidate;
    }

    private void copy(SupplierSaveReqVO req, Supplier entity) {
        entity.setName(req.getName());
        entity.setCreditCode(req.getCreditCode());
        entity.setLevel(req.getLevel());
        entity.setSupplierCategoryId(req.getSupplierCategoryId());
        entity.setLegalPerson(req.getLegalPerson());
        entity.setBusinessScope(req.getBusinessScope());
        entity.setContact(req.getContact());
        entity.setPhone(req.getPhone());
        entity.setBankName(req.getBankName());
        entity.setBankAccount(req.getBankAccount());
    }

    private SupplierSource toSource(Integer value) {
        if (value == null) {
            return SupplierSource.PLATFORM;
        }
        for (SupplierSource source : SupplierSource.values()) {
            if (source.getValue().equals(value)) {
                return source;
            }
        }
        throw new BizException(ResultCode.PARAM_ERROR, "未知来源：" + value);
    }
}
