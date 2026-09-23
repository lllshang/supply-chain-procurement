-- ============================================================
-- P3 预算硬控 + 结算付款 + 成本管理 —— 独立迁移脚本（2 CREATE 新表 +
--   settlement/payment 补全量 + 3 组 ALTER，幂等）
-- 对应 docs/P3预算结算设计.md §1；与 db/schema.sql 末段同源，
-- 供存量库手工执行：mysql -u<user> -p <db> < scripts/sql/p3_schema.sql
-- ============================================================

SET NAMES utf8mb4;

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

-- 1.2.2 price_history（价格库：成交价沉淀；与 price_rule 事前限价两套分工，Q5）
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

-- 1.3.3 purchase_order（+1 阶段结算比例）
ALTER TABLE `purchase_order`
  ADD COLUMN `phase_plan` TEXT NULL COMMENT '阶段结算比例 JSON（[{"phase":1,"ratio":30}…]；null=一次性）' AFTER `order_type`;

SET FOREIGN_KEY_CHECKS = 1;
