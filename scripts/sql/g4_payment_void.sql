-- ============================================================
-- G4 付款冲销（复刻 B9 Settlement VOIDED 范式）
-- 本 SQL 为独立迁移脚本，供存量库手动执行 / 追溯；
-- 其内容已合并进 backend/src/main/resources/db/schema.sql（随应用启动自动幂等初始化）。
-- 新建库可直接用 schema.sql，无需单独执行本文件。
-- ============================================================
ALTER TABLE `payment`
  ADD COLUMN `voided_reason` VARCHAR(512) NULL COMMENT '作废/冲销原因' AFTER `remark`,
  ADD COLUMN `voided_by`     BIGINT       NULL COMMENT '作废/冲销操作人' AFTER `voided_reason`,
  ADD COLUMN `voided_at`     DATETIME     NULL COMMENT '作废/冲销时间' AFTER `voided_by`;
