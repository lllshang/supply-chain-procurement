-- ============================================================
-- D16 无申请来源订单「需求来源」留痕 —— 增量迁移脚本（幂等）
-- 对应 docs/D16需求来源留痕_规格设计.md
-- 供存量库手工执行： mysql -u<user> -p <db> < scripts/sql/d16_schema.sql
-- 注：新建库无需本脚本——backend/src/main/resources/db/schema.sql 已含本批全部列
--     （spring.sql.init mode=always 自动初始化）。本脚本经 information_schema 判重，
--     可重复执行（已存在则跳过，no-op）。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELIMITER $$

DROP PROCEDURE IF EXISTS d16_add_source_columns $$
CREATE PROCEDURE d16_add_source_columns()
BEGIN
    -- source_type
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'purchase_order'
                     AND COLUMN_NAME = 'source_type') THEN
        ALTER TABLE `purchase_order`
          ADD COLUMN `source_type` VARCHAR(20) NULL
            COMMENT '需求来源类型：APPLY=采购申请来源/OFFLINE=无申请来源(日常采购/框架合同直发)' AFTER `auth_over_limit`;
    END IF;

    -- source_reason
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'purchase_order'
                     AND COLUMN_NAME = 'source_reason') THEN
        ALTER TABLE `purchase_order`
          ADD COLUMN `source_reason` VARCHAR(500) NULL
            COMMENT '需求来源说明(applyId==null 必填)' AFTER `source_type`;
    END IF;
END $$

DELIMITER ;

CALL d16_add_source_columns();
DROP PROCEDURE IF EXISTS d16_add_source_columns;

SET FOREIGN_KEY_CHECKS = 1;
