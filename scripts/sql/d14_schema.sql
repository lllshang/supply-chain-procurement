-- ============================================================
-- D14 日常采购「授权」控制 —— 增量迁移脚本（幂等）
-- 对应 docs/D14日常采购授权控制_规格设计.md
-- 供存量库手工执行： mysql -u<user> -p <db> < scripts/sql/d14_schema.sql
-- 注：新建库无需本脚本——backend/src/main/resources/db/schema.sql 已含本批全部列
--     （spring.sql.init mode=always 自动初始化）。本脚本经 information_schema 判重，
--     可重复执行（已存在则跳过，no-op）。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELIMITER $$

DROP PROCEDURE IF EXISTS d14_add_auth_columns $$
CREATE PROCEDURE d14_add_auth_columns()
BEGIN
    -- authorized_by
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'purchase_order'
                     AND COLUMN_NAME = 'authorized_by') THEN
        ALTER TABLE `purchase_order`
          ADD COLUMN `authorized_by` BIGINT NULL
            COMMENT '授权人用户ID（日常采购=订单创建人）' AFTER `remark`;
    END IF;

    -- authorized_name
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'purchase_order'
                     AND COLUMN_NAME = 'authorized_name') THEN
        ALTER TABLE `purchase_order`
          ADD COLUMN `authorized_name` VARCHAR(50) NULL
            COMMENT '授权人姓名/账号（冗余便于审计）' AFTER `authorized_by`;
    END IF;

    -- authorized_time
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'purchase_order'
                     AND COLUMN_NAME = 'authorized_time') THEN
        ALTER TABLE `purchase_order`
          ADD COLUMN `authorized_time` DATETIME NULL
            COMMENT '授权时间（=订单生成时间）' AFTER `authorized_name`;
    END IF;

    -- auth_over_limit
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'purchase_order'
                     AND COLUMN_NAME = 'auth_over_limit') THEN
        ALTER TABLE `purchase_order`
          ADD COLUMN `auth_over_limit` TINYINT NULL DEFAULT 0
            COMMENT '是否超单笔授权额度升级(0否 1是)' AFTER `authorized_time`;
    END IF;
END $$

DELIMITER ;

CALL d14_add_auth_columns();
DROP PROCEDURE IF EXISTS d14_add_auth_columns;

SET FOREIGN_KEY_CHECKS = 1;
