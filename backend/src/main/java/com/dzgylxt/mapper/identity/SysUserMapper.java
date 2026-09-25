package com.dzgylxt.mapper.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.identity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用户 Mapper。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** P4 审批人解析（ROLE）：持指定角色的全部在职用户 id（sys_user.status=0，逻辑删除过滤）。 */
    @Select("SELECT DISTINCT u.id FROM sys_user u "
            + "JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0 "
            + "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 "
            + "WHERE r.role_code = #{roleCode} AND u.status = 0 AND u.deleted = 0")
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /** P4 审批人解析（DEPT_HEAD_OF_APPLICANT）：指定部门内持指定角色的在职用户 id。 */
    @Select("SELECT DISTINCT u.id FROM sys_user u "
            + "JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0 "
            + "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 "
            + "WHERE u.main_dept_id = #{deptId} AND r.role_code = #{roleCode} "
            + "AND u.status = 0 AND u.deleted = 0")
    List<Long> selectUserIdsByDeptAndRole(@Param("deptId") Long deptId, @Param("roleCode") String roleCode);

    /** P4 审批人解析（USER）：按用户名取在职用户 id。 */
    @Select("SELECT id FROM sys_user WHERE username = #{username} AND status = 0 AND deleted = 0 LIMIT 1")
    Long selectEnabledIdByUsername(@Param("username") String username);
}
