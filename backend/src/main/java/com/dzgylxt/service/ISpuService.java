package com.dzgylxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.Spu;
import com.dzgylxt.vo.catalog.SpuPageReqVO;
import com.dzgylxt.vo.catalog.SpuPageRespVO;
import com.dzgylxt.vo.catalog.SpuSaveReqVO;

/**
 * 商品 SPU 服务。
 */
public interface ISpuService extends IService<Spu> {

    /** 新增 SPU：code 唯一 + 品类叶子校验 + 主图落 file_meta。 */
    Long createSpu(SpuSaveReqVO req);

    /** 编辑 SPU。 */
    void updateSpu(Long id, SpuSaveReqVO req);

    /** 启用（status=0）。 */
    void enable(Long id);

    /** 停用（status=1）：联动其下 SKU 不可被新单据引用（P1 仅置状态）。 */
    void disable(Long id);

    /** 分页查询（筛选：category/status/关键字）。 */
    IPage<SpuPageRespVO> pageSpu(SpuPageReqVO req);
}
