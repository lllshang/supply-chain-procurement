package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.Unit;
import com.dzgylxt.vo.catalog.UnitSaveReqVO;

/**
 * 计量单位字典服务。
 */
public interface IUnitService extends IService<Unit> {

    /** 新增单位：code 唯一。 */
    Long createUnit(UnitSaveReqVO req);

    /** 编辑单位。 */
    void updateUnit(Long id, UnitSaveReqVO req);

    /** 置无效：被 SKU/换算/绑定引用则拒绝。 */
    void invalidate(Long id);

    /** 断言单位编码存在且有效。 */
    void assertExists(String code);
}
