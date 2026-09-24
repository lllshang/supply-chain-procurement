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
    project_id BIGINT        NULL COMMENT '预算项目ID（R1：统计冗余，不参与额度控制）',
    period     INT           NOT NULL DEFAULT 0 COMMENT '月份 1-12（R1：period=0 年度额度行已拆除，年度=12 个月度行聚合视图）',
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
    budget_subject_id BIGINT  NULL COMMENT '预算科目ID（QA2-01：额度控制键=部门×月份×科目，提交前必填；存量置空待补录）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定标（R2：一询价单一中标供应商）';
-- R2 唯一索引 uk_award_inquiry(inquiry_id, deleted) 由 scripts/sql/p3_r2_migration.sql 幂等补建。

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

-- ============================================================
-- P2 采购主链路：7 个新建实体（docs/P2采购主链路设计.md §1.2）
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `award_item` (
  `id`                 BIGINT        NOT NULL,
  `award_id`           BIGINT        NOT NULL                COMMENT '定标单ID（逻辑外键 award.id）',
  `sku_id`             BIGINT        NOT NULL                COMMENT 'SKU（逻辑外键 sku.id）',
  `supplier_id`        BIGINT        NOT NULL                COMMENT '中标供应商（R2：单中标，全明细行同供应商）',
  `price`              DECIMAL(18,2) NOT NULL                COMMENT '定标单价（基本单位口径）',
  `qty`                DECIMAL(18,4) NOT NULL                COMMENT '定标数量（采购单位）',
  `qty_in_base_unit`   DECIMAL(18,4) NOT NULL                COMMENT '基本单位数量 = qty × conv_rate_snapshot',
  `conv_rate_snapshot` DECIMAL(18,6) NOT NULL                COMMENT '换算快照（取 unit_conversion 当前生效版本）',
  `remark`             VARCHAR(255)  NULL,
  `created_by` BIGINT NULL, `created_at` DATETIME NULL,
  `updated_by` BIGINT NULL, `updated_at` DATETIME NULL,
  `deleted`    TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_award` (`award_id`),
  KEY `idx_supplier_sku` (`supplier_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定标明细（order_item.source_award_item 引用）';

CREATE TABLE IF NOT EXISTS `inquiry_supplier` (
  `id`                 BIGINT      NOT NULL,
  `inquiry_id`         BIGINT      NOT NULL                COMMENT '询价单ID',
  `supplier_id`        BIGINT      NOT NULL                COMMENT '供应商ID',
  `invited`            TINYINT     NOT NULL DEFAULT 1      COMMENT '是否邀请：0/1',
  `quoted`             TINYINT     NOT NULL DEFAULT 0      COMMENT '是否已报价：0/1',
  `admission_snapshot` TEXT        NULL                    COMMENT '准入快照 JSON：{qualified,reasons[],coopStatus,isBlacklist,qualValidity}（发布时落，审计用）',
  `remark`             VARCHAR(255) NULL,
  `created_by` BIGINT NULL, `created_at` DATETIME NULL,
  `updated_by` BIGINT NULL, `updated_at` DATETIME NULL,
  `deleted`    TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inquiry_supplier` (`inquiry_id`, `supplier_id`, `deleted`),
  KEY `idx_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='询价供应商范围（发布时逐家准入校验+落快照）';

CREATE TABLE IF NOT EXISTS `frequent_purchase` (
  `id`            BIGINT        NOT NULL,
  `dept_id`       BIGINT        NOT NULL                COMMENT '部门（数据权限隔离）',
  `sku_id`        BIGINT        NOT NULL                COMMENT 'SKU（仅有效 SKU 可维护）',
  `default_qty`   DECIMAL(18,4) NOT NULL DEFAULT 1      COMMENT '默认数量（采购单位）',
  `purchase_unit` VARCHAR(32)   NULL                    COMMENT '默认采购单位（unit.code）',
  `last_price`    DECIMAL(18,2) NULL                    COMMENT '最近价（最近订单/报价价，无则 standard_price）',
  `remark`        VARCHAR(255)  NULL,
  `status`        TINYINT       NOT NULL DEFAULT 0      COMMENT '0=正常 1=停用',
  `created_by` BIGINT NULL, `created_at` DATETIME NULL,
  `updated_by` BIGINT NULL, `updated_at` DATETIME NULL,
  `deleted`    TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_sku` (`dept_id`, `sku_id`, `deleted`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门常购清单（申请快速带入）';

CREATE TABLE IF NOT EXISTS `order_change` (
  `id`          BIGINT       NOT NULL,
  `order_id`    BIGINT       NOT NULL                COMMENT '订单ID',
  `change_type` TINYINT      NOT NULL                COMMENT '1=数量 2=价格 3=明细 4=取消',
  `before_json` TEXT         NULL                    COMMENT '变更前快照（明细/金额/数量）',
  `after_json`  TEXT         NULL                    COMMENT '变更后快照',
  `reason`      VARCHAR(512) NULL,
  `status`      TINYINT      NOT NULL DEFAULT 0      COMMENT '0=待审 1=生效 2=驳回',
  `operator`    BIGINT       NULL                    COMMENT '操作人',
  `created_by` BIGINT NULL, `created_at` DATETIME NULL,
  `updated_by` BIGINT NULL, `updated_at` DATETIME NULL,
  `deleted`    TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单变更留痕';

CREATE TABLE IF NOT EXISTS `arrival_item` (
  `id`             BIGINT        NOT NULL,
  `arrival_id`     BIGINT        NOT NULL                COMMENT '到货单ID',
  `order_item_id`  BIGINT        NOT NULL                COMMENT '订单明细ID（继承其换算快照与来源追溯）',
  `sku_id`         BIGINT        NOT NULL,
  `qty_expected`   DECIMAL(18,4) NOT NULL                COMMENT '应收数量（基本单位）',
  `qty_actual`     DECIMAL(18,4) NOT NULL                COMMENT '实收数量（基本单位，入库口径）',
  `qty_diff`       DECIMAL(18,4) NOT NULL                COMMENT '差异 = qty_expected − qty_actual',
  `diff_type`      TINYINT       NOT NULL DEFAULT 0      COMMENT '0=无差异 1=短缺 2=破损',
  `handle_type`    TINYINT       NOT NULL DEFAULT 0      COMMENT '0=接受 1=退货 2=补货',
  `qty_stored`     DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '已入库数量（基本单位，分次累加）',
  `handle_status`  TINYINT       NOT NULL DEFAULT 0      COMMENT '0=待处理 1=已完成',
  `remark`         VARCHAR(255)  NULL,
  `created_by` BIGINT NULL, `created_at` DATETIME NULL,
  `updated_by` BIGINT NULL, `updated_at` DATETIME NULL,
  `deleted`    TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_arrival` (`arrival_id`),
  KEY `idx_order_item` (`order_item_id`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='到货验收明细（差异退货补货 / 入库流水）';

CREATE TABLE IF NOT EXISTS `service_assess` (
  `id`            BIGINT        NOT NULL,
  `order_id`      BIGINT        NOT NULL                COMMENT '服务订单ID（order_type=1）',
  `assess_date`   DATE          NOT NULL                COMMENT '考核日期',
  `score`         DECIMAL(5,2)  NULL                    COMMENT '评分（口径见 Q7，一期自由录入）',
  `deduct_amount` DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '扣款金额（<!-- D8: P3 结算直接取数 -->）',
  `basis`         VARCHAR(512)  NULL                    COMMENT '考核依据',
  `file_keys`     TEXT          NULL                    COMMENT '附件 JSON 数组（file_meta.file_key）',
  `status`        TINYINT       NOT NULL DEFAULT 0      COMMENT '0=正常 1=作废',
  `remark`        VARCHAR(255)  NULL,
  `created_by` BIGINT NULL, `created_at` DATETIME NULL,
  `updated_by` BIGINT NULL, `updated_at` DATETIME NULL,
  `deleted`    TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务验收考核（扣款供 P3 结算取数）';

CREATE TABLE IF NOT EXISTS `fulfillment_adjust` (
  `id`          BIGINT       NOT NULL,
  `adjust_no`   VARCHAR(32)  NOT NULL                COMMENT '调整单号 LY-{yyyy}{MM}-{seq6}（全局唯一）',
  `biz_type`    TINYINT      NOT NULL                COMMENT '0=订单 1=到货',
  `order_id`    BIGINT       NOT NULL                COMMENT '订单ID',
  `arrival_id`  BIGINT       NULL                    COMMENT '到货单ID（可空）',
  `adjust_type` TINYINT      NOT NULL                COMMENT '0=差异 1=退货 2=补货 3=变更',
  `before_json` TEXT         NULL                    COMMENT '调整前口径快照（金额/数量）',
  `after_json`  TEXT         NULL                    COMMENT '调整后口径快照',
  `reason`      VARCHAR(512) NOT NULL,
  `status`      TINYINT      NOT NULL DEFAULT 0      COMMENT '0=草稿 1=审批中 2=生效 3=驳回',
  `file_keys`   TEXT         NULL                    COMMENT '附件 JSON 数组',
  `operator`    BIGINT       NULL,
  `created_by` BIGINT NULL, `created_at` DATETIME NULL,
  `updated_by` BIGINT NULL, `updated_at` DATETIME NULL,
  `deleted`    TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adjust_no` (`adjust_no`, `deleted`),
  KEY `idx_order` (`order_id`),
  KEY `idx_arrival` (`arrival_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约调整台账（变更留痕）';

-- ============================================================
-- P2 已有实体 ALTER（设计 §1.3，9 张表；只加列/索引，不改既有列语义）
-- 幂等：新建库 CREATE TABLE 未内联这些列，由本段 ALTER 一次性补齐（首建即成功）；
-- 存量库重复执行报 1060/1061，由 spring.sql.init.continue-on-error=true 忽略。
-- 独立迁移脚本见 scripts/sql/p2_schema.sql / p2_migrate_unit_snapshot.sql。
-- ============================================================

-- 1.3.1 purchase_apply（+1）
ALTER TABLE `purchase_apply`
  ADD COLUMN `expected_date` DATE NULL COMMENT '期望到货日期' AFTER `applicant_id`;

-- 1.3.2 purchase_apply_item（+7 新列；unit_snapshot(String) 弃用停写，回填见 scripts/sql/p2_migrate_unit_snapshot.sql）
ALTER TABLE `purchase_apply_item`
  ADD COLUMN `purchase_unit`        VARCHAR(32)   NULL COMMENT '采购单位（unit.code）' AFTER `sku_id`,
  ADD COLUMN `qty_in_purchase_unit` DECIMAL(18,4) NULL COMMENT '采购单位数量' AFTER `purchase_unit`,
  ADD COLUMN `qty_in_base_unit`     DECIMAL(18,4) NULL COMMENT '基本单位数量 = qty × 换算率' AFTER `qty_in_purchase_unit`,
  ADD COLUMN `conv_rate_snapshot`   DECIMAL(18,6) NULL COMMENT '换算快照（unit_conversion 当前生效版本）' AFTER `qty_in_base_unit`,
  ADD COLUMN `price_estimate`       DECIMAL(18,2) NULL COMMENT '预估单价' AFTER `conv_rate_snapshot`,
  ADD COLUMN `item_type`            TINYINT       NOT NULL DEFAULT 0 COMMENT '行级类型：0=物料 1=服务（转单拆分依据）' AFTER `price_estimate`,
  ADD COLUMN `remark`               VARCHAR(255)  NULL AFTER `item_type`,
  ADD COLUMN `version`              INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本（下单余量扣减并发兜底）' AFTER `remark`;

-- 1.3.3 inquiry（+2）
ALTER TABLE `inquiry`
  ADD COLUMN `deadline`        DATETIME NULL COMMENT '截标时间（到期或手动截标→CLOSED）' AFTER `inquiry_no`,
  ADD COLUMN `created_by_dept` BIGINT   NULL COMMENT '创建部门（数据权限）' AFTER `deadline`;

-- 1.3.4 quotation（+5）
ALTER TABLE `quotation`
  ADD COLUMN `batch_no`         VARCHAR(32)   NULL COMMENT '报价批次 BJ-{inquiry_no}-{seq2}' AFTER `inquiry_id`,
  ADD COLUMN `purchase_unit`    VARCHAR(32)   NULL COMMENT '报价单位（unit.code）' AFTER `sku_id`,
  ADD COLUMN `qty_in_base_unit` DECIMAL(18,4) NULL COMMENT '基本单位数量（导入单位换算所得）' AFTER `purchase_unit`,
  ADD COLUMN `file_key`         VARCHAR(255)  NULL COMMENT '原始报价附件（file_meta.file_key）' AFTER `qty_in_base_unit`,
  ADD COLUMN `invalid`          TINYINT       NOT NULL DEFAULT 0 COMMENT '0=有效 1=已失效（新批次导入后旧批次置 1，不物理删）' AFTER `file_key`,
  ADD KEY `idx_batch` (`inquiry_id`, `batch_no`);

-- 1.3.5 award（+2）
ALTER TABLE `award`
  ADD COLUMN `award_no` VARCHAR(32) NULL COMMENT '定标单号 DB-{yyyy}{MM}-{seq6}（全局唯一）' AFTER `inquiry_id`,
  ADD COLUMN `apply_id` BIGINT      NULL COMMENT '追溯申请（定标→申请上溯）' AFTER `award_no`,
  ADD UNIQUE KEY `uk_award_no` (`award_no`, `deleted`);

-- 1.3.6 contract（+6，含并发兜底 version；award_id nullable 兼容 <!-- D2 --> 线下补录，renewed_from_id 兼容 <!-- D1 --> 框架续签）
ALTER TABLE `contract`
  ADD COLUMN `award_id`         BIGINT       NULL COMMENT '来源定标（<!-- D2: P2b 线下补录可空 -->）' AFTER `supplier_id`,
  ADD COLUMN `contract_type`    TINYINT      NOT NULL DEFAULT 0 COMMENT '0=物料 1=服务 2=综合' AFTER `title`,
  ADD COLUMN `file_keys`        TEXT         NULL COMMENT '附件 JSON 数组（file_meta.file_key，多附件）' AFTER `valid_to`,
  ADD COLUMN `renewed_from_id`  BIGINT       NULL COMMENT '续签来源合同（<!-- D1: P2b 框架续签 -->）' AFTER `file_keys`,
  ADD COLUMN `terminate_reason` VARCHAR(512) NULL COMMENT '终止原因' AFTER `renewed_from_id`,
  ADD COLUMN `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本（额度扣减并发兜底，主防=行锁）' AFTER `terminate_reason`;

-- 1.3.7 purchase_order（+1）
ALTER TABLE `purchase_order`
  ADD COLUMN `order_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0=物料 1=服务（源自申请 item_type 拆单）' AFTER `order_no`;

-- 1.3.8 order_item（+3）
ALTER TABLE `order_item`
  ADD COLUMN `apply_item_id` BIGINT        NULL COMMENT '申请明细追溯（与 source_award_item 二选一或并存）' AFTER `source_award_item`,
  ADD COLUMN `plan_date`     DATE          NULL COMMENT '到货计划日期（P2-T09 逾期扫描依据）' AFTER `apply_item_id`,
  ADD COLUMN `planned_qty`   DECIMAL(18,4) NULL COMMENT '计划数量（基本单位，可分批多计划行）' AFTER `plan_date`;

-- 1.3.9 arrival（+1）
ALTER TABLE `arrival`
  ADD COLUMN `arrival_no` VARCHAR(32) NULL COMMENT '到货单号 DH-{order_no}-{seq2}（全局唯一）' AFTER `order_id`,
  ADD UNIQUE KEY `uk_arrival_no` (`arrival_no`, `deleted`);

-- ============================================================
-- P3 预算硬控 + 结算付款 + 成本管理（docs/P3预算结算设计.md §1；幂等）
-- ============================================================

-- 1.2.1 budget_occupy_log（预算占用/释放/核销流水，每日对账唯一依据）
CREATE TABLE IF NOT EXISTS budget_occupy_log (
    id             BIGINT        NOT NULL PRIMARY KEY,
    budget_line_id BIGINT        NOT NULL COMMENT '预算明细行ID（控制单元：部门×科目×月）',
    biz_type       TINYINT       NOT NULL COMMENT '1=APPLY 2=AWARD 3=ORDER 4=SETTLEMENT 5=ADJUST',
    biz_id         BIGINT        NOT NULL COMMENT '业务单据ID（申请/定标/订单/结算/调整）',
    action         TINYINT       NOT NULL COMMENT '0=占用 1=释放 2=核销 3=调整',
    amount         DECIMAL(18,2) NOT NULL COMMENT '本笔发生额（占用/调增为正，释放/调减为负；核销为正且 used_amount 不变）',
    balance_before DECIMAL(18,2) NOT NULL COMMENT '动作前 used_amount 快照',
    balance_after  DECIMAL(18,2) NOT NULL COMMENT '动作后 used_amount 快照（核销时前后相等）',
    operator       BIGINT        NULL COMMENT '操作人（系统动作为 NULL）',
    remark         VARCHAR(512)  NULL,
    created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT NOT NULL DEFAULT 0,
    KEY idx_pol_line (budget_line_id, created_at),
    KEY idx_pol_biz (biz_type, biz_id),
    KEY idx_pol_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算占用/释放/核销流水（每日对账依据）';

-- 1.2.2 price_history（价格库：成交价沉淀；与 price_rule 事前限价两套分工，Q5）CREATE TABLE IF NOT EXISTS price_history (
    id             BIGINT        NOT NULL PRIMARY KEY,
    sku_id         BIGINT        NOT NULL COMMENT 'SKU',
    supplier_id    BIGINT        NULL COMMENT '供应商（手工/品类级价可空）',
    price          DECIMAL(18,2) NOT NULL COMMENT '单价（基本单位口径）',
    source         TINYINT       NOT NULL COMMENT '0=报价 1=定标 2=订单 3=手工',
    biz_type       VARCHAR(32)   NULL COMMENT '来源单据类型（QUOTATION/AWARD/ORDER）',
    biz_id         BIGINT        NULL COMMENT '来源单据ID',
    effective_date DATE          NOT NULL COMMENT '生效日期（默认来源单据日期）',
    audit_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待审 1=通过 2=驳回',
    audit_by       BIGINT        NULL,
    audit_at       DATETIME      NULL,
    remark         VARCHAR(512)  NULL,
    created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT NOT NULL DEFAULT 0,
    KEY idx_ph_sku (sku_id, supplier_id, effective_date),
    KEY idx_ph_audit (audit_status),
    KEY idx_ph_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='价格库（成交价沉淀；P0 骨架建表缺 DDL，此处补齐全量）';

-- 1.3.1 settlement（P0 骨架表缺 DDL，此处补全量 CREATE + 新列经 ALTER 幂等追加）
CREATE TABLE IF NOT EXISTS settlement (
    id          BIGINT        NOT NULL PRIMARY KEY,
    order_id    BIGINT        NULL,
    arrival_id  BIGINT        NULL,
    type        TINYINT       NOT NULL DEFAULT 0 COMMENT '0=物料 1=服务',
    amount      DECIMAL(18,2) NULL,
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待结算 1=已结算 2=部分结算',
    remark      VARCHAR(255)  NULL,
    created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算单';

ALTER TABLE `settlement`
  ADD COLUMN `settle_no`        VARCHAR(32)   NULL COMMENT '结算单号 JS-{yyyy}{MM}-{seq6}（全局唯一）' AFTER `order_id`,
  ADD COLUMN `settled_qty_base` DECIMAL(18,4) NULL COMMENT '本次结算数量（基本单位累计口径）' AFTER `settle_no`,
  ADD COLUMN `deduct_amount`    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '服务考核扣款合计（物料=0；取数 service_assess）' AFTER `settled_qty_base`,
  ADD COLUMN `contract_id`      BIGINT        NULL COMMENT '追溯合同（nullable，展示比对用）' AFTER `deduct_amount`,
  ADD COLUMN `settle_mode`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0=一次性 1=阶段 2=尾款' AFTER `contract_id`,
  ADD COLUMN `phase_no`         INT           NULL COMMENT '阶段号（settle_mode=1 时必填）' AFTER `settle_mode`,
  ADD COLUMN `phase_ratio`      DECIMAL(5,2)  NULL COMMENT '阶段比例%（Σ=100 校验）' AFTER `phase_no`,
  ADD COLUMN `is_final`         TINYINT       NOT NULL DEFAULT 0 COMMENT '是否尾款结清：0/1' AFTER `phase_ratio`,
  ADD UNIQUE KEY `uk_settle_no` (`settle_no`, `deleted`);

-- 1.3.2 payment（P0 骨架表缺 DDL，同上）
CREATE TABLE IF NOT EXISTS payment (
    id            BIGINT        NOT NULL PRIMARY KEY,
    settlement_id BIGINT        NOT NULL,
    pay_amount    DECIMAL(18,2) NULL,
    pay_method    VARCHAR(50)   NULL,
    voucher_file  VARCHAR(200)  NULL,
    status        TINYINT       NOT NULL DEFAULT 0 COMMENT '0=未付款 1=已付款 2=已驳回',
    remark        VARCHAR(255)  NULL,
    created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款登记';

ALTER TABLE `payment`
  ADD COLUMN `pay_no`       VARCHAR(32) NULL COMMENT '付款单号 FK-{yyyy}{MM}-{seq6}（全局唯一）' AFTER `settlement_id`,
  ADD COLUMN `pay_date`     DATE        NULL COMMENT '付款日期（登记确认时填）' AFTER `pay_no`,
  ADD COLUMN `reviewed_by`  BIGINT      NULL COMMENT '财务审核人（PAYMENT 审批回调落）' AFTER `pay_date`,
  ADD COLUMN `reviewed_at`  DATETIME    NULL COMMENT '财务审核时间' AFTER `reviewed_by`,
  ADD COLUMN `confirmed_by` BIGINT      NULL COMMENT '线下付款登记确认人' AFTER `reviewed_at`,
  ADD COLUMN `confirmed_at` DATETIME    NULL COMMENT '登记确认时间' AFTER `confirmed_by`,
  ADD UNIQUE KEY `uk_pay_no` (`pay_no`, `deleted`);

-- 1.3.3 approval_task（+1：payload 持久化，P3 §4 spec.payloadJson 落库，回调侧可读）
ALTER TABLE `approval_task`
  ADD COLUMN `payload_json` TEXT NULL COMMENT '审批负载 JSON（bizType 升级审批参数，如 BUDGET 超支/调整、SETTLEMENT/PAYMENT 摘要）' AFTER `current_node`;

-- 1.3.4 purchase_order（+1 阶段结算比例）
ALTER TABLE `purchase_order`
  ADD COLUMN `phase_plan` TEXT NULL COMMENT '阶段结算比例 JSON（[{"phase":1,"ratio":30}…]；null=一次性）' AFTER `order_type`;

-- ---------------- P2b 扩展链路补丁（D1 日常/框架 · D2 线下补录 · D9 独立寻源） ----------------
-- 对齐 scripts/sql/p2b_migration.sql；存量库请执行迁移脚本（幂等），本段服务新建库自动初始化。
-- 新建库若 CREATE TABLE 已含新列，此处 ADD COLUMN 报 1060 可忽略（与上方 ALTER 段同模式）。

ALTER TABLE `inquiry`
  MODIFY COLUMN `apply_id` BIGINT NULL COMMENT '来源申请（D9 独立寻源为 NULL）',
  ADD COLUMN `source_type` VARCHAR(20) NOT NULL DEFAULT 'APPLY' COMMENT '询价来源：APPLY=申请转询价 / OFFLINE=独立寻源（BR-07）' AFTER `apply_id`,
  ADD COLUMN `source_reason` VARCHAR(500) NULL COMMENT '寻源原因（source_type=OFFLINE 必填）' AFTER `source_type`;

ALTER TABLE `award`
  MODIFY COLUMN `inquiry_id` BIGINT NULL COMMENT '来源询价（D9 线下直接登记为 NULL）',
  ADD COLUMN `dept_id` BIGINT NULL COMMENT '预算部门（线下登记必填：CP-11 锚点=award，提交即占预算）' AFTER `apply_id`,
  ADD COLUMN `subject_id` BIGINT NULL COMMENT '预算科目（线下登记必填：占用量化键之一）' AFTER `dept_id`;

ALTER TABLE `purchase_apply`
  ADD COLUMN `purpose` VARCHAR(500) NULL COMMENT '采购用途（PR-01）' AFTER `budget_subject_id`,
  ADD COLUMN `project_name` VARCHAR(200) NULL COMMENT '手工项目名（PR-01：无预算项目时的业务归属）' AFTER `purpose`;

ALTER TABLE `contract`
  ADD COLUMN `subject_id` BIGINT NULL COMMENT '预算科目（S8：统计冗余，预算锚点仍=申请/award）' AFTER `contract_type`;

-- ---------------- P3 遗漏合并修复：price_history 建表（原仅存于 scripts/sql/p3_schema.sql，QA 手工执行） ----------------
-- 缺陷记录：P3 交付时未合并进本文件，导致新库自动初始化后下单埋点（PriceHistoryServiceImpl）报表不存在。
CREATE TABLE IF NOT EXISTS price_history (
    id             BIGINT        NOT NULL PRIMARY KEY,
    sku_id         BIGINT        NOT NULL COMMENT 'SKU',
    supplier_id    BIGINT        NULL COMMENT '供应商（手工/品类级价可空）',
    price          DECIMAL(18,2) NOT NULL COMMENT '单价（基本单位口径）',
    source         TINYINT       NOT NULL COMMENT '0=报价 1=定标 2=订单 3=手工',
    biz_type       VARCHAR(32)   NULL COMMENT '来源单据类型（QUOTATION/AWARD/ORDER）',
    biz_id         BIGINT        NULL COMMENT '来源单据ID',
    effective_date DATE          NOT NULL COMMENT '生效日期（默认来源单据日期）',
    audit_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待审 1=通过 2=驳回',
    audit_by       BIGINT        NULL,
    audit_at       DATETIME      NULL,
    remark         VARCHAR(512)  NULL,
    created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='价格历史（P3 §1.4 价格库）';

SET FOREIGN_KEY_CHECKS = 1;
