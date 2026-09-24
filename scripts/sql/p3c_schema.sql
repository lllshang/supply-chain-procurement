-- ============================================================
-- P3c 阶段（D13 字段补齐主批 A1–A6）—— 增量迁移脚本（幂等）
-- 对应 docs/P3c字段补齐规格设计.md；供存量库手工执行：
--   mysql -u<user> -p <db> < scripts/sql/p3c_schema.sql
-- 注：新建库无需本脚本——backend/src/main/resources/db/schema.sql 已含本批
--     全部建表/列（自动初始化一条路打通）。本脚本经 information_schema 判重，
--     可重复执行（已存在则跳过，no-op）。
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- A1 合同价格清单（PRD BR-26 L1101：合同价格必须与订单一致）
-- contract_price_item：下单第四重校验取数表。
--   无清单记录的合同 = 免价格校验（框架协议仅控金额 / 存量合同），走合同额度闸兜底；
--   有清单的合同，order_item 每行必须命中 sku 且价格一致，否则 4000 硬拦截。
-- order_item.contract_item_id：校验命中行的回填追溯。
-- ============================================================

DROP PROCEDURE IF EXISTS p3c_add_a1_objects;

DELIMITER $$

CREATE PROCEDURE p3c_add_a1_objects()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'contract_price_item') THEN
        CREATE TABLE `contract_price_item` (
            id             BIGINT        NOT NULL PRIMARY KEY,
            contract_id    BIGINT        NOT NULL COMMENT '合同ID',
            sku_id         BIGINT        NOT NULL COMMENT 'SKU',
            unit_price     DECIMAL(18,2) NULL COMMENT '含税单价（基本单位口径）',
            qty            DECIMAL(18,3) NULL COMMENT '数量（基本单位口径）',
            source_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '来源：1=定标继承 2=手工维护',
            remark         VARCHAR(255)  NULL,
            created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_by BIGINT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted TINYINT NOT NULL DEFAULT 0
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同价格清单（P3c-A1）';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'order_item'
                     AND COLUMN_NAME = 'contract_item_id') THEN
        ALTER TABLE `order_item`
          ADD COLUMN `contract_item_id` BIGINT NULL
            COMMENT '命中的合同价格清单行（P3c-A1 第四重校验回填）' AFTER `planned_qty`;
    END IF;
END$$

DELIMITER ;

CALL p3c_add_a1_objects();
DROP PROCEDURE IF EXISTS p3c_add_a1_objects;

-- ============================================================
-- A2 报价含税三件套（PRD L697/L709/L711/L717）
-- tax_rate 税率% / freight 运费 / delivery_days 承诺交期天数；
-- 导入时三件套必填，税率合法域 0–13；金额口径 = 含税单价 × 数量（不保留不含税净额）。
-- ============================================================

DROP PROCEDURE IF EXISTS p3c_add_a2_objects;

DELIMITER $$

CREATE PROCEDURE p3c_add_a2_objects()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'quotation'
                     AND COLUMN_NAME = 'tax_rate') THEN
        ALTER TABLE `quotation`
          ADD COLUMN `tax_rate` DECIMAL(5,2) NULL
            COMMENT '税率%（P3c-A2 含税口径，0–13）' AFTER `invalid`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'quotation'
                     AND COLUMN_NAME = 'freight') THEN
        ALTER TABLE `quotation`
          ADD COLUMN `freight` DECIMAL(18,2) NULL
            COMMENT '运费（P3c-A2 比价维度）' AFTER `tax_rate`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'quotation'
                     AND COLUMN_NAME = 'delivery_days') THEN
        ALTER TABLE `quotation`
          ADD COLUMN `delivery_days` INT NULL
            COMMENT '承诺交期天数（P3c-A2）' AFTER `freight`;
    END IF;
END$$

DELIMITER ;

CALL p3c_add_a2_objects();
DROP PROCEDURE IF EXISTS p3c_add_a2_objects;

-- P3c-A2：award_item 继承税率（定标含税口径与报价一致；线下定标手填，可空）
DROP PROCEDURE IF EXISTS p3c_add_a2_award_tax;

DELIMITER $$

CREATE PROCEDURE p3c_add_a2_award_tax()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'award_item'
                     AND COLUMN_NAME = 'tax_rate') THEN
        ALTER TABLE `award_item`
          ADD COLUMN `tax_rate` DECIMAL(5,2) NULL
            COMMENT '税率%（P3c-A2：报价继承/线下手填）' AFTER `conv_rate_snapshot`;
    END IF;
END$$

DELIMITER ;

CALL p3c_add_a2_award_tax();
DROP PROCEDURE IF EXISTS p3c_add_a2_award_tax;

-- ============================================================
-- A5 服务扣款明细子表（PRD L812 一条或多条扣款明细；BR-15 L1090）
-- service_deduction_item：粒度细化；service_assess.deduct_amount 保留为 Σ 汇总冗余
-- （结算 R-STL-02 取数口径不变）。应付非负：Σ 明细 ≤ 订单应付基数。
-- ============================================================

DROP PROCEDURE IF EXISTS p3c_add_a5_objects;

DELIMITER $$

CREATE PROCEDURE p3c_add_a5_objects()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'service_deduction_item') THEN
        CREATE TABLE `service_deduction_item` (
            id          BIGINT        NOT NULL PRIMARY KEY,
            assess_id   BIGINT        NOT NULL COMMENT '服务考核单ID',
            item_name   VARCHAR(100)  NOT NULL COMMENT '扣款项目（取服务考核指标）',
            amount      DECIMAL(18,2) NOT NULL COMMENT '扣款金额',
            reason      VARCHAR(200)  NULL COMMENT '扣款原因',
            created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_by BIGINT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted TINYINT NOT NULL DEFAULT 0
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务扣款明细（P3c-A5）';
    END IF;
END$$

DELIMITER ;

CALL p3c_add_a5_objects();
DROP PROCEDURE IF EXISTS p3c_add_a5_objects;

-- 验证（可选执行）：
-- SHOW TABLES LIKE 'contract_price_item';
-- SHOW COLUMNS FROM order_item LIKE 'contract_item_id';

SET FOREIGN_KEY_CHECKS = 1;
