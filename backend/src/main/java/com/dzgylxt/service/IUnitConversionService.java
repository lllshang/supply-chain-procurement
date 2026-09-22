package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.vo.catalog.UnitConversionSaveReqVO;

import java.util.List;

/**
 * 单位换算服务。
 */
public interface IUnitConversionService extends IService<UnitConversion> {

    /** 保存换算：rate>0；(fromUnit,生效期) 仅一条有效；变更生成新 version。 */
    Long saveConversion(UnitConversionSaveReqVO req);

    /** 当前生效版本（供 P2 落快照，本阶段仅暴露查询）。 */
    UnitConversion currentEffective(Long skuId, String fromUnit);

    /** 某 SKU 换算历史。 */
    List<UnitConversion> history(Long skuId);
}
