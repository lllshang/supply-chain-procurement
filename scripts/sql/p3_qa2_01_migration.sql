-- ============================================================
-- P3 QA2-01 迁移：purchase_apply 补预算科目字段（额度控制键=部门×月份×科目）
-- 依据：QA2-01（🔴 运行时控制键缺"科目"维度）+ PRD PR-01（申请填写科目）+ §6.4.1 L609
-- 幂等：借助 information_schema 判断列是否存在，可重复执行。
-- 存量数据：置空待补录（补录后可正常提交/下单；QA2-07 会拦截无占用申请，不绕过硬控）。
-- ============================================================

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS qa2_01_add_column;
DELIMITER $$
CREATE PROCEDURE qa2_01_add_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply'
          AND COLUMN_NAME = 'budget_subject_id'
    ) THEN
        ALTER TABLE `purchase_apply`
            ADD COLUMN `budget_subject_id` BIGINT NULL
            COMMENT '预算科目ID（QA2-01：额度控制键=部门×月份×科目，提交前必填；存量置空待补录）'
            AFTER `budget_status`;
    END IF;
END$$
DELIMITER ;

CALL qa2_01_add_column();
DROP PROCEDURE IF EXISTS qa2_01_add_column;

-- 核对（人工验证）：SHOW COLUMNS FROM purchase_apply LIKE 'budget_subject_id';
