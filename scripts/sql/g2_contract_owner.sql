-- ============================================================
-- G2 合同经办信息（PRD 合同登记）：经办部门/经办人/签订日期/关联项目
-- 本 SQL 为独立迁移脚本，供存量库手动执行 / 追溯；
-- 其内容已合并进 backend/src/main/resources/db/schema.sql（随应用启动自动幂等初始化）。
-- 新建库可直接用 schema.sql，无需单独执行本文件。
-- ============================================================
ALTER TABLE `contract`
  ADD COLUMN `owner_dept` VARCHAR(200) NULL COMMENT '经办部门' AFTER `remark`,
  ADD COLUMN `owner`      VARCHAR(100) NULL COMMENT '经办人' AFTER `owner_dept`,
  ADD COLUMN `sign_date`  DATE         NULL COMMENT '签订日期' AFTER `owner`,
  ADD COLUMN `project_id` BIGINT       NULL COMMENT '关联项目（可选）' AFTER `sign_date`;
