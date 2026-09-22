-- ============================================================
-- 供应链采购协同中台 —— 数据库 Schema（MySQL 8.0）
-- 字符集 utf8mb4 / 排序 utf8mb4_0900_ai_ci / InnoDB
-- 统一审计列：id, created_by, created_at, updated_by, updated_at, deleted(逻辑删除)
-- 状态字段统一 TINYINT，取值见各实体枚举
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------- 组织 / 权限（identity） ----------------
CREATE TABLE IF NOT EXISTS sys_dept (
    id            BIGINT       NOT NULL PRIMARY KEY,
    parent_id     BIGINT       NULL,
    dept_name     VARCHAR(100) NOT NULL,
    tree_path     VARCHAR(500) NULL,
    data_scope    TINYINT      NULL COMMENT '数据权限范围 0本人 1本部门 2本部门及子部门 3全部',
    sort          INT          NULL,
    leader        VARCHAR(50)  NULL,
    phone         VARCHAR(20)  NULL,
    status        TINYINT      NOT NULL DEFAULT 0,
    created_by    BIGINT       NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门';

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)  NULL,
    main_dept_id  BIGINT       NULL,
    email         VARCHAR(100) NULL,
    phone         VARCHAR(20)  NULL,
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0启用 1禁用',
    last_login_at DATETIME     NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT       NOT NULL PRIMARY KEY,
    role_code  VARCHAR(50)  NOT NULL,
    role_name  VARCHAR(50)  NOT NULL,
    remark     VARCHAR(255) NULL,
    status     TINYINT      NOT NULL DEFAULT 0,
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE IF NOT EXISTS sys_menu (
    id         BIGINT       NOT NULL PRIMARY KEY,
    parent_id  BIGINT       NULL,
    menu_name  VARCHAR(50)  NOT NULL,
    menu_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '0目录 1菜单 2按钮',
    path       VARCHAR(200) NULL,
    component  VARCHAR(200) NULL,
    icon       VARCHAR(50)  NULL,
    perms      VARCHAR(200) NULL COMMENT '权限标识 res:action',
    sort       INT          NULL,
    status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0显示 1隐藏',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id         BIGINT NOT NULL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    role_id    BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id         BIGINT NOT NULL PRIMARY KEY,
    role_id    BIGINT NOT NULL,
    menu_id    BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单';

CREATE TABLE IF NOT EXISTS sys_user_dept (
    id         BIGINT   NOT NULL PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    dept_id    BIGINT   NOT NULL,
    data_scope TINYINT  NULL,
    created_by BIGINT   NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT   NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT  NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-部门多归属';

-- ---------------- 商品 / 供应商（catalog） ----------------
CREATE TABLE IF NOT EXISTS spu (
    id         BIGINT       NOT NULL PRIMARY KEY,
    spu_code   VARCHAR(50)  NULL,
    name       VARCHAR(200) NOT NULL,
    category_id BIGINT      NULL,
    spec       VARCHAR(255) NULL,
    base_unit  VARCHAR(20)  NULL,
    status     TINYINT      NOT NULL DEFAULT 0,
    image_file_key VARCHAR(255) NULL COMMENT '主图 file_key（引用 file_meta.file_key）',
    description    TEXT         NULL COMMENT '商品简介',
    remark     VARCHAR(255) NULL,
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SPU';

CREATE TABLE IF NOT EXISTS sku (
    id         BIGINT       NOT NULL PRIMARY KEY,
    spu_id     BIGINT       NOT NULL,
    sku_code   VARCHAR(50)  NULL,
    barcode    VARCHAR(50)  NULL,
    base_unit  VARCHAR(20)  NULL,
    spec       VARCHAR(255) NULL,
    status     TINYINT      NOT NULL DEFAULT 0,
    purchase_unit   VARCHAR(32)   NULL COMMENT '采购单位（引用 unit.code）',
    reference_price DECIMAL(18,2) NULL COMMENT '参考价（≥0）',
    standard_price  DECIMAL(18,2) NULL COMMENT '标准价（≥0）',
    valuation_type  TINYINT       NULL COMMENT '计价方式：0计件 1计重',
    image_file_key  VARCHAR(255)  NULL COMMENT '主图 file_key（引用 file_meta.file_key）',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SKU';

CREATE TABLE IF NOT EXISTS unit_conversion (
    id             BIGINT        NOT NULL PRIMARY KEY,
    sku_id         BIGINT        NOT NULL,
    from_unit      VARCHAR(20)   NOT NULL,
    to_unit        VARCHAR(20)   NOT NULL,
    rate           DECIMAL(18,6) NOT NULL,
    effective_from DATETIME      NULL,
    version        INT           NULL,
    created_by     BIGINT        NULL,
    created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT        NULL,
    updated_at     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单位换算';

CREATE TABLE IF NOT EXISTS supplier (
    id            BIGINT       NOT NULL PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    credit_code   VARCHAR(50)  NULL COMMENT '统一社会信用代码',
    level         VARCHAR(20)  NULL,
    category      VARCHAR(50)  NULL COMMENT '遗留自由文本分类',
    supplier_category_id BIGINT NULL COMMENT '供应商分类ID（引用 supplier_category）',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '资质状态：0审核中 1通过 2驳回',
    coop_status   TINYINT      NOT NULL DEFAULT 0 COMMENT '合作状态：0正常 1停用 2冻结',
    is_blacklist  TINYINT      NOT NULL DEFAULT 0 COMMENT '黑名单：0否 1是',
    source        TINYINT      NOT NULL DEFAULT 0 COMMENT '来源：0平台录入 1H5提交 2导入',
    legal_person  VARCHAR(50)  NULL,
    business_scope VARCHAR(500) NULL,
    contact       VARCHAR(50)  NULL,
    phone         VARCHAR(20)  NULL,
    bank_name     VARCHAR(100) NULL,
    bank_account  VARCHAR(50)  NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商';

CREATE TABLE IF NOT EXISTS supplier_qual (
    id         BIGINT     NOT NULL PRIMARY KEY,
    supplier_id BIGINT     NOT NULL,
    type       VARCHAR(50) NULL,
    qual_name  VARCHAR(128) NULL COMMENT '资质名称',
    file_key   VARCHAR(200) NULL,
    expire_at  DATETIME    NULL,
    status     TINYINT    NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
    reject_reason VARCHAR(512) NULL COMMENT '驳回原因（驳回时必填）',
    reviewed_by   BIGINT       NULL COMMENT '审核人ID',
    reviewed_at   DATETIME     NULL COMMENT '审核时间',
    created_by BIGINT     NULL,
    created_at DATETIME   DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT     NULL,
    updated_at DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT    NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商资质';

CREATE TABLE IF NOT EXISTS supplier_sku (
    id         BIGINT      NOT NULL PRIMARY KEY,
    supplier_id BIGINT      NOT NULL,
    sku_id     BIGINT      NOT NULL,
    supplier_sku_code VARCHAR(64) NULL COMMENT '供应商货号',
    price_range VARCHAR(100) NULL COMMENT '遗留价格区间（保留兼容）',
    supply_price DECIMAL(18,2) NULL COMMENT '供货价（≥0，以本字段为准）',
    package_unit VARCHAR(32) NULL COMMENT '包装单位（引用 unit.code）',
    bind_scope TINYINT     NOT NULL DEFAULT 0 COMMENT '绑定范围：0不限定 1限定报价接单',
    status     TINYINT     NOT NULL DEFAULT 0,
    created_by BIGINT      NULL,
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT      NULL,
    updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sup_sku (supplier_id, sku_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商-商品绑定';

-- ---------------- 预算（budget） ----------------
CREATE TABLE IF NOT EXISTS budget_header (
    id         BIGINT        NOT NULL PRIMARY KEY,
    year       INT           NOT NULL,
    dept_id    BIGINT        NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    status     TINYINT       NOT NULL DEFAULT 0,
    remark     VARCHAR(255)  NULL,
    created_by BIGINT        NULL,
    created_at DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT        NULL,
    updated_at DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算头';

CREATE TABLE IF NOT EXISTS budget_line (
    id         BIGINT        NOT NULL PRIMARY KEY,
    header_id  BIGINT        NOT NULL,
    subject_id BIGINT        NOT NULL,
    project_id BIGINT        NULL COMMENT '预算项目ID（可选，引用 budget_project）',
    period     INT           NOT NULL DEFAULT 0 COMMENT '0=年度 1-12=月份',
    amount     DECIMAL(18,2) NOT NULL DEFAULT 0,
    used_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version    INT           NOT NULL DEFAULT 0,
    created_by BIGINT        NULL,
    created_at DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT        NULL,
    updated_at DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算明细';

-- ---------------- 采购（purchase） ----------------
CREATE TABLE IF NOT EXISTS purchase_apply (
    id           BIGINT       NOT NULL PRIMARY KEY,
    dept_id      BIGINT       NOT NULL,
    apply_no     VARCHAR(50)  NULL,
    title        VARCHAR(200) NULL,
    type         TINYINT      NOT NULL DEFAULT 0 COMMENT '0标准/项目 1日常/框架 2线下补录',
    status       TINYINT      NOT NULL DEFAULT 0,
    budget_status TINYINT     NOT NULL DEFAULT 0 COMMENT '0未校验 1通过 2超预算',
    applicant_id BIGINT       NULL,
    remark       VARCHAR(255) NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购申请';

CREATE TABLE IF NOT EXISTS purchase_apply_item (
    id           BIGINT        NOT NULL PRIMARY KEY,
    apply_id     BIGINT        NOT NULL,
    sku_id       BIGINT        NOT NULL,
    qty          DECIMAL(18,3) NULL,
    apply_qty    DECIMAL(18,3) NULL,
    ordered_qty  DECIMAL(18,3) NULL,
    remain_qty   DECIMAL(18,3) NULL,
    unit_snapshot VARCHAR(100) NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购申请明细';

CREATE TABLE IF NOT EXISTS inquiry (
    id           BIGINT       NOT NULL PRIMARY KEY,
    apply_id     BIGINT       NOT NULL,
    inquiry_no   VARCHAR(50)  NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    remark       VARCHAR(255) NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='询价单';

CREATE TABLE IF NOT EXISTS quotation (
    id           BIGINT        NOT NULL PRIMARY KEY,
    inquiry_id   BIGINT        NOT NULL,
    supplier_id  BIGINT        NOT NULL,
    sku_id       BIGINT        NOT NULL,
    price        DECIMAL(18,2) NULL,
    conv_snapshot VARCHAR(100) NULL,
    status       TINYINT       NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报价';

CREATE TABLE IF NOT EXISTS award (
    id           BIGINT        NOT NULL PRIMARY KEY,
    inquiry_id   BIGINT        NOT NULL,
    supplier_id  BIGINT        NOT NULL,
    amount       DECIMAL(18,2) NULL,
    status       TINYINT       NOT NULL DEFAULT 0,
    remark       VARCHAR(255)  NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定标';

-- ---------------- 合同 / 订单（contract / order） ----------------
CREATE TABLE IF NOT EXISTS contract (
    id               BIGINT        NOT NULL PRIMARY KEY,
    supplier_id      BIGINT        NOT NULL,
    no               VARCHAR(50)   NULL,
    title            VARCHAR(200)  NULL,
    amount           DECIMAL(18,2) NULL,
    valid_from       DATE          NULL,
    valid_to         DATE          NULL,
    status           TINYINT       NOT NULL DEFAULT 0,
    available_amount DECIMAL(18,2) NULL,
    remark           VARCHAR(255)  NULL,
    created_by       BIGINT        NULL,
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT        NULL,
    updated_at       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同';

CREATE TABLE IF NOT EXISTS purchase_order (
    id               BIGINT        NOT NULL PRIMARY KEY,
    contract_id      BIGINT        NOT NULL,
    apply_id         BIGINT        NULL,
    supplier_id      BIGINT        NOT NULL,
    order_no         VARCHAR(50)   NULL,
    status           TINYINT       NOT NULL DEFAULT 0,
    budget_occupied DECIMAL(18,2)  NULL,
    remark           VARCHAR(255)  NULL,
    created_by       BIGINT        NULL,
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT        NULL,
    updated_at       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单';

CREATE TABLE IF NOT EXISTS order_item (
    id                BIGINT        NOT NULL PRIMARY KEY,
    order_id          BIGINT        NOT NULL,
    sku_id            BIGINT        NOT NULL,
    qty_purchase      DECIMAL(18,3) NULL,
    qty_base          DECIMAL(18,3) NULL,
    conv_snapshot     VARCHAR(100)  NULL,
    price             DECIMAL(18,2) NULL,
    source_award_item BIGINT        NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细';

CREATE TABLE IF NOT EXISTS arrival (
    id               BIGINT        NOT NULL PRIMARY KEY,
    order_id         BIGINT        NOT NULL,
    actual_qty       DECIMAL(18,3) NULL,
    diff_qty         DECIMAL(18,3) NULL,
    voucher_file_key VARCHAR(200)   NULL,
    status           TINYINT       NOT NULL DEFAULT 0,
    remark           VARCHAR(255)  NULL,
    created_by       BIGINT        NULL,
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT        NULL,
    updated_at       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='到货验收';

-- ---------------- 结算 / 付款（settlement） ----------------
CREATE TABLE IF NOT EXISTS settlement (
    id         BIGINT        NOT NULL PRIMARY KEY,
    order_id   BIGINT        NULL,
    arrival_id BIGINT        NULL,
    type       TINYINT       NOT NULL DEFAULT 0 COMMENT '0物料 1服务',
    amount     DECIMAL(18,2)  NULL,
    status     TINYINT       NOT NULL DEFAULT 0,
    remark     VARCHAR(255)  NULL,
    created_by BIGINT        NULL,
    created_at DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT        NULL,
    updated_at DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算';

CREATE TABLE IF NOT EXISTS payment (
    id            BIGINT        NOT NULL PRIMARY KEY,
    settlement_id BIGINT        NOT NULL,
    pay_amount    DECIMAL(18,2) NULL,
    pay_method    VARCHAR(50)   NULL,
    voucher_file  VARCHAR(200)  NULL,
    status        TINYINT       NOT NULL DEFAULT 0,
    remark        VARCHAR(255)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款登记';

-- ---------------- 审批中心（approval） ----------------
CREATE TABLE IF NOT EXISTS approval_task (
    id           BIGINT       NOT NULL PRIMARY KEY,
    biz_type     VARCHAR(50)  NULL,
    biz_id       BIGINT       NULL,
    flow_key     VARCHAR(50)  NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    current_node VARCHAR(50)  NULL,
    remark       VARCHAR(255) NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批单据';

CREATE TABLE IF NOT EXISTS approval_node (
    id         BIGINT       NOT NULL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    node_def  VARCHAR(100) NULL,
    approver  BIGINT       NULL,
    action    VARCHAR(50)  NULL,
    comment   VARCHAR(255) NULL,
    status    TINYINT      NULL,
    created_by BIGINT      NULL,
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT      NULL,
    updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批节点';

CREATE TABLE IF NOT EXISTS approval_record (
    id         BIGINT       NOT NULL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    node_id    BIGINT       NULL,
    approver   BIGINT       NULL,
    action     VARCHAR(50)  NULL,
    comment    VARCHAR(255) NULL,
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录';

-- ---------------- 集成 / 文件（integration） ----------------
CREATE TABLE IF NOT EXISTS outbox_event (
    id            BIGINT       NOT NULL PRIMARY KEY,
    aggregate     VARCHAR(50)  NULL,
    aggregate_id  BIGINT       NULL,
    type          VARCHAR(50)  NULL,
    payload_json  LONGTEXT     NULL,
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0PENDING 1SENT 2RETRY 3FAILED',
    retry         INT          NOT NULL DEFAULT 0,
    created_by    BIGINT       NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Outbox事件';

CREATE TABLE IF NOT EXISTS sync_reconcile (
    id         BIGINT      NOT NULL PRIMARY KEY,
    event_id   VARCHAR(100) NULL,
    target     VARCHAR(50)  NULL,
    status     TINYINT     NULL,
    last_resp  LONGTEXT    NULL,
    created_by BIGINT      NULL,
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT      NULL,
    updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT    NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同步对账';

CREATE TABLE IF NOT EXISTS file_meta (
    id            BIGINT       NOT NULL PRIMARY KEY,
    file_key      VARCHAR(200) NOT NULL,
    original_name VARCHAR(200) NULL,
    size          BIGINT       NULL,
    sha256        VARCHAR(64)  NULL,
    biz_id        BIGINT       NULL,
    biz_type      VARCHAR(50)  NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据';

-- ---------------- P1 主数据 / 基础配置（catalog） ----------------
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

-- ---------------- P1 主数据 / 基础配置（budget） ----------------
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

-- ---------------- P1 存量表加字段迁移（幂等说明） ----------------
-- 说明：新建库时上述 CREATE TABLE 已含新列，以下 ALTER 会因"列已存在"报 1060，
-- 由 spring.sql.init.continue-on-error=true 忽略；存量库则由这些 ALTER 补齐新列。
-- 更严谨的幂等迁移脚本见 scripts/sql/p1_alter.sql。
ALTER TABLE `spu`
  ADD COLUMN `image_file_key` VARCHAR(255) NULL COMMENT '主图 file_key（引用 file_meta.file_key）' AFTER `status`,
  ADD COLUMN `description`    TEXT         NULL COMMENT '商品简介' AFTER `image_file_key`;

ALTER TABLE `sku`
  ADD COLUMN `purchase_unit`   VARCHAR(32)   NULL COMMENT '采购单位（引用 unit.code）'        AFTER `spec`,
  ADD COLUMN `reference_price` DECIMAL(18,2) NULL COMMENT '参考价（≥0）'                     AFTER `purchase_unit`,
  ADD COLUMN `standard_price`  DECIMAL(18,2) NULL COMMENT '标准价（≥0）'                     AFTER `reference_price`,
  ADD COLUMN `valuation_type`  TINYINT       NULL COMMENT '计价方式：0=计件 1=计重'           AFTER `standard_price`,
  ADD COLUMN `image_file_key`  VARCHAR(255)  NULL COMMENT '主图 file_key（引用 file_meta.file_key）' AFTER `valuation_type`;

ALTER TABLE `supplier`
  ADD COLUMN `supplier_category_id` BIGINT  NULL     DEFAULT NULL COMMENT '供应商分类ID（引用 supplier_category）' AFTER `category`,
  ADD COLUMN `coop_status`          TINYINT NOT NULL DEFAULT 0    COMMENT '合作状态：0=正常 1=停用 2=冻结'      AFTER `status`,
  ADD COLUMN `is_blacklist`         TINYINT NOT NULL DEFAULT 0    COMMENT '黑名单：0=否 1=是'                     AFTER `coop_status`,
  ADD COLUMN `source`               TINYINT NOT NULL DEFAULT 0    COMMENT '来源：0=平台录入 1=H5提交 2=导入'      AFTER `is_blacklist`;

ALTER TABLE `supplier_qual`
  ADD COLUMN `qual_name`     VARCHAR(128) NULL COMMENT '资质名称'              AFTER `type`,
  ADD COLUMN `reject_reason` VARCHAR(512) NULL COMMENT '驳回原因（驳回时必填）' AFTER `status`,
  ADD COLUMN `reviewed_by`   BIGINT       NULL COMMENT '审核人ID'              AFTER `reject_reason`,
  ADD COLUMN `reviewed_at`   DATETIME     NULL COMMENT '审核时间'              AFTER `reviewed_by`;

ALTER TABLE `supplier_sku`
  ADD COLUMN `supplier_sku_code` VARCHAR(64)   NULL     COMMENT '供应商货号'                    AFTER `sku_id`,
  ADD COLUMN `supply_price`      DECIMAL(18,2) NULL     COMMENT '供货价（≥0，以本字段为准）'     AFTER `price_range`,
  ADD COLUMN `package_unit`      VARCHAR(32)   NULL     COMMENT '包装单位（引用 unit.code）'     AFTER `supply_price`,
  ADD COLUMN `bind_scope`        TINYINT       NOT NULL DEFAULT 0 COMMENT '绑定范围：0=不限定 1=限定报价接单' AFTER `package_unit`;

ALTER TABLE `budget_line`
  ADD COLUMN `project_id` BIGINT NULL DEFAULT NULL COMMENT '预算项目ID（可选，引用 budget_project；NULL=未启用项目维度）' AFTER `subject_id`;

-- P2-4：supplier_sku 唯一约束（设计 §1.3.5）——同一 (supplier_id, sku_id) 仅允许一条未删除记录。
-- 幂等：新建库已在 CREATE TABLE 内联该索引，此处 ALTER 会因"索引已存在"报 1061，
-- 由 spring.sql.init.continue-on-error=true 忽略；存量库由此补齐。更严谨见 scripts/sql/p1_alter.sql。
ALTER TABLE `supplier_sku`
  ADD UNIQUE KEY `uk_sup_sku` (`supplier_id`, `sku_id`, `deleted`);

SET FOREIGN_KEY_CHECKS = 1;
