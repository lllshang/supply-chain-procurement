-- ============================================================
-- P2b 迁移：扩展链路（D1 日常/框架 · D2 线下补录 · D9 独立寻源）
-- 依据：docs/P2b扩展链路规格设计.md §2 + PRD BR-07(L1082)/§6.6.1(L682)/§6.7.1(L743,L747)
-- 幂等：information_schema 判断列是否存在 + 列默认值判断，可重复执行。
-- ============================================================

SET NAMES utf8mb4;

-- ---------- ① inquiry：独立寻源（apply_id 可空 + 来源类型 + 寻源原因） ----------
DROP PROCEDURE IF EXISTS p2b_inquiry_offline;
DELIMITER $$
CREATE PROCEDURE p2b_inquiry_offline()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inquiry'
          AND COLUMN_NAME = 'apply_id' AND IS_NULLABLE = 'NO'
    ) THEN
        ALTER TABLE `inquiry` MODIFY COLUMN `apply_id` BIGINT NULL
            COMMENT '来源申请（D9 独立寻源为 NULL）';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inquiry'
          AND COLUMN_NAME = 'source_type'
    ) THEN
        ALTER TABLE `inquiry`
            ADD COLUMN `source_type` VARCHAR(20) NOT NULL DEFAULT 'APPLY'
            COMMENT '询价来源：APPLY=申请转询价 / OFFLINE=独立寻源（BR-07）' AFTER `apply_id`,
            ADD COLUMN `source_reason` VARCHAR(500) NULL
            COMMENT '寻源原因（source_type=OFFLINE 必填）' AFTER `source_type`;
    END IF;
END$$
DELIMITER ;
CALL p2b_inquiry_offline();
DROP PROCEDURE IF EXISTS p2b_inquiry_offline;

-- ---------- ② award：线下定标登记（inquiry_id 可空 + 预算锚点 部门×科目） ----------
DROP PROCEDURE IF EXISTS p2b_award_offline;
DELIMITER $$
CREATE PROCEDURE p2b_award_offline()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'award'
          AND COLUMN_NAME = 'inquiry_id' AND IS_NULLABLE = 'NO'
    ) THEN
        ALTER TABLE `award` MODIFY COLUMN `inquiry_id` BIGINT NULL
            COMMENT '来源询价（D9 线下直接登记为 NULL）';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'award'
          AND COLUMN_NAME = 'dept_id'
    ) THEN
        ALTER TABLE `award`
            ADD COLUMN `dept_id` BIGINT NULL
            COMMENT '预算部门（线下登记必填：CP-11 锚点=award，提交即占预算）' AFTER `apply_id`,
            ADD COLUMN `subject_id` BIGINT NULL
            COMMENT '预算科目（线下登记必填：占用量化键之一）' AFTER `dept_id`;
    END IF;
END$$
DELIMITER ;
CALL p2b_award_offline();
DROP PROCEDURE IF EXISTS p2b_award_offline;

-- ---------- ③ purchase_apply：S9 剩余字段（用途 / 手工项目名） ----------
DROP PROCEDURE IF EXISTS p2b_apply_fields;
DELIMITER $$
CREATE PROCEDURE p2b_apply_fields()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply'
          AND COLUMN_NAME = 'purpose'
    ) THEN
        ALTER TABLE `purchase_apply`
            ADD COLUMN `purpose` VARCHAR(500) NULL
            COMMENT '采购用途（PR-01）' AFTER `budget_subject_id`,
            ADD COLUMN `project_name` VARCHAR(200) NULL
            COMMENT '手工项目名（PR-01：无预算项目时的业务归属）' AFTER `purpose`;
    END IF;
END$$
DELIMITER ;
CALL p2b_apply_fields();
DROP PROCEDURE IF EXISTS p2b_apply_fields;

-- ---------- ④ contract：S8 预算科目（统计冗余） ----------
DROP PROCEDURE IF EXISTS p2b_contract_subject;
DELIMITER $$
CREATE PROCEDURE p2b_contract_subject()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract'
          AND COLUMN_NAME = 'subject_id'
    ) THEN
        ALTER TABLE `contract`
            ADD COLUMN `subject_id` BIGINT NULL
            COMMENT '预算科目（S8：统计冗余，预算锚点仍=申请/award）' AFTER `contract_type`;
    END IF;
END$$
DELIMITER ;
CALL p2b_contract_subject();
DROP PROCEDURE IF EXISTS p2b_contract_subject;

-- 核对（人工验证）：
-- SHOW COLUMNS FROM inquiry LIKE 'source_type';
-- SHOW COLUMNS FROM award LIKE 'subject_id';
-- SHOW COLUMNS FROM purchase_apply LIKE 'purpose';
-- SHOW COLUMNS FROM contract LIKE 'subject_id';

-- P2b-9 口径迁移：RELEASE 流水符号口径统一（写侧一律正数，查询侧取负）
-- =====================================================================
-- 背景（P2b 第 2 轮 QA 三实锤）：历史 release() 写流水时 take.negate()（RELEASE 落负值），
-- 而查询 SQL 对 action IN (1,2) 取负 → 双重取反 → 有释放历史的 bizId 占用总额翻倍
-- （驳回后重提 3002 / 作废按翻倍值释放吞掉同行占用 / 订单取消多释放）。
-- 修复后写侧统一落正数（动作语义由 action 表达），本脚本把存量负值 RELEASE 行翻正。
--
-- 口径说明：RELEASE 枚举 code = 1（WRITE_OFF = 2，历史恒落正值，无需处理；
-- ADJUST = 3 带符号、不参与占用恒等式，亦无需处理）。
--
-- 幂等性：仅匹配 amount < 0 的行，翻正后再次执行无匹配（no-op）。
-- 适用库：所有存在 budget_occupy_log 历史数据的库（supply_chain / scm_e2e / scm_qa_p2b2 等）。
-- =====================================================================

UPDATE budget_occupy_log
   SET amount = -amount
 WHERE deleted = 0
   AND action = 1        -- RELEASE（BudgetAction.RELEASE.getValue()）
   AND amount < 0;       -- 仅历史"落负值"行；新口径写入恒为正，重跑为 no-op

-- 验证（可选执行）：翻正后不应再有负值 RELEASE 行
-- SELECT COUNT(*) FROM budget_occupy_log WHERE deleted = 0 AND action = 1 AND amount < 0;
