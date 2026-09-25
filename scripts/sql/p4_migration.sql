-- ============================================================
-- P4 迁移：审批中心真实接入（表驱动引擎 + 工作台 + P3c 延后 3 项 + D15）
-- 依据：docs/P4审批中心设计.md §1（DDL 级）+ §1.4（配置种子 = 拍板清单默认值）
-- 幂等：5 建表 IF NOT EXISTS；4 组 ALTER 报 1060（Duplicate column）可忽略；
--       种子全部 INSERT IGNORE / UPDATE 幂等；重复执行无副作用。
-- 在途任务口径：配置变更只影响新创建任务，在途任务按提交时节点快照走完，无需迁移。
-- 执行：docker exec -i dzgylxt-p1-mysql mysql -uroot -p<pwd> supply_chain < p4_migration.sql
-- ============================================================

SET NAMES utf8mb4;

-- ---------------- 1) 5 个新表 ----------------
CREATE TABLE IF NOT EXISTS approval_flow_def (
    id           BIGINT       NOT NULL PRIMARY KEY,
    flow_key     VARCHAR(50)  NOT NULL COMMENT '流程键（=biz_type，8 个）',
    biz_type     VARCHAR(50)  NOT NULL COMMENT '业务类型（与 flow_key 同值，显式冗余便于查询）',
    flow_version INT          NOT NULL DEFAULT 1 COMMENT '流程版本（配置变更 +1；任务创建时快照落 approval_task.flow_version）',
    flow_name    VARCHAR(100) NULL COMMENT '流程名称',
    enabled      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用后新任务拒绝创建，在途不受影响）',
    remark       VARCHAR(255) NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_flow_key (flow_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批流定义（表驱动硬约束）';

CREATE TABLE IF NOT EXISTS approval_node_def (
    id            BIGINT        NOT NULL PRIMARY KEY,
    flow_key      VARCHAR(50)   NOT NULL COMMENT '所属流程（引用 approval_flow_def.flow_key）',
    node_code     VARCHAR(50)   NOT NULL COMMENT '节点编码（任务内唯一，如 N1/N2）',
    node_name     VARCHAR(100)  NULL COMMENT '节点名称（工作台展示）',
    seq           INT           NOT NULL COMMENT '节点序（1 起，按 seq 升序流转）',
    approver_type VARCHAR(30)   NOT NULL COMMENT '审批人解析类型：ROLE / DEPT_HEAD_OF_APPLICANT / USER',
    approver_value VARCHAR(200) NULL COMMENT 'ROLE=角色编码；DEPT_HEAD_OF_APPLICANT=置 NULL；USER=用户名（兜底慎用）',
    amount_min    DECIMAL(18,2) NULL COMMENT '节点生效金额区间下界（含）；NULL=无下界',
    amount_max    DECIMAL(18,2) NULL COMMENT '节点生效金额区间上界（不含）；NULL=无上界；双 NULL=恒生效',
    sign_type     VARCHAR(10)   NOT NULL DEFAULT 'ANY' COMMENT 'ANY=或签（默认）/ ALL=会签（预留）',
    timeout_hours INT           NULL COMMENT '超时升级时限（预留位不实现，v1.x）',
    free_review   TINYINT       NOT NULL DEFAULT 0 COMMENT '免审规则位（0=必审；Q7 一律审批口径固化）',
    enabled       TINYINT       NOT NULL DEFAULT 1,
    remark        VARCHAR(255)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_flow_node (flow_key, node_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批节点定义';

CREATE TABLE IF NOT EXISTS user_notice (
    id         BIGINT       NOT NULL PRIMARY KEY,
    user_id    BIGINT       NOT NULL COMMENT '接收人（sys_user.id）',
    title      VARCHAR(200) NOT NULL COMMENT '通知标题',
    content    VARCHAR(1000) NULL COMMENT '正文（终态通知含意见摘要，截断 500 字）',
    biz_type   VARCHAR(50)  NULL COMMENT '关联业务类型（审批通知=bizType）',
    biz_id     BIGINT       NULL COMMENT '关联业务单据/任务 id（taskId）',
    channel    VARCHAR(20)  NOT NULL DEFAULT 'site' COMMENT '渠道：site=站内（sms/wecom/dingtalk 列 v1.x）',
    read_flag  TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读（红点角标数据源）',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    KEY idx_user_read (user_id, read_flag, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';

CREATE TABLE IF NOT EXISTS contract_type (
    id         BIGINT       NOT NULL PRIMARY KEY,
    type_code  VARCHAR(50)  NOT NULL COMMENT '类型编码',
    type_name  VARCHAR(100) NOT NULL COMMENT '类型名称',
    enabled    TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用后新建合同不可选，存量不受影响）',
    remark     VARCHAR(255) NULL,
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_type_code (type_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同类型字典（被引用不可删）';

CREATE TABLE IF NOT EXISTS contract_sku_whitelist (
    id         BIGINT       NOT NULL PRIMARY KEY,
    contract_id BIGINT      NOT NULL COMMENT '合同 id（无清单且无定标合同的兜底供货范围）',
    sku_id     BIGINT       NOT NULL COMMENT '允许下单的 SKU',
    remark     VARCHAR(255) NULL COMMENT '维护原因（审计）',
    created_by BIGINT       NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT       NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_contract_sku (contract_id, sku_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同 SKU 白名单（D15 方案 A 兜底，空=维持现网额度闸行为）';

-- ---------------- 2) 4 组 ALTER（列已存在报 1060 可忽略） ----------------
ALTER TABLE `approval_task`
  ADD COLUMN `applicant`     VARCHAR(64)    NULL COMMENT '申请人用户名快照（展示用，通知投递用 applicant_id）' AFTER `flow_key`,
  ADD COLUMN `applicant_id`  BIGINT         NULL COMMENT '申请人用户 id（通知/重提归属）' AFTER `applicant`,
  ADD COLUMN `amount`        DECIMAL(18,2)  NULL COMMENT '金额摘要（payloadJson.amount 提取冗余）' AFTER `applicant_id`,
  ADD COLUMN `flow_version`  INT            NOT NULL DEFAULT 1 COMMENT '提交时流程版本快照' AFTER `amount`,
  ADD COLUMN `version`       INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本（并发防重审）' AFTER `flow_version`;

ALTER TABLE `approval_node`
  ADD COLUMN `node_code`     VARCHAR(50)   NULL COMMENT '节点编码快照（=approval_node_def.node_code）' AFTER `task_id`,
  ADD COLUMN `seq`           INT           NULL COMMENT '节点序快照' AFTER `node_code`,
  ADD COLUMN `sign_type`     VARCHAR(10)   NULL COMMENT '签类型快照：ANY/ALL' AFTER `seq`,
  ADD COLUMN `version`       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本（节点行并发防护）' AFTER `sign_type`;

ALTER TABLE `approval_record`
  ADD COLUMN `approver_name` VARCHAR(64) NULL COMMENT '审批人姓名快照（留痕防用户改名）' AFTER `approver`;

ALTER TABLE `contract`
  ADD COLUMN `source_contract_id` BIGINT      NULL COMMENT '关联原合同（补充签订 SUPPLEMENT 指向原合同）' AFTER `renewed_from_id`,
  ADD COLUMN `relation_type`      VARCHAR(20) NULL COMMENT '关联类型：SUPPLEMENT=补充签订' AFTER `source_contract_id`,
  ADD COLUMN `type_id`            BIGINT      NULL COMMENT '合同类型字典引用（type_id 优先于存量 TINYINT contract_type）' AFTER `relation_type`;

-- ---------------- 3) 角色 / 流定义 / 节点定义 / 菜单 / 合同类型种子（幂等） ----------------
INSERT IGNORE INTO sys_role (id, role_code, role_name, remark, status, created_at, updated_at, deleted) VALUES
(2, 'DEPT_HEAD',       '部门负责人', '采购申请第 1 节点/日常超授权确认（拍板清单第 3 题）', 0, NOW(), NOW(), 0),
(3, 'PURCHASE_DEPT',   '采购部（招采）', '采购申请第 2 节点/资质审批', 0, NOW(), NOW(), 0),
(4, 'FINANCE',         '财务负责人', '超预算/调整审批（拍板 2-A）', 0, NOW(), NOW(), 0),
(5, 'PROCUREMENT_LEAD','采购负责人', '定标/合同/履约/结算审批（拍板 3 表）', 0, NOW(), NOW(), 0),
(6, 'LEADER',          '分管领导', '合同超阈值第 2 节点（待拍板默认值，P4 设计偏差①）', 0, NOW(), NOW(), 0);

-- admin（user_id=1）兼任全部审批角色：角色驱动候选人解析下保证 SUPER_ADMIN 全功能可用
INSERT IGNORE INTO sys_user_role (id, user_id, role_id, created_at, updated_at, deleted) VALUES
(2, 1, 2, NOW(), NOW(), 0),
(3, 1, 3, NOW(), NOW(), 0),
(4, 1, 4, NOW(), NOW(), 0),
(5, 1, 5, NOW(), NOW(), 0),
(6, 1, 6, NOW(), NOW(), 0);

INSERT IGNORE INTO approval_flow_def (id, flow_key, biz_type, flow_version, flow_name, enabled, remark, created_at, updated_at, deleted) VALUES
(1, 'PURCHASE_APPLY',      'PURCHASE_APPLY',      1, '采购申请审批流', 1, '两级：部门负责人→采购部（拍板 1-A）', NOW(), NOW(), 0),
(2, 'AWARD',               'AWARD',               1, '定标审批流',     1, '单级：采购负责人', NOW(), NOW(), 0),
(3, 'CONTRACT',            'CONTRACT',            1, '合同审批流',     1, '金额>50 万升两级（规格基线 §2.2，Q5 占位）', NOW(), NOW(), 0),
(4, 'FULFILLMENT_ADJUST',  'FULFILLMENT_ADJUST',  1, '履约调整审批流', 1, 'Q7：一律审批无免审', NOW(), NOW(), 0),
(5, 'BUDGET',              'BUDGET',              1, '超预算/调整审批流', 1, '拍板 2-A：财务负责人', NOW(), NOW(), 0),
(6, 'SETTLEMENT',          'SETTLEMENT',          1, '结算审批流',     1, 'Q9：采购负责人', NOW(), NOW(), 0),
(7, 'SUPPLIER_QUAL',       'SUPPLIER_QUAL',       1, '资质审核流',     1, '拍板 3 表·招采人员', NOW(), NOW(), 0),
(8, 'DAILY_AUTH',          'DAILY_AUTH',          1, '日常超授权确认流', 1, '拍板 3 表二选一默认部门负责人（偏差③）', NOW(), NOW(), 0);

INSERT IGNORE INTO approval_node_def (id, flow_key, node_code, node_name, seq, approver_type, approver_value, amount_min, amount_max, sign_type, free_review, enabled, created_at, updated_at, deleted) VALUES
(1,  'PURCHASE_APPLY',     'N1', '部门负责人审批', 1, 'DEPT_HEAD_OF_APPLICANT', NULL, NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(2,  'PURCHASE_APPLY',     'N2', '采购部审批',     2, 'ROLE', 'PURCHASE_DEPT', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(3,  'AWARD',              'N1', '定标审批',       1, 'ROLE', 'PROCUREMENT_LEAD', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(4,  'CONTRACT',           'N1', '合同审批',       1, 'ROLE', 'PROCUREMENT_LEAD', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(5,  'CONTRACT',           'N2', '合同升级审批',   2, 'ROLE', 'LEADER', 500000, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(6,  'FULFILLMENT_ADJUST', 'N1', '履约调整审批',   1, 'ROLE', 'PROCUREMENT_LEAD', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(7,  'BUDGET',             'N1', '超预算/调整审批', 1, 'ROLE', 'FINANCE', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(8,  'SETTLEMENT',         'N1', '结算审批',       1, 'ROLE', 'PROCUREMENT_LEAD', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(9,  'SUPPLIER_QUAL',      'N1', '资质审核',       1, 'ROLE', 'PURCHASE_DEPT', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0),
(10, 'DAILY_AUTH',         'N1', '超授权确认',     1, 'ROLE', 'DEPT_HEAD', NULL, NULL, 'ANY', 0, 1, NOW(), NOW(), 0);

-- 菜单 1001 语义升级（幂等 UPDATE；perms 追加 approval:todo/approve，保留 approval:read 兼容一版防存量角色授权丢失语义）
UPDATE sys_menu SET menu_name='待办审批', path='/approval/todo', component='approval/todo/index',
  perms='approval:todo,approval:approve,approval:read' WHERE id=1001 AND deleted=0;

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort, status, created_at, updated_at, deleted) VALUES
(1002, 10, '已办审批', 2, '/approval/done',   'approval/done/index',   'finished', 'approval:done',   2, 0, NOW(), NOW(), 0),
(1003, 10, '流程配置', 2, '/approval/config', 'approval/config/index', 'setting',  'approval:config', 3, 0, NOW(), NOW(), 0);

-- 超管授权新菜单（1001 既有授权沿用，不得锁死 SUPER_ADMIN；admin 全功能冒烟必测）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, created_at, updated_at, deleted) VALUES
(50, 1, 1002, NOW(), NOW(), 0),
(51, 1, 1003, NOW(), NOW(), 0);

INSERT IGNORE INTO contract_type (id, type_code, type_name, enabled, remark, created_at, updated_at, deleted) VALUES
(1, 'MATERIAL', '物料', 1, '存量 contract_type=0 映射', NOW(), NOW(), 0),
(2, 'SERVICE',  '服务', 1, '存量 contract_type=1 映射', NOW(), NOW(), 0),
(3, 'MIXED',    '综合', 1, '存量 contract_type=2 映射', NOW(), NOW(), 0);

-- ---------------- 4) contract 存量 type_id 回填（幂等：仅 NULL 行） ----------------
UPDATE contract SET type_id = 1 WHERE contract_type = 0 AND type_id IS NULL AND deleted = 0;
UPDATE contract SET type_id = 2 WHERE contract_type = 1 AND type_id IS NULL AND deleted = 0;
UPDATE contract SET type_id = 3 WHERE contract_type = 2 AND type_id IS NULL AND deleted = 0;

-- ---------------- 5) 人工核对段 ----------------
-- SELECT flow_key, flow_version, enabled FROM approval_flow_def WHERE deleted = 0;            -- 应 8 条
-- SELECT flow_key, node_code, seq, approver_type, approver_value FROM approval_node_def
--   WHERE deleted = 0 ORDER BY flow_key, seq;                                                 -- 应 10 条
-- SELECT id, menu_name, path, perms FROM sys_menu WHERE id IN (1001, 1002, 1003) AND deleted = 0; -- 应 3 条
-- SELECT type_code, type_name FROM contract_type WHERE deleted = 0;                           -- 应 3 条
-- SELECT id, role_code FROM sys_role WHERE id IN (2,3,4,5,6) AND deleted = 0;                 -- 应 5 条
