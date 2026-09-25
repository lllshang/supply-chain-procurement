-- ============================================================
-- D17 高频补货自动授权 —— 增量迁移脚本（幂等）
-- 对应 docs/D17高频补货自动授权_规格设计.md
-- 供存量库手工执行： mysql -u<user> -p <db> < scripts/sql/d17_schema.sql
-- 注：新建库无需本脚本——backend/src/main/resources/db/schema.sql 已含本列
--     （spring.sql.init mode=always 自动初始化）。本脚本经 information_schema 判重，
--     可重复执行（已存在则跳过，no-op）。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELIMITER $$

DROP PROCEDURE IF EXISTS d17_add_auto_authorized $$
CREATE PROCEDURE d17_add_auto_authorized()
BEGIN
    -- auto_authorized（D17：系统自动授权通过标记，与 auth_over_limit 正交）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'purchase_order'
                     AND COLUMN_NAME = 'auto_authorized') THEN
        ALTER TABLE `purchase_order`
          ADD COLUMN `auto_authorized` TINYINT NULL DEFAULT 0
            COMMENT '是否系统自动授权通过(0否 1是；D17开启且日常采购时跳过DAILY_AUTH人工升级，仍保留授权留痕)'
            AFTER `auth_over_limit`;
    END IF;
END $$

DELIMITER ;

CALL d17_add_auto_authorized();
DROP PROCEDURE IF EXISTS d17_add_auto_authorized;

SET FOREIGN_KEY_CHECKS = 1;
