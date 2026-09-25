package com.dzgylxt.mapper.approval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.approval.ApprovalNodeDef;
import org.apache.ibatis.annotations.Mapper;

/** 审批节点定义 Mapper（P4 表驱动）。 */
@Mapper
public interface ApprovalNodeDefMapper extends BaseMapper<ApprovalNodeDef> {
}
