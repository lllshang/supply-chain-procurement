package com.dzgylxt.permission;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.dzgylxt.security.LoginUser;
import com.dzgylxt.security.UserContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 数据权限拦截器（阶段一实现）。
 *
 * <p>仅对实际存在 {@code dept_id} 列的业务表生效（{@link #DEPT_MAPPERS}）。
 * 非超级管理员访问这些表时，自动追加 {@code AND dept_id = 当前用户主部门}，
 * 实现「按部门隔离」的纵深防御。超级管理员（角色 SUPER_ADMIN）查看全部。
 * 当前 schema 中具备 dept_id 的业务表仅为 budget_header / purchase_apply，
 * 故仅对 BudgetHeaderMapper / PurchaseApplyMapper 生效；后续为合同、供应商等表
 * 补充 dept_id 列后，扩展本 Mapper 集合即可。</p>
 */
@Component
public class DataPermissionInterceptor
        extends com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor {

    /** 具备 dept_id 列、需要按部门隔离的业务表对应的 Mapper（阶段一仅 budget_header / purchase_apply） */
    private static final Set<String> DEPT_MAPPERS = Set.of("BudgetHeaderMapper", "PurchaseApplyMapper");

    public DataPermissionInterceptor() {
        setDataPermissionHandler(this::buildSegment);
    }

    private Expression buildSegment(Expression where, String mappedStatementId) {
        if (mappedStatementId == null || DEPT_MAPPERS.stream().noneMatch(mappedStatementId::contains)) {
            return null;
        }
        LoginUser user = UserContext.get();
        if (user == null) {
            return null;
        }
        // 超级管理员查看全部数据
        if (user.getRoles() != null && user.getRoles().contains("SUPER_ADMIN")) {
            return null;
        }
        Long deptId = user.getMainDeptId();
        if (deptId == null) {
            // 无部门归属时无可见数据（恒不成立条件，确保不越权读到其它部门）
            return new EqualsTo(new Column("dept_id"), new LongValue(-1L));
        }
        // 非超管仅可见本部门数据
        return new EqualsTo(new Column("dept_id"), new LongValue(deptId));
    }
}
