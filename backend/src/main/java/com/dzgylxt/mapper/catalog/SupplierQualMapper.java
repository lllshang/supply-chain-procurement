package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.SupplierQual;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 供应商资质 Mapper。 */
@Mapper
public interface SupplierQualMapper extends BaseMapper<SupplierQual> {

    /** 某供应商的全部资质（新录入在前）。 */
    @Select("SELECT * FROM supplier_qual WHERE deleted = 0 AND supplier_id = #{supplierId}"
            + " ORDER BY id DESC")
    List<SupplierQual> selectBySupplier(@Param("supplierId") Long supplierId);
}
