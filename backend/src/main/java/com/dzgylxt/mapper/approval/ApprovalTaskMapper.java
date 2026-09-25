package com.dzgylxt.mapper.approval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.enums.ApprovalStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 审批单据 Mapper。 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    /**
     * P4 并发防重审（规格 §3.4）：version 乐观锁 + 状态机条件更新（双保险）。
     * 仅 CREATED(0)/IN_PROGRESS(1) 可流转；影响行数=0 视为单据已被处理（DATA_CONFLICT 3002）。
     *
     * @return 影响行数（1=流转成功，0=并发冲突/终态）
     */
    @Update("UPDATE approval_task SET status = #{status}, current_node = #{currentNode}, "
            + "version = version + 1, remark = #{remark} "
            + "WHERE id = #{id} AND version = #{version} AND status IN (0, 1) AND deleted = 0")
    int casTransition(@Param("id") Long id,
                      @Param("version") Integer version,
                      @Param("status") ApprovalStatus status,
                      @Param("currentNode") String currentNode,
                      @Param("remark") String remark);
}
