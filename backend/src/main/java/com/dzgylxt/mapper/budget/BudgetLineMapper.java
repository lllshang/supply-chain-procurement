package com.dzgylxt.mapper.budget;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.budget.BudgetLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 预算明细 Mapper。 */
@Mapper
public interface BudgetLineMapper extends BaseMapper<BudgetLine> {

    /** 取某预算头下的全部明细（含 period 0 与 1–12，按科目/期间排序）。 */
    @Select("SELECT * FROM budget_line WHERE deleted = 0 AND header_id = #{headerId}"
            + " ORDER BY subject_id ASC, period ASC")
    List<BudgetLine> selectByHeader(@Param("headerId") Long headerId);

    /**
     * 按控制单元（部门×年度×科目×月份）锁行（FOR UPDATE，P3 设计 §3 校验事务主锁）。
     *
     * <p>锁序约定：同事务涉及多行时由调用方按 {@code budget_line.id} 升序逐行加锁，
     * 防交叉死锁；Redis 锁 {@code budget:lock:{deptId}:{subjectId}:{period}} 为多实例前置。</p>
     */
    @Select("SELECT bl.* FROM budget_line bl"
            + " JOIN budget_header bh ON bh.id = bl.header_id AND bh.deleted = 0"
            + " WHERE bl.deleted = 0 AND bh.dept_id = #{deptId} AND bh.year = #{year}"
            + " AND bl.subject_id = #{subjectId} AND bl.period = #{period}"
            + " FOR UPDATE")
    BudgetLine selectForUpdate(@Param("deptId") Long deptId, @Param("year") Integer year,
                               @Param("subjectId") Long subjectId, @Param("period") Integer period);

    /** 同控制单元的全部月度行（不锁；occupy 分摊按 id 升序遍历时逐行 FOR UPDATE）。 */
    @Select("SELECT bl.* FROM budget_line bl"
            + " JOIN budget_header bh ON bh.id = bl.header_id AND bh.deleted = 0"
            + " WHERE bl.deleted = 0 AND bh.dept_id = #{deptId} AND bh.year = #{year}"
            + " AND bl.subject_id = #{subjectId} AND bl.period BETWEEN 1 AND 12"
            + " ORDER BY bl.id ASC")
    List<BudgetLine> selectMonthlyLines(@Param("deptId") Long deptId, @Param("year") Integer year,
                                        @Param("subjectId") Long subjectId);

    /** 条件更新 used_amount（乐观锁 version 兜底；delta 为负时保证不减穿 0）。 */
    @Update("UPDATE budget_line SET used_amount = used_amount + #{delta}, version = version + 1,"
            + " updated_at = NOW() WHERE id = #{id} AND deleted = 0 AND version = #{version}"
            + " AND used_amount + #{delta} >= 0")
    int changeUsedAmount(@Param("id") Long id, @Param("delta") java.math.BigDecimal delta,
                         @Param("version") Integer version);

    /** 条件更新预算额（月度调整；version 兜底）。 */
    @Update("UPDATE budget_line SET amount = #{newAmount}, version = version + 1, updated_at = NOW()"
            + " WHERE id = #{id} AND deleted = 0 AND version = #{version}")
    int adjustAmount(@Param("id") Long id, @Param("newAmount") java.math.BigDecimal newAmount,
                     @Param("version") Integer version);

    /** 统计某科目是否被预算明细引用。 */
    @Select("SELECT COUNT(1) FROM budget_line WHERE deleted = 0 AND subject_id = #{subjectId}")
    boolean existsBySubject(@Param("subjectId") Long subjectId);

    /** 统计某项目是否被预算明细引用。 */
    @Select("SELECT COUNT(1) FROM budget_line WHERE deleted = 0 AND project_id = #{projectId}")
    boolean existsByProject(@Param("projectId") Long projectId);
}
