package com.dzgylxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.vo.catalog.SkuPageReqVO;
import com.dzgylxt.vo.catalog.SkuPageRespVO;
import com.dzgylxt.vo.catalog.SkuSaveReqVO;

import java.util.List;

/**
 * 商品 SKU 服务。
 */
public interface ISkuService extends IService<Sku> {

    /** 新增 SKU：skuCode/barcode 唯一 + purchaseUnit 存在于 unit + 价格规则校验。 */
    Long createSku(SkuSaveReqVO req);

    /** 编辑 SKU。 */
    void updateSku(Long id, SkuSaveReqVO req);

    /** 启用。 */
    void enable(Long id);

    /** 停用。 */
    void disable(Long id);

    /** 分页查询。 */
    IPage<SkuPageRespVO> pageSku(SkuPageReqVO req);

    /** 某 SPU 下全部 SKU。 */
    List<Sku> listBySpu(Long spuId);
}
