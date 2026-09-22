-- ============================================================
-- P1 主数据 / 基础配置 —— 新建表 DDL（7 张）
-- 对应 docs/P1主数据设计.md §1.2
-- 字符集 utf8mb4 / 排序 utf8mb4_0900_ai_ci / InnoDB
-- 统一审计列：id, created_by, created_at, updated_by, updated_at, deleted(逻辑删除)
-- 幂等：CREATE TABLE IF NOT EXISTS
-- ============================================================

SET NAMES utf8mb4;

-- ---------------- catalog ----------------
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

-- ---------------- budget ----------------
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
