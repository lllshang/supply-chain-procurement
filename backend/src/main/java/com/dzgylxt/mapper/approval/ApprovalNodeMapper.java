package com.dzgylxt.mapper.approval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.approval.ApprovalNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 审批节点 Mapper。 */
@Mapper
public interface ApprovalNodeMapper extends BaseMapper<ApprovalNode> {

    /**
     * P4 并发防护（规格 §3.4）：节点行条件更新（version 乐观锁），仅 PENDING 可完成。
     * 通过时回填 approver/action/comment/status=DONE(1)。
     *
     * @return 影响行数（1=成功，0=并发冲突/已处理）
     */
    @Update("UPDATE approval_node SET approver = #{approver}, action = #{action}, "
            + "comment = #{comment}, status = 1, version = version + 1 "
            + "WHERE id = #{id} AND version = #{version} AND (status IS NULL OR status = 0) AND deleted = 0")
    int casComplete(@Param("id") Long id,
                    @Param("version") Integer version,
                    @Param("approver") Long approver,
                    @Param("action") String action,
                    @Param("comment") String comment);
}
