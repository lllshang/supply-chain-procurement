package com.dzgylxt.permission;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import org.springframework.stereotype.Component;

/**
 * 数据权限拦截器（骨架）。
 *
 * <p>一期实现为「按当前登录用户主部门注入 {@code dept_id} 过滤」的结构占位：
 * 当前返回 {@code null} 表示不做额外限制，避免对无 dept_id 列的表造成 SQL 错误。
 * 后续按 includeTables/excludeTables 与数据权限范围（本人/本部门/全部）扩展为实际条件。</p>
 */
@Component
public class DataPermissionInterceptor extends com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor {

    public DataPermissionInterceptor() {
        DataPermissionHandler handler = (where, mappedStatementId) -> null;
        setDataPermissionHandler(handler);
    }
}
