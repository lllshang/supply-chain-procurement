package com.dzgylxt.mapper.approval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.approval.ApprovalFlowDef;
import org.apache.ibatis.annotations.Mapper;

/** 审批流定义 Mapper（P4 表驱动）。 */
@Mapper
public interface ApprovalFlowDefMapper extends BaseMapper<ApprovalFlowDef> {
}
