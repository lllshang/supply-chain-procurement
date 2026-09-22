package com.dzgylxt.mapper.budget;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.budget.BudgetProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 预算项目 Mapper。 */
@Mapper
public interface BudgetProjectMapper extends BaseMapper<BudgetProject> {

    /** 编码在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM budget_project WHERE deleted = 0 AND code = #{code}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsByCode(@Param("code") String code, @Param("excludeId") Long excludeId);
}
