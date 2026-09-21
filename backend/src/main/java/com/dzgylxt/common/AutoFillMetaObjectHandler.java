package com.dzgylxt.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.dzgylxt.security.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器：写入 / 更新时填充审计列。
 */
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long uid = currentUserId();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", Long.class, uid);
        strictInsertFill(metaObject, "updatedBy", Long.class, uid);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId());
    }

    private Long currentUserId() {
        try {
            Long id = UserContext.getCurrentUserId();
            return id == null ? 0L : id;
        } catch (Exception e) {
            return 0L;
        }
    }
}
