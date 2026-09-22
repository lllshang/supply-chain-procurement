-- ============================================================
-- P2 采购主链路 —— 存量数据迁移：purchase_apply_item.unit_snapshot 回填
-- 对应 docs/P2采购主链路设计.md §1.3.2：
--   unit_snapshot(String) 弃用停写；存量行一次性回填到结构化三字段
--   (qty_in_purchase_unit / qty_in_base_unit / conv_rate_snapshot) 后仅作历史展示。
-- 口径：qty_in_purchase_unit = qty；conv_rate_snapshot = 1（存量无换算依据，按 1:1 兜底）；
--       qty_in_base_unit = qty × 1 = qty。
-- 幂等：仅回填三字段全为 NULL 的行，可重复执行。
-- 用法：mysql -u<user> -p <db> < scripts/sql/p2_migrate_unit_snapshot.sql
-- ============================================================

SET NAMES utf8mb4;

-- P1 全程未写入 unit_snapshot（字段从未被业务代码赋值、种子无数据），
-- 本脚本为守卫式实现：若后续发现存量行带 unit_snapshot 语义，先修正下方映射口径再执行。
UPDATE purchase_apply_item
SET qty_in_purchase_unit = COALESCE(qty, apply_qty),
    qty_in_base_unit     = COALESCE(qty, apply_qty),
    conv_rate_snapshot   = 1
WHERE deleted = 0
  AND qty_in_purchase_unit IS NULL
  AND qty_in_base_unit IS NULL
  AND conv_rate_snapshot IS NULL;

-- 回填完成后 unit_snapshot 仅作历史展示，应用侧已停写（P2 起新明细一律落三字段）。
