-- ============================================================
-- P1 主数据 / 基础配置 —— 存量表加字段迁移（6 张表，20 列）
-- 对应 docs/P1主数据设计.md §1.3
-- 幂等：借助 information_schema 判断列是否存在，可重复执行。
-- 用法：mysql -u<user> -p <db> < scripts/sql/p1_alter.sql
-- ============================================================

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS p1_add_column;
DELIMITER $$
CREATE PROCEDURE p1_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_ddl VARCHAR(1024))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---- spu（+2） ----
CALL p1_add_column('spu', 'image_file_key',
    '`image_file_key` VARCHAR(255) NULL COMMENT ''主图 file_key（引用 file_meta.file_key）'' AFTER `status`');
CALL p1_add_column('spu', 'description',
    '`description` TEXT NULL COMMENT ''商品简介'' AFTER `image_file_key`');

-- ---- sku（+5） ----
CALL p1_add_column('sku', 'purchase_unit',
    '`purchase_unit` VARCHAR(32) NULL COMMENT ''采购单位（引用 unit.code）'' AFTER `spec`');
CALL p1_add_column('sku', 'reference_price',
    '`reference_price` DECIMAL(18,2) NULL COMMENT ''参考价（≥0）'' AFTER `purchase_unit`');
CALL p1_add_column('sku', 'standard_price',
    '`standard_price` DECIMAL(18,2) NULL COMMENT ''标准价（≥0）'' AFTER `reference_price`');
CALL p1_add_column('sku', 'valuation_type',
    '`valuation_type` TINYINT NULL COMMENT ''计价方式：0=计件 1=计重'' AFTER `standard_price`');
CALL p1_add_column('sku', 'image_file_key',
    '`image_file_key` VARCHAR(255) NULL COMMENT ''主图 file_key（引用 file_meta.file_key）'' AFTER `valuation_type`');

-- ---- supplier（+4） ----
CALL p1_add_column('supplier', 'supplier_category_id',
    '`supplier_category_id` BIGINT NULL DEFAULT NULL COMMENT ''供应商分类ID（引用 supplier_category）'' AFTER `category`');
CALL p1_add_column('supplier', 'coop_status',
    '`coop_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''合作状态：0=正常 1=停用 2=冻结'' AFTER `status`');
CALL p1_add_column('supplier', 'is_blacklist',
    '`is_blacklist` TINYINT NOT NULL DEFAULT 0 COMMENT ''黑名单：0=否 1=是'' AFTER `coop_status`');
CALL p1_add_column('supplier', 'source',
    '`source` TINYINT NOT NULL DEFAULT 0 COMMENT ''来源：0=平台录入 1=H5提交 2=导入'' AFTER `is_blacklist`');

-- ---- supplier_qual（+4） ----
CALL p1_add_column('supplier_qual', 'qual_name',
    '`qual_name` VARCHAR(128) NULL COMMENT ''资质名称'' AFTER `type`');
CALL p1_add_column('supplier_qual', 'reject_reason',
    '`reject_reason` VARCHAR(512) NULL COMMENT ''驳回原因（驳回时必填）'' AFTER `status`');
CALL p1_add_column('supplier_qual', 'reviewed_by',
    '`reviewed_by` BIGINT NULL COMMENT ''审核人ID'' AFTER `reject_reason`');
CALL p1_add_column('supplier_qual', 'reviewed_at',
    '`reviewed_at` DATETIME NULL COMMENT ''审核时间'' AFTER `reviewed_by`');

-- ---- supplier_sku（+4） ----
CALL p1_add_column('supplier_sku', 'supplier_sku_code',
    '`supplier_sku_code` VARCHAR(64) NULL COMMENT ''供应商货号'' AFTER `sku_id`');
CALL p1_add_column('supplier_sku', 'supply_price',
    '`supply_price` DECIMAL(18,2) NULL COMMENT ''供货价（≥0，以本字段为准）'' AFTER `price_range`');
CALL p1_add_column('supplier_sku', 'package_unit',
    '`package_unit` VARCHAR(32) NULL COMMENT ''包装单位（引用 unit.code）'' AFTER `supply_price`');
CALL p1_add_column('supplier_sku', 'bind_scope',
    '`bind_scope` TINYINT NOT NULL DEFAULT 0 COMMENT ''绑定范围：0=不限定 1=限定报价接单'' AFTER `package_unit`');

-- ---- budget_line（+1） ----
CALL p1_add_column('budget_line', 'project_id',
    '`project_id` BIGINT NULL DEFAULT NULL COMMENT ''预算项目ID（可选，引用 budget_project；NULL=未启用项目维度）'' AFTER `subject_id`');

DROP PROCEDURE IF EXISTS p1_add_column;

-- ============================================================
-- P2-4：supplier_sku 唯一约束（设计 §1.3.5）
-- uk_sup_sku(supplier_id, sku_id, deleted)：同一供应商+SKU 仅允许一条未删除记录。
-- 幂等：借助 information_schema.STATISTICS 判断索引是否已存在，可重复执行。
-- ============================================================
DROP PROCEDURE IF EXISTS p1_add_index;
DELIMITER $$
CREATE PROCEDURE p1_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (', p_cols, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL p1_add_index('supplier_sku', 'uk_sup_sku', '`supplier_id`, `sku_id`, `deleted`');

DROP PROCEDURE IF EXISTS p1_add_index;
