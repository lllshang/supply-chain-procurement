package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.SpecOption;
import com.dzgylxt.vo.catalog.SpecOptionSaveReqVO;

import java.util.List;
import java.util.Map;

/**
 * 规格配置服务。
 */
public interface ISpecOptionService extends IService<SpecOption> {

    /** 新增规格值：(specName,specValue) 唯一。 */
    Long createSpecOption(SpecOptionSaveReqVO req);

    /** 编辑规格值。 */
    void updateSpecOption(Long id, SpecOptionSaveReqVO req);

    /** 规格名 -> 值列表（供多规格组合）。 */
    Map<String, List<String>> groupedOptions();

    /** 置无效：被 SKU 引用则拒绝。 */
    void invalidate(Long id);
}
