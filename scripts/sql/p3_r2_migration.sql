-- ============================================================
-- P3 R2 迁移：定标回退单中标供应商（审计修正 R2）
-- 依据：《口径与PRD一致性核对报告》R2 + 《P3预算结算设计》§0R.2 方向 A
--   - award 按 inquiry 唯一中标（单数语义恢复，PRD 数据模型 awarded_supplier_id 单数）；
--   - award_item 仅保留明细行（同一供应商，模型保留不删表，拆标需求已登记台账）；
--   - 合同金额 = 定标金额（可校验），删除"按 award_item 供应商份额校验"（代码侧已改）。
-- 幂等：借助 information_schema 判断索引是否存在，可重复执行。
-- ============================================================

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS p3_r2_add_index;
DELIMITER $$
CREATE PROCEDURE p3_r2_add_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'award' AND INDEX_NAME = 'uk_award_inquiry'
    ) THEN
        -- 若存量数据存在同询价多定标（拆标历史），先逻辑删除较旧记录仅保留最新一条
        UPDATE award a
        JOIN (
            SELECT inquiry_id, MAX(id) AS keep_id
            FROM award WHERE deleted = 0 GROUP BY inquiry_id HAVING COUNT(*) > 1
        ) d ON a.inquiry_id = d.inquiry_id AND a.id < d.keep_id
        SET a.deleted = 1
        WHERE a.deleted = 0;
        SET @ddl = 'ALTER TABLE `award` ADD UNIQUE KEY `uk_award_inquiry` (`inquiry_id`, `deleted`)';
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL p3_r2_add_index();
DROP PROCEDURE IF EXISTS p3_r2_add_index;

-- 核对（人工验证）：SHOW INDEX FROM award WHERE Key_name = 'uk_award_inquiry';
