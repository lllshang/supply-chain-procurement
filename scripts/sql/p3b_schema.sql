-- ============================================================
-- P3b 阶段一（D10 预付款结算 + D11 订单状态机收敛）—— 增量迁移脚本（幂等）
-- 对应 docs/P3预算结算设计.md §0R(R4/R5)；供存量库手工执行：
--   mysql -u<user> -p <db> < scripts/sql/p3b_schema.sql
-- 注：新建库无需本脚本——backend/src/main/resources/db/schema.sql 已含两列
--     （自动初始化一条路打通，QA P2-2）。本脚本经 information_schema 判重，
--     可重复执行（已存在列则跳过，no-op）。
-- ============================================================

SET NAMES utf8mb4;

-- R4：settlement 补 2 列（预付款阶段 + 尾款自动抵扣回填）
-- payment_stage：1=预付款 2=进度款(阶段结算) 3=尾款（一次性/物料结算可空）
-- prepayment_deduction：尾款结算自动扣减时回填的预付款承诺合计（非尾款结算恒 0）
-- 幂等性：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程判重后追加。

DROP PROCEDURE IF EXISTS p3b_add_settlement_columns;

DELIMITER $$

CREATE PROCEDURE p3b_add_settlement_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'settlement'
                     AND COLUMN_NAME = 'payment_stage') THEN
        ALTER TABLE `settlement`
          ADD COLUMN `payment_stage` TINYINT NULL
            COMMENT '付款阶段（R4/P3b：1=预付 2=进度款 3=尾款）' AFTER `is_final`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'settlement'
                     AND COLUMN_NAME = 'prepayment_deduction') THEN
        ALTER TABLE `settlement`
          ADD COLUMN `prepayment_deduction` DECIMAL(18,2) NOT NULL DEFAULT 0
            COMMENT '本单抵扣的预付款合计（R4/P3b：尾款结算自动扣减回填）' AFTER `payment_stage`;
    END IF;
END$$

DELIMITER ;

CALL p3b_add_settlement_columns();
DROP PROCEDURE IF EXISTS p3b_add_settlement_columns;

-- ============================================================
-- PB-01（P3b 第二批，PM 裁决 b676143）：payment 状态重编号
-- 采口径 B：部分付款属结算维度（不落库枚举），付款状态收敛 UNPAID(0)/PAID(1)。
-- 旧库 2=已付款（P3b-T01 编号）→ 1；PARTIAL 从未落库（QA 实证零赋值点），无数据冲突。
-- 幂等性：重跑时无 status=2 行，no-op。
-- ============================================================
UPDATE `payment` SET `status` = 1 WHERE `status` = 2;

ALTER TABLE `payment`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0
    COMMENT '付款状态（PB-01：0=未付款 1=已付款；部分付款为结算维度派生，不落库）';

-- 验证（可选执行）：
-- SHOW COLUMNS FROM settlement LIKE 'payment_stage';
-- SHOW COLUMNS FROM settlement LIKE 'prepayment_deduction';
-- SELECT DISTINCT status FROM payment;  -- 应仅 0/1

SET FOREIGN_KEY_CHECKS = 1;
