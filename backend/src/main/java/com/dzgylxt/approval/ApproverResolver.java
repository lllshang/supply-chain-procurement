package com.dzgylxt.approval;

import com.dzgylxt.entity.approval.ApprovalNodeDef;
import com.dzgylxt.entity.identity.SysDept;
import com.dzgylxt.mapper.identity.SysDeptMapper;
import com.dzgylxt.mapper.identity.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 审批人解析器（P4 规格 §3.2 / 设计 §2.4）。
 *
 * <p>候选人<b>即时解析、不落库</b>：待办查询与 approve/reject 校验时按当前节点 def
 * 实时解析（角色成员变动即时生效，AC#3 覆盖）；节点快照仅固化 seq/code/sign_type（§2.9），
 * 不固化候选人——"流程稳定"（快照）与"人员及时"（实时解析）两者正交。</p>
 */
@Slf4j
@Component
public class ApproverResolver {

    /** approver_type 常量（对齐 approval_node_def.approver_type 字典）。 */
    public static final String TYPE_ROLE = "ROLE";
    public static final String TYPE_DEPT_HEAD_OF_APPLICANT = "DEPT_HEAD_OF_APPLICANT";
    public static final String TYPE_USER = "USER";

    /** DEPT_HEAD 上溯兜底角色（部门内无负责人时逐级上溯至根仍无 → 兜底采购部，设计 §2.4）。 */
    private static final String FALLBACK_ROLE = "PURCHASE_DEPT";
    /** 上溯保护上限（防环/防脏数据死循环）。 */
    private static final int MAX_DEPT_HOPS = 16;

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;

    public ApproverResolver(SysUserMapper sysUserMapper, SysDeptMapper sysDeptMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
    }

    /**
     * 解析当前节点候选审批人 userId 集合（空集=无可审批人，引擎按 FORBIDDEN 处理）。
     *
     * @param nodeDef    当前节点定义（审批人规则实时读取）
     * @param applicantId 申请人用户 id（DEPT_HEAD_OF_APPLICANT 定位部门用）
     */
    public Set<Long> resolveCandidates(ApprovalNodeDef nodeDef, Long applicantId) {
        if (nodeDef == null || nodeDef.getApproverType() == null) {
            return Collections.emptySet();
        }
        return switch (nodeDef.getApproverType()) {
            case TYPE_ROLE -> resolveRole(nodeDef.getApproverValue());
            case TYPE_DEPT_HEAD_OF_APPLICANT -> resolveDeptHead(applicantId);
            case TYPE_USER -> resolveUser(nodeDef.getApproverValue());
            default -> {
                log.warn("[P4-Resolver] 未知审批人解析类型 approverType={}", nodeDef.getApproverType());
                yield Collections.emptySet();
            }
        };
    }

    /** ROLE：持指定角色的全部在职用户。 */
    private Set<Long> resolveRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(sysUserMapper.selectUserIdsByRoleCode(roleCode));
    }

    /**
     * DEPT_HEAD_OF_APPLICANT：申请人所在部门内持 DEPT_HEAD 角色的用户；
     * 部门内无该角色 → 逐级上溯父部门（sys_dept.parent_id）；根部门仍无 →
     * 兜底 ROLE PURCHASE_DEPT 并写 WARN 日志（设计 §2.4）。
     */
    private Set<Long> resolveDeptHead(Long applicantId) {
        if (applicantId == null) {
            log.warn("[P4-Resolver] DEPT_HEAD_OF_APPLICANT 解析失败：任务缺 applicantId");
            return Collections.emptySet();
        }
        com.dzgylxt.entity.identity.SysUser applicant = sysUserMapper.selectById(applicantId);
        if (applicant == null || applicant.getMainDeptId() == null) {
            log.warn("[P4-Resolver] DEPT_HEAD_OF_APPLICANT 解析失败：申请人不存在或无部门 applicantId={}", applicantId);
            return Collections.emptySet();
        }
        Long deptId = applicant.getMainDeptId();
        int hops = 0;
        while (deptId != null && hops++ < MAX_DEPT_HOPS) {
            Set<Long> heads = new LinkedHashSet<>(
                    sysUserMapper.selectUserIdsByDeptAndRole(deptId, "DEPT_HEAD"));
            if (!heads.isEmpty()) {
                return heads;
            }
            SysDept dept = sysDeptMapper.selectById(deptId);
            deptId = dept == null ? null : dept.getParentId();
            if (deptId != null && deptId == 0L) {
                break; // 已到根部门
            }
        }
        log.warn("[P4-Resolver] 申请人部门链上无 DEPT_HEAD，兜底角色 {} applicantId={}", FALLBACK_ROLE, applicantId);
        return new LinkedHashSet<>(sysUserMapper.selectUserIdsByRoleCode(FALLBACK_ROLE));
    }

    /** USER：按用户名直取在职用户（兜底慎用）。 */
    private Set<Long> resolveUser(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptySet();
        }
        Long id = sysUserMapper.selectEnabledIdByUsername(username);
        return id == null ? Collections.emptySet() : Set.of(id);
    }
}
