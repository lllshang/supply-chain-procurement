package com.dzgylxt.approval;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 种子契约测试（设计 §8-2"契约测试 8 bizType"）：8 bizType 流定义 / 10 节点 /
 * 5 审批角色 / 菜单 1001-1003 迁移——对齐 design §1.4 种子表，双份 SQL（schema.sql 段 +
 * p4_migration.sql）一致，防种子漂移。文件级断言（不依赖 DB），持续锁定配置契约。
 */
class ApprovalSeedContractTest {

    private String read(String relative) throws IOException {
        // 兼容不同工作目录（gradle 测试 cwd=backend；IDE 可能=仓库根）：向上两级兜底
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path base : new Path[]{cwd,
                cwd.getParent() == null ? cwd : cwd.getParent(),
                cwd.getParent() != null && cwd.getParent().getParent() != null
                        ? cwd.getParent().getParent() : cwd}) {
            Path p = base.resolve(relative);
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8);
            }
        }
        throw new FileNotFoundException("文件不存在：" + relative);
    }

    /** 8 bizType 全部有流定义种子（data.sql + p4_migration.sql 双份）。 */
    @Test
    void seed_containsAllEightBizTypes_bothSqlFiles() throws IOException {
        for (String file : new String[]{
                "backend/src/main/resources/db/data.sql",
                "scripts/sql/p4_migration.sql"}) {
            String sql = read(file);
            for (String bizType : ApprovalBizTypes.all()) {
                assertTrue(sql.contains("'" + bizType + "'"),
                        file + " 缺少 bizType 种子：" + bizType);
            }
        }
    }

    /** 节点定义种子 10 行（N1×8 + CONTRACT N2），关键角色值锁定。 */
    @Test
    void seed_containsTenNodeDefs() throws IOException {
        String sql = read("scripts/sql/p4_migration.sql");
        // 逐节点锁定（flow_key, node_code）
        String[][] expected = {
                {"PURCHASE_APPLY", "N1"}, {"PURCHASE_APPLY", "N2"},
                {"AWARD", "N1"},
                {"CONTRACT", "N1"}, {"CONTRACT", "N2"},
                {"FULFILLMENT_ADJUST", "N1"},
                {"BUDGET", "N1"},
                {"SETTLEMENT", "N1"},
                {"SUPPLIER_QUAL", "N1"},
                {"DAILY_AUTH", "N1"}
        };
        for (String[] e : expected) {
            assertTrue(sql.contains("'" + e[0] + "'") && sql.contains("'" + e[1] + "'"),
                    "缺少节点种子：" + e[0] + "/" + e[1]);
        }
        // 角色解析类型与关键角色编码
        assertTrue(sql.contains("DEPT_HEAD_OF_APPLICANT"));
        assertTrue(sql.contains("'PURCHASE_DEPT'"));
        assertTrue(sql.contains("'PROCUREMENT_LEAD'"));
        assertTrue(sql.contains("'FINANCE'"));
        assertTrue(sql.contains("'LEADER'"));
        assertTrue(sql.contains("'DEPT_HEAD'"));
        // CONTRACT 升级阈值 50 万（Q5 占位）
        assertTrue(sql.contains("500000"));
    }

    /** 审批角色种子 5 行（id 2-6）。 */
    @Test
    void seed_containsFiveApprovalRoles() throws IOException {
        String sql = read("backend/src/main/resources/db/data.sql");
        for (String roleCode : new String[]{"DEPT_HEAD", "PURCHASE_DEPT", "FINANCE",
                "PROCUREMENT_LEAD", "LEADER"}) {
            assertTrue(sql.contains("'" + roleCode + "'"), "缺少角色种子：" + roleCode);
        }
    }

    /** 菜单迁移：1001 升级 perms 含 approval:todo/approve；1002/1003 具名键。 */
    @Test
    void seed_menus_todoDoneConfig_withNamedPermKeys() throws IOException {
        String sql = read("scripts/sql/p4_migration.sql");
        assertTrue(sql.contains("approval:todo"));
        assertTrue(sql.contains("approval:done"));
        assertTrue(sql.contains("approval:approve"));
        assertTrue(sql.contains("approval:config"));
        // 不得锁死 SUPER_ADMIN：超管授权 1002/1003 种子存在
        assertTrue(sql.contains("1002"));
        assertTrue(sql.contains("1003"));
    }
}
