-- ============================================================
-- P1 主数据 / 基础配置 —— DDL 汇总（新建 CREATE TABLE + 存量 ALTER TABLE）
-- 对应 docs/P1主数据设计.md §1.2 / §1.3
-- 本文件为可读汇总；实际执行请分别使用 p1_schema.sql（幂等建表）与
-- p1_alter.sql（幂等加列）。应用自动初始化脚本仍为
-- backend/src/main/resources/db/schema.sql。
-- ============================================================

SET NAMES utf8mb4;

-- ============ 一、新建表（CREATE TABLE，7 张） ============
-- 详见 scripts/sql/p1_schema.sql：product_category / unit / spec_option / price_rule /
-- supplier_category / budget_subject / budget_project
-- 为保证单片可执行，重复内联如下：

CREATE TABLE IF NOT EXISTS product_category (
    id         BIGINT       NOT NULL PRIMARY KEY,
    parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点ID，0=根',
    level      TINYINT      NOT NULL COMMENT '层级：1/2/3',
    code       VARCHAR(64)  NOT NULL COMMENT '品类编码（有效期内唯一）',
    name       VARCHAR(128) NOT NULL COMMENT '品类名称',
    tree_path  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '祖先路径，如 /1/3/7',
    status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=有效 1=无效',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_parent (parent_id),
    KEY idx_tree_path (tree_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品三级品类';

CREATE TABLE IF NOT EXISTS unit (
    id         BIGINT      NOT NULL PRIMARY KEY,
    code       VARCHAR(32) NOT NULL COMMENT '单位编码（有效期内唯一），如 PCS/BOX/KG',
    name       VARCHAR(64) NOT NULL COMMENT '单位名称，如 个/箱/千克',
    status     TINYINT     NOT NULL DEFAULT 0 COMMENT '0=有效 1=无效',
    created_by BIGINT      NULL,
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT      NULL,
    updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    INT         NOT NULL DEFAULT 0,
    UNIQUE KEY uk_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计量单位字典';

CREATE TABLE IF NOT EXISTS spec_option (
    id         BIGINT       NOT NULL PRIMARY KEY,
    spec_name  VARCHAR(64)  NOT NULL COMMENT '规格名，如 颜色/尺寸',
    spec_value VARCHAR(128) NOT NULL COMMENT '规格值，如 红/S',
    status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=有效 1=无效',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_name_value (spec_name, spec_value, deleted),
    KEY idx_spec_name (spec_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规格维度可选值';

CREATE TABLE IF NOT EXISTS price_rule (
    id         BIGINT        NOT NULL PRIMARY KEY,
    rule_type  TINYINT       NOT NULL COMMENT '1=最低限价 2=最高限价 3=区间 4=公式',
    ref_type   TINYINT       NOT NULL COMMENT '引用类型：1=商品(SPU) 2=品类',
    ref_id     BIGINT        NULL COMMENT '引用对象ID（spu.id 或 product_category.id）',
    min_price  DECIMAL(18,2) NULL COMMENT '最低价/区间下界',
    max_price  DECIMAL(18,2) NULL COMMENT '最高价/区间上界',
    expression VARCHAR(512)  NULL COMMENT '公式表达式（rule_type=4 时使用）',
    status     TINYINT       NOT NULL DEFAULT 0 COMMENT '0=有效 1=无效',
    created_by BIGINT        NULL,
    created_at DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT        NULL,
    updated_at DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    INT           NOT NULL DEFAULT 0,
    KEY idx_ref (ref_type, ref_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='价格规则';

CREATE TABLE IF NOT EXISTS supplier_category (
    id         BIGINT       NOT NULL PRIMARY KEY,
    parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点ID，0=根',
    level      TINYINT      NOT NULL COMMENT '层级：1/2/3',
    code       VARCHAR(64)  NOT NULL COMMENT '分类编码（有效期内唯一）',
    name       VARCHAR(128) NOT NULL COMMENT '分类名称',
    tree_path  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '祖先路径，如 /1/3/7',
    status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=有效 1=无效',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_parent (parent_id),
    KEY idx_tree_path (tree_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商三级分类';

CREATE TABLE IF NOT EXISTS budget_subject (
    id           BIGINT       NOT NULL PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL COMMENT '科目编码（有效期内唯一）',
    name         VARCHAR(128) NOT NULL COMMENT '科目名称',
    parent_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '父科目ID，0=根',
    subject_type TINYINT      NOT NULL COMMENT '1=支出 2=收入',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=有效 1=无效',
    created_by   BIGINT       NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算科目';

CREATE TABLE IF NOT EXISTS budget_project (
    id         BIGINT       NOT NULL PRIMARY KEY,
    code       VARCHAR(64)  NOT NULL COMMENT '项目编码（有效期内唯一）',
    name       VARCHAR(128) NOT NULL COMMENT '项目名称',
    year       INT          NOT NULL COMMENT '所属年份，如 2026',
    status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=有效 1=无效',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算项目（可选维度）';

-- ============ 二、存量表加字段（ALTER TABLE，6 张表 21 列） ============
-- 幂等加列过程（information_schema 判存）
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

-- spu（+2）
CALL p1_add_column('spu', 'image_file_key',
    '`image_file_key` VARCHAR(255) NULL COMMENT ''主图 file_key'' AFTER `status`');
CALL p1_add_column('spu', 'description',
    '`description` TEXT NULL COMMENT ''商品简介'' AFTER `image_file_key`');
-- sku（+5）
CALL p1_add_column('sku', 'purchase_unit',
    '`purchase_unit` VARCHAR(32) NULL COMMENT ''采购单位（引用 unit.code）'' AFTER `spec`');
CALL p1_add_column('sku', 'reference_price',
    '`reference_price` DECIMAL(18,2) NULL COMMENT ''参考价（≥0）'' AFTER `purchase_unit`');
CALL p1_add_column('sku', 'standard_price',
    '`standard_price` DECIMAL(18,2) NULL COMMENT ''标准价（≥0）'' AFTER `reference_price`');
CALL p1_add_column('sku', 'valuation_type',
    '`valuation_type` TINYINT NULL COMMENT ''计价方式：0=计件 1=计重'' AFTER `standard_price`');
CALL p1_add_column('sku', 'image_file_key',
    '`image_file_key` VARCHAR(255) NULL COMMENT ''主图 file_key'' AFTER `valuation_type`');
-- supplier（+4）
CALL p1_add_column('supplier', 'supplier_category_id',
    '`supplier_category_id` BIGINT NULL DEFAULT NULL COMMENT ''供应商分类ID'' AFTER `category`');
CALL p1_add_column('supplier', 'coop_status',
    '`coop_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''合作状态：0=正常 1=停用 2=冻结'' AFTER `status`');
CALL p1_add_column('supplier', 'is_blacklist',
    '`is_blacklist` TINYINT NOT NULL DEFAULT 0 COMMENT ''黑名单：0=否 1=是'' AFTER `coop_status`');
CALL p1_add_column('supplier', 'source',
    '`source` TINYINT NOT NULL DEFAULT 0 COMMENT ''来源：0=平台录入 1=H5提交 2=导入'' AFTER `is_blacklist`');
-- supplier_qual（+4）
CALL p1_add_column('supplier_qual', 'qual_name',
    '`qual_name` VARCHAR(128) NULL COMMENT ''资质名称'' AFTER `type`');
CALL p1_add_column('supplier_qual', 'reject_reason',
    '`reject_reason` VARCHAR(512) NULL COMMENT ''驳回原因（驳回时必填）'' AFTER `status`');
CALL p1_add_column('supplier_qual', 'reviewed_by',
    '`reviewed_by` BIGINT NULL COMMENT ''审核人ID'' AFTER `reject_reason`');
CALL p1_add_column('supplier_qual', 'reviewed_at',
    '`reviewed_at` DATETIME NULL COMMENT ''审核时间'' AFTER `reviewed_by`');
-- supplier_sku（+4）
CALL p1_add_column('supplier_sku', 'supplier_sku_code',
    '`supplier_sku_code` VARCHAR(64) NULL COMMENT ''供应商货号'' AFTER `sku_id`');
CALL p1_add_column('supplier_sku', 'supply_price',
    '`supply_price` DECIMAL(18,2) NULL COMMENT ''供货价（≥0）'' AFTER `price_range`');
CALL p1_add_column('supplier_sku', 'package_unit',
    '`package_unit` VARCHAR(32) NULL COMMENT ''包装单位（引用 unit.code）'' AFTER `supply_price`');
CALL p1_add_column('supplier_sku', 'bind_scope',
    '`bind_scope` TINYINT NOT NULL DEFAULT 0 COMMENT ''绑定范围：0=不限定 1=限定报价接单'' AFTER `package_unit`');
-- budget_line（+1）
CALL p1_add_column('budget_line', 'project_id',
    '`project_id` BIGINT NULL DEFAULT NULL COMMENT ''预算项目ID（可选，引用 budget_project）'' AFTER `subject_id`');

DROP PROCEDURE IF EXISTS p1_add_column;

-- ============ 三、存量表加唯一索引（ALTER TABLE，P2-4） ============
-- supplier_sku 唯一约束（设计 §1.3.5）：uk_sup_sku(supplier_id, sku_id, deleted)
-- 幂等加索引过程（information_schema.STATISTICS 判存）
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
