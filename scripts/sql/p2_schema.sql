-- ============================================================
-- P2 采购主链路 —— 独立迁移脚本（7 CREATE + 9 ALTER，幂等）
-- 对应 docs/P2采购主链路设计.md §1.2 / §1.3；与 db/schema.sql 末段同源，
-- 供存量库手工执行：mysql -u<user> -p <db> < scripts/sql/p2_schema.sql
-- （schema.sql 版随应用启动自动执行，重复列报 1060/1061 由
--   spring.sql.init.continue-on-error=true 忽略）
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- P2 采购主链路：7 个新建实体（docs/P2采购主链路设计.md §1.2）
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `award_item` (
  `id`                 BIGINT        NOT NULL,
  `award_id`           BIGINT        NOT NULL                COMMENT '定标单ID（逻辑外键 award.id）',
  `sku_id`             BIGINT        NOT NULL                COMMENT 'SKU（逻辑外键 sku.id）',
  `supplier_id`        BIGINT        NOT NULL                COMMENT '中标供应商（按 SKU 可拆分多家）',
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

SET FOREIGN_KEY_CHECKS = 1;
