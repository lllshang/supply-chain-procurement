package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.SupplierSku;
import com.dzgylxt.vo.supplier.BatchBindRespVO;
import com.dzgylxt.vo.supplier.SupplierSkuRespVO;
import com.dzgylxt.vo.supplier.SupplierSkuSaveReqVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 供应商-商品绑定服务。
 */
public interface ISupplierSkuService extends IService<SupplierSku> {

    /** 绑定：(supplier,sku) 唯一 + supplyPrice≥0 + packageUnit 存在。 */
    Long bind(SupplierSkuSaveReqVO req);

    /** 解绑。 */
    void unbind(Long id);

    /** 批量绑定（Excel）。 */
    BatchBindRespVO batchBind(MultipartFile file);

    /** 某供应商全部绑定。 */
    List<SupplierSkuRespVO> listBySupplier(Long supplierId);
}
