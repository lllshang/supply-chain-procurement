package com.dzgylxt.service.impl.catalog;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.SupplierSku;
import com.dzgylxt.enums.BindScope;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.SupplierMapper;
import com.dzgylxt.mapper.catalog.SupplierSkuMapper;
import com.dzgylxt.service.ISkuService;
import com.dzgylxt.service.ISupplierSkuService;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.common.ImportErrorVO;
import com.dzgylxt.vo.supplier.BatchBindItemVO;
import com.dzgylxt.vo.supplier.BatchBindRespVO;
import com.dzgylxt.vo.supplier.SupplierSkuRespVO;
import com.dzgylxt.vo.supplier.SupplierSkuSaveReqVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 供应商-商品绑定服务实现。 */
@Service
public class SupplierSkuServiceImpl extends ServiceImpl<SupplierSkuMapper, SupplierSku>
        implements ISupplierSkuService {

    private static final int STATUS_NORMAL = 0;

    private final SupplierMapper supplierMapper;
    private final SkuMapper skuMapper;
    private final ISkuService skuService;
    private final IUnitService unitService;

    public SupplierSkuServiceImpl(SupplierMapper supplierMapper,
                                  SkuMapper skuMapper,
                                  ISkuService skuService,
                                  IUnitService unitService) {
        this.supplierMapper = supplierMapper;
        this.skuMapper = skuMapper;
        this.skuService = skuService;
        this.unitService = unitService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long bind(SupplierSkuSaveReqVO req) {
        return doBind(req, null);
    }

    @Override
    public void unbind(Long id) {
        SupplierSku entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "绑定不存在：" + id);
        }
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchBindRespVO batchBind(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        List<BatchBindItemVO> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).head(BatchBindItemVO.class).sheet().doReadSync();
        } catch (IOException e) {
            throw new BizException(ResultCode.BIZ_ERROR, "文件解析失败：" + e.getMessage());
        }
        BatchBindRespVO resp = new BatchBindRespVO();
        resp.setTotalRows(rows.size());
        int success = 0;
        for (int i = 0; i < rows.size(); i++) {
            BatchBindItemVO row = rows.get(i);
            int rowNo = i + 2;
            try {
                SupplierSkuSaveReqVO req = new SupplierSkuSaveReqVO();
                req.setSupplierId(row.getSupplierId());
                req.setSkuId(row.getSkuId());
                req.setSupplierSkuCode(row.getSupplierSkuCode());
                req.setSupplyPrice(row.getSupplyPrice());
                req.setPackageUnit(row.getPackageUnit());
                req.setBindScope(row.getBindScope());
                doBind(req, null);
                success++;
            } catch (BizException e) {
                resp.getErrors().add(new ImportErrorVO(rowNo, "行", e.getMessage()));
            }
        }
        resp.setSuccessRows(success);
        resp.setFailRows(rows.size() - success);
        return resp;
    }

    @Override
    public List<SupplierSkuRespVO> listBySupplier(Long supplierId) {
        List<SupplierSku> binds = list(new LambdaQueryWrapper<SupplierSku>()
                .eq(SupplierSku::getSupplierId, supplierId)
                .orderByDesc(SupplierSku::getId));
        Map<Long, String> skuCodeMap = binds.isEmpty() ? Map.of()
                : skuMapper.selectBatchIds(binds.stream().map(SupplierSku::getSkuId).distinct().toList())
                .stream().collect(Collectors.toMap(Sku::getId, Sku::getSkuCode, (a, b) -> a));
        List<SupplierSkuRespVO> result = new ArrayList<>();
        for (SupplierSku bind : binds) {
            SupplierSkuRespVO vo = new SupplierSkuRespVO();
            vo.setId(bind.getId());
            vo.setSupplierId(bind.getSupplierId());
            vo.setSkuId(bind.getSkuId());
            vo.setSkuCode(skuCodeMap.get(bind.getSkuId()));
            vo.setSupplierSkuCode(bind.getSupplierSkuCode());
            vo.setSupplyPrice(bind.getSupplyPrice());
            vo.setPackageUnit(bind.getPackageUnit());
            vo.setBindScope(bind.getBindScope() == null ? null : bind.getBindScope().getValue());
            vo.setStatus(bind.getStatus());
            result.add(vo);
        }
        return result;
    }

    /** 绑定核心逻辑（供单条与批量复用）。 */
    private Long doBind(SupplierSkuSaveReqVO req, Long excludeId) {
        if (req.getSupplierId() == null || supplierMapper.selectById(req.getSupplierId()) == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商不存在：" + req.getSupplierId());
        }
        if (req.getSkuId() == null || skuService.getById(req.getSkuId()) == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SKU 不存在：" + req.getSkuId());
        }
        if (baseMapper.existsBind(req.getSupplierId(), req.getSkuId(), excludeId)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "该供应商与 SKU 已存在有效绑定");
        }
        if (req.getSupplyPrice() != null && req.getSupplyPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "供货价不可为负");
        }
        if (StringUtils.hasText(req.getPackageUnit())) {
            unitService.assertExists(req.getPackageUnit());
        }
        SupplierSku entity = new SupplierSku();
        entity.setSupplierId(req.getSupplierId());
        entity.setSkuId(req.getSkuId());
        entity.setSupplierSkuCode(req.getSupplierSkuCode());
        entity.setSupplyPrice(req.getSupplyPrice());
        entity.setPackageUnit(req.getPackageUnit());
        entity.setBindScope(toBindScope(req.getBindScope()));
        entity.setStatus(STATUS_NORMAL);
        try {
            save(entity);
        } catch (DuplicateKeyException e) {
            // DB 唯一约束 uk_sup_sku(supplier_id, sku_id, deleted) 兜底并发/竞态下的重复绑定，
            // 转为与前置 existsBind 校验一致的友好提示。
            throw new BizException(ResultCode.DATA_CONFLICT, "该供应商与 SKU 已存在有效绑定");
        }
        return entity.getId();
    }

    private BindScope toBindScope(Integer value) {
        if (value == null) {
            return BindScope.UNLIMITED;
        }
        for (BindScope scope : BindScope.values()) {
            if (scope.getValue().equals(value)) {
                return scope;
            }
        }
        throw new BizException(ResultCode.PARAM_ERROR, "未知绑定范围：" + value);
    }
}
