-- ============================================================
-- P3b 阶段一（D10 预付款结算 + D11 订单状态机收敛）—— 增量迁移脚本
-- 对应 docs/P3预算结算设计.md §0R(R4/R5)；与 scripts/sql/p3_schema.sql 末段同源，
-- 供存量库手工执行：mysql -u<user> -p <db> < scripts/sql/p3b_schema.sql
-- 注：p3_schema.sql 本批不修改，结算/付款基础表 CREATE 维持原样。
-- ============================================================

SET NAMES utf8mb4;

-- R4：settlement 补 2 列（预付款阶段 + 尾款自动抵扣回填）
-- payment_stage：1=预付 2=进度款 3=尾款（预付款结算=1，一次性/物料可空）
-- prepayment_deduction：尾款结算自动扣减时回填的已付预付款合计（非预付款结算恒 0）
ALTER TABLE `settlement`
  ADD COLUMN `payment_stage`       TINYINT       NULL COMMENT '付款阶段（R4：1=预付 2=进度款 3=尾款；预付款结算=1）' AFTER `is_final`,
  ADD COLUMN `prepayment_deduction` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '本单抵扣的预付款合计（R4：尾款结算自动扣减回填）' AFTER `payment_stage`;

SET FOREIGN_KEY_CHECKS = 1;
