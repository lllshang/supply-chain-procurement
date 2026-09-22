package com.dzgylxt.mapper.budget;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.budget.BudgetLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 预算明细 Mapper。 */
@Mapper
public interface BudgetLineMapper extends BaseMapper<BudgetLine> {

    /** 取某预算头下的全部明细（含 period 0 与 1–12，按科目/期间排序）。 */
    @Select("SELECT * FROM budget_line WHERE deleted = 0 AND header_id = #{headerId}"
            + " ORDER BY subject_id ASC, period ASC")
    List<BudgetLine> selectByHeader(@Param("headerId") Long headerId);

    /** 统计某科目是否被预算明细引用。 */
    @Select("SELECT COUNT(1) FROM budget_line WHERE deleted = 0 AND subject_id = #{subjectId}")
    boolean existsBySubject(@Param("subjectId") Long subjectId);

    /** 统计某项目是否被预算明细引用。 */
    @Select("SELECT COUNT(1) FROM budget_line WHERE deleted = 0 AND project_id = #{projectId}")
    boolean existsByProject(@Param("projectId") Long projectId);
}
