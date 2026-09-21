package com.dzgylxt.common;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 分布式 ID 生成器（基于 Hutool Snowflake）。
 *
 * <p>用于非数据库主键的业务单号 / 文件 key 等（数据库主键使用 MyBatis-Plus ASSIGN_ID）。</p>
 */
public final class IdGenerator {

    /** 终端 ID 与数据中心 ID（生产可注入，避免冲突） */
    private static final long WORKER_ID = 1L;
    private static final long DATA_CENTER_ID = 1L;

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(WORKER_ID, DATA_CENTER_ID);

    private IdGenerator() {
    }

    /** 生成雪花长整型 ID */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /** 生成字符串 ID */
    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    /** 生成业务单号，前缀 + yyyyMMdd + 序列 */
    public static String nextNo(String prefix) {
        return prefix + IdUtil.getSnowflake(0, 0).nextId();
    }
}
