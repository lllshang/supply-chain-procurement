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
    id         VARCHAR(32)  NOT NULL PRIMARY KEY,
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
    category      VARCHAR(50)  NULL,
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0审核中 1通过 2驳回',
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
    file_key   VARCHAR(200) NULL,
    expire_at  DATETIME    NULL,
    status     TINYINT    NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
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
    price_range VARCHAR(100) NULL,
    status     TINYINT     NOT NULL DEFAULT 0,
    created_by BIGINT      NULL,
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT      NULL,
    updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0
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

SET FOREIGN_KEY_CHECKS = 1;
