-- ============================================================
-- P3 R1 迁移：拆除 budget_line period=0 年度额度行（审计修正 R1）
-- 依据：《口径与PRD一致性核对报告》R1 + 《P3预算结算设计》§0R.1
--   - 额度控制键 = 部门 × 预算科目 × 月份（PRD §6.4.1 L609）；
--   - 年度 = 12 个月度行的聚合视图，不落库为独立额度行（PRD 数据模型 L1197 无年度行）；
--   - project_id 退出额度控制（列保留为统计冗余，不删除、不迁移数据）。
-- 幂等：重复执行无副作用（无 period=0 行时 DELETE 0 行）。
-- 执行：docker exec dzgylxt-p1-mysql mysql -uroot -p<pwd> supply_chain < p3_r1_migration.sql
-- ============================================================

-- 1) 清理 period=0 年度额度行（逻辑删除，保留留痕）
UPDATE budget_line SET deleted = 1
WHERE period = 0 AND deleted = 0;

-- 2) 同步列注释（口径固化：无年度额度行）
ALTER TABLE budget_line
    MODIFY COLUMN period INT NOT NULL DEFAULT 0
    COMMENT '月份 1-12（R1：period=0 年度额度行已拆除，年度=12 个月度行聚合视图）',
    MODIFY COLUMN project_id BIGINT NULL
    COMMENT '预算项目ID（R1：统计冗余，不参与额度控制）';

-- 3) 核对（人工验证）：剩余有效行应全部为月度行
-- SELECT period, COUNT(*) FROM budget_line WHERE deleted = 0 GROUP BY period;
