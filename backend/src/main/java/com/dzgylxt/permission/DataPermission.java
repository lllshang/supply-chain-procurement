package com.dzgylxt.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解（部门隔离骨架）。
 *
 * <p>标记在 Mapper 方法或 Service 方法上，由 {@link DataPermissionInterceptor} 拦截并注入部门过滤条件。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {

    /** 仅对指定表生效（表名小写） */
    String[] includeTables() default {};

    /** 排除指定表 */
    String[] excludeTables() default {};
}
