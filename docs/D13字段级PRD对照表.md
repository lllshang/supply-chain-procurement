# D13 字段级 ↔ PRD 对照表（Batch 3 交付物）

> 目的：将数据库 Schema（`backend/src/main/resources/db/schema.sql`）中的每一个业务字段，映射到《供应链采购协同中台 PRD v1.1》（`docs/参考-PRD_v1.1_原文.md`）的对应条款，形成**上线前契约 / 验收基线**。
>
> 基准文件：
> - `schema.sql`（权威表/列清单，含 P0–P4 历次 CREATE/ALTER，约 52 张表）
> - `参考-PRD_v1.1_原文.md`（1532 行，章节号如 §6.5 / BR-04 / L644）
> - `口径与PRD一致性核对报告.md`（字段级一致性专项审计，结论 R1–R9 / S1–S12）
> - `P3c字段补齐规格设计.md`（A1–A6 字段补齐项，对应 S4/S8/S9/S10/S11/S12）
>
> 读法：`PRD 出处` 列若为 PRD 章节号，表示该字段可在该条款找到依据；若为「内部管理 / 审计列 / 审计/状态机 / 待确认」之一，表示该字段非 PRD 业务字段（说明见文末汇总）。
>
> 已剔除的纯 RBAC/身份基础设施表（非 PRD 业务字段，标准框架表）：`sys_dept`、`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`、`sys_user_dept`。本文件仅覆盖业务域实体。

---

## 模块一：商品主数据（Catalog）

### 1.1 product_category（商品三级品类）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键（雪花 ID） |
| parent_id | BIGINT | §6.2.2 PM-03 三级品类 | 父节点 ID，0=根 |
| level | TINYINT | §6.2.2 PM-03 | 层级 1/2/3 |
| code | VARCHAR(64) | §6.2.2 PM-03 | 品类编码（有效期内唯一） |
| name | VARCHAR(128) | §6.2.2 PM-03 | 品类名称 |
| tree_path | VARCHAR(512) | §6.2.2 PM-03 | 祖先路径，树形展示 |
| status | TINYINT | 审计/状态机（§6.2.2 PM-09 启用/停用） | 0=有效 1=无效 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 1.2 unit（计量单位字典）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| code | VARCHAR(32) | §6.2.2 PM-06 多单位 | 单位编码（如 PCS/BOX/KG） |
| name | VARCHAR(64) | §6.2.2 PM-06 | 单位名称（如 个/箱/千克） |
| status | TINYINT | 审计/状态机（§6.2.2 PM-09） | 0=有效 1=无效 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | INT | 内部管理 | 逻辑删除 |

### 1.3 spec_option（规格维度可选值）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| spec_name | VARCHAR(64) | §6.2.2 PM-02 多规格产品 | 规格名（如 颜色/尺寸） |
| spec_value | VARCHAR(128) | §6.2.2 PM-02 | 规格值（如 红/S） |
| status | TINYINT | 审计/状态机（§6.2.2 PM-09） | 0=有效 1=无效 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | INT | 内部管理 | 逻辑删除 |

### 1.4 spu（商品 SPU）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| spu_code | VARCHAR(50) | §6.2 / §10.1 数据模型 | SPU 编码（导入模板含产品编码） |
| name | VARCHAR(200) | §6.2.1 数据模型（SPU 共享名称） | 商品名称 |
| category_id | BIGINT | §6.2.2 PM-03 | 关联三级品类 |
| spec | VARCHAR(255) | §6.2.2 PM-02 | 规格描述 |
| base_unit | VARCHAR(20) | §6.2.1 / PM-06 | 基本单位 |
| status | TINYINT | 审计/状态机（§6.2.2 PM-09 启用/停用） | 0=启用 1=停用 |
| image_file_key | VARCHAR(255) | §6.2.1（图片） | 主图 file_key，引用 file_meta |
| description | TEXT | §6.2.1（简介） | 商品简介 |
| remark | VARCHAR(255) | 待确认 | 业务备注，PRD 未单列定义 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

> 注：PRD §6.2.1 明示「SPU 共享…采购项类型、计量方式、基本单位、采购单位和税率」，当前 `spu` 表无 `item_type`/`valuation_type`/`purchase_unit`/`tax_rate` 列（落在 `sku` 或 `purchase_apply_item`）。详见文末风险提示。

### 1.5 sku（商品 SKU）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| spu_id | BIGINT | §6.2.1（SKU 归属 SPU） | 所属 SPU |
| sku_code | VARCHAR(50) | §6.2.3（SKU 编码必须唯一，跨系统稳定键） | SKU 编码 |
| barcode | VARCHAR(50) | §6.2.2 PM-12/13、§6.2.3 | 条码（可空，填写后校验冲突） |
| base_unit | VARCHAR(20) | §6.2.1 / PM-06 | 基本单位 |
| spec | VARCHAR(255) | §6.2.2 PM-02、§6.2.3 | 规格 |
| status | TINYINT | 审计/状态机（§6.2.2 PM-09） | 0=启用 1=停用 |
| purchase_unit | VARCHAR(32) | §6.2.2 PM-06、PR-02 | 采购单位（引用 unit.code） |
| reference_price | DECIMAL(18,2) | §6.2.2 PM-08、§6.2.3 | 参考价（必填 ≥0） |
| standard_price | DECIMAL(18,2) | §6.2.2 PM-08、P3c A6/S12 | 标准价（选填，空则 fallback 参考价） |
| valuation_type | TINYINT | §6.2.2 PM-05、§6.8.2 | 计价方式 0=计件 1=计重 |
| image_file_key | VARCHAR(255) | §6.2.1（图片） | 主图 file_key |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 1.6 unit_conversion（单位换算）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| sku_id | BIGINT | §6.2.2 PM-06 | 所属 SKU（同一 SKU 默认包装换算） |
| from_unit | VARCHAR(20) | §6.2.2 PM-06、BR-02 | 源单位 |
| to_unit | VARCHAR(20) | §6.2.2 PM-06、BR-02 | 目标单位 |
| rate | DECIMAL(18,6) | §6.2.2 PM-06 | 换算率（如 1箱=24瓶） |
| effective_from | DATETIME | §6.2.2 PM-07、BR-21 | 生效起始（版本化/快照） |
| version | INT | 内部管理 | 版本（变更只影响后续单据，见 BR-21） |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 1.7 price_rule（价格规则）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| rule_type | TINYINT | 待确认（核对报告 S1：PRD 无对应） | 规则类型 1=最低限价 2=最高限价 3=区间 4=公式 |
| ref_type | TINYINT | 待确认（S1） | 引用类型 1=商品(SPU) 2=品类 |
| ref_id | BIGINT | 待确认（S1） | 引用对象 ID |
| min_price | DECIMAL(18,2) | 待确认（S1） | 最低/区间下界，PRD 价格治理=参考价/标准价+价格纠正，无限价规则 |
| max_price | DECIMAL(18,2) | 待确认（S1） | 最高/区间上界 |
| expression | VARCHAR(512) | 待确认（S1） | 公式表达式 |
| status | TINYINT | 审计/状态机 | 0=有效 1=无效 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | INT | 内部管理 | 逻辑删除 |

### 1.8 price_history（价格库）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| sku_id | BIGINT | §6.12.1 价格库 | 价格库保留 SKU |
| supplier_id | BIGINT | §6.12.1 价格库（手工/品类级价可空） | 供应商 |
| price | DECIMAL(18,2) | §6.12.1（含税价） | 单价（基本单位口径） |
| source | TINYINT | §6.12.1（来源 报价/定标/订单/手工） | 0=报价 1=定标 2=订单 3=手工 |
| biz_type | VARCHAR(32) | §6.12.1（来源单据类型） | QUOTATION/AWARD/ORDER |
| biz_id | BIGINT | §6.12.1（来源单据 ID） | 来源单据 |
| effective_date | DATE | §6.12.1（生效时间） | 生效日期 |
| audit_status | TINYINT | 待确认（§6.12.1 未明确价格库审核） | 0=待审 1=通过 2=驳回 |
| audit_by | BIGINT | 待确认 | 审核人 |
| audit_at | DATETIME | 待确认 | 审核时间 |
| remark | VARCHAR(512) | 待确认 | 备注 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

---

## 模块二：供应商与资质（Supplier）

### 2.1 supplier_category（供应商三级分类）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| parent_id | BIGINT | §6.3.1 业务分类（供应品类字典） | 父节点 ID，0=根 |
| level | TINYINT | §6.3.1 | 层级 1/2/3 |
| code | VARCHAR(64) | §6.3.1 | 分类编码（有效期内唯一） |
| name | VARCHAR(128) | §6.3.1 | 分类名称 |
| tree_path | VARCHAR(512) | §6.3.1 | 祖先路径 |
| status | TINYINT | 审计/状态机 | 0=有效 1=无效 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | INT | 内部管理 | 逻辑删除 |

### 2.2 supplier（供应商）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| name | VARCHAR(200) | §6.3.1 企业信息（企业名称） | 供应商名称 |
| credit_code | VARCHAR(50) | §6.3.1（统一社会信用代码） | 统一社会信用代码 |
| level | VARCHAR(20) | §6.3.1（A/B 等可配置等级） | 等级 |
| category | VARCHAR(50) | 待确认（遗留自由文本，已由 supplier_category 取代） | 遗留业务分类文本 |
| supplier_category_id | BIGINT | §6.3.1（业务分类由字典维护） | 分类 ID，引用 supplier_category |
| status | TINYINT | 审计/状态机（§6.3.1、§8 供应商状态机） | 注释标「资质状态」；语义见 §6.3 合作/审核状态 |
| coop_status | TINYINT | §6.3.1、§8、BR-03 | 合作状态 0=正常 1=停用 2=冻结 |
| is_blacklist | TINYINT | §6.3.1（黑名单独立于合作状态） | 0=否 1=是 |
| source | TINYINT | §6.3.4（资质提交来源 H5/后台）、§6.13.1 | 0=平台录入 1=H5提交 2=导入 |
| legal_person | VARCHAR(50) | §6.3.1（法人） | 法人 |
| business_scope | VARCHAR(500) | §6.3.1（经营范围） | 经营范围 |
| contact | VARCHAR(50) | §6.3.1（联系人） | 联系人 |
| phone | VARCHAR(20) | §6.3.1（手机号） | 联系电话 |
| bank_name | VARCHAR(100) | §6.3.1（开户行，权限脱敏） | 开户行 |
| bank_account | VARCHAR(50) | §6.3.1（银行账号，权限脱敏） | 银行账号 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 2.3 supplier_qual（供应商资质）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| supplier_id | BIGINT | §6.3（资质关联供应商） | 供应商 ID |
| type | VARCHAR(50) | §6.3.1（资质类别，如营业执照/银行证明） | 资质类型 |
| qual_name | VARCHAR(128) | §6.3.1（资质名称，新增列） | 资质名称 |
| file_key | VARCHAR(200) | §6.3.1（附件）、§6.8.3 | 附件 file_key，引用 file_meta |
| expire_at | DATETIME | §6.3.2 有效状态、§6.3.4（有效期） | 资质有效期 |
| status | TINYINT | 审计/状态机（§6.3.2 审核状态、§6.3.3 状态机） | 0=待审 1=通过 2=驳回 |
| reject_reason | VARCHAR(512) | §6.3.4、§6.16（驳回原因必填） | 驳回原因 |
| reviewed_by | BIGINT | §6.3.4（后台审核人） | 审核人 ID |
| reviewed_at | DATETIME | §6.3.4（审核时间） | 审核时间 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

> 注：PRD §6.3.2 将「审核状态」与「有效状态（正常/即将到期/已过期/已失效）」分开管理，且 §6.3.2 另列「变更类型 / 修改来源」。当前 `supplier_qual` 仅 `status` 覆盖审核状态，缺**有效状态**与**变更类型**列，亦无独立资质历史表（§10.1 `supplier_qualification_history`）。详见文末风险提示。

### 2.4 supplier_sku（供应商-商品绑定）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| supplier_id | BIGINT | §6.3.1（供应商产品绑定，代码保留入口隐藏） | 供应商 ID |
| sku_id | BIGINT | §6.3.1 | SKU ID |
| supplier_sku_code | VARCHAR(64) | §6.3.1、§10.1（供应商货号） | 供应商货号 |
| price_range | VARCHAR(100) | 待确认（遗留价格区间，保留兼容） | 历史价格区间文本 |
| supply_price | DECIMAL(18,2) | §6.3.1、§10.1（供货价，以本字段为准） | 供货价（≥0） |
| package_unit | VARCHAR(32) | §6.2.1（供应商箱规差异）、§6.15.1 | 包装单位（引用 unit.code） |
| bind_scope | TINYINT | 待确认（绑定范围 PRD 未细化） | 0=不限定 1=限定报价接单 |
| status | TINYINT | 审计/状态机 | 启用/停用 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

---

## 模块三：预算（Budget）

### 3.1 budget_subject（预算科目）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| code | VARCHAR(64) | §6.4.1（预算科目）、§10.2 | 科目编码（唯一） |
| name | VARCHAR(128) | §6.4.1 | 科目名称 |
| parent_id | BIGINT | §6.4.1（科目档案维护） | 父科目 ID，0=根 |
| subject_type | TINYINT | §6.4.1（科目，支出/收入） | 1=支出 2=收入 |
| status | TINYINT | 审计/状态机 | 0=有效 1=无效 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | INT | 内部管理 | 逻辑删除 |

### 3.2 budget_project（预算项目）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| code | VARCHAR(64) | §6.4.1（项目档案）、PR-01（手工项目名） | 项目编码（唯一） |
| name | VARCHAR(128) | §6.4.1（项目名称）、PR-01 | 项目名称 |
| year | INT | §6.4.1（项目所属年份）、§6.4.2（按年模板） | 所属年份 |
| status | TINYINT | 审计/状态机 | 0=有效 1=无效 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | INT | 内部管理 | 逻辑删除 |

### 3.3 budget_header（预算头）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| year | INT | §6.4.1、§6.4.2（按年模板） | 预算年度 |
| dept_id | BIGINT | §6.4.1、§6.4.3（部门额度） | 部门 ID |
| total_amount | DECIMAL(18,2) | §6.4.2（年度总额）、§6.4.3（年度合计） | 年度预算总额 |
| status | TINYINT | 审计/状态机（预算头状态，PRD 未细化枚举） | 预算头状态 |
| remark | VARCHAR(255) | 待确认（业务备注） | 备注 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 3.4 budget_line（预算明细）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| header_id | BIGINT | §6.4.2（年度→12 月台账） | 预算头 ID |
| subject_id | BIGINT | §6.4.1（预算科目） | 科目 ID |
| project_id | BIGINT | §6.4.1（项目仅统计，不参与额度，核对报告 R1） | 项目 ID（统计冗余） |
| period | INT | §6.4.2（月份 1–12，R1 已拆年度行） | 月份 |
| amount | DECIMAL(18,2) | §6.4.2、§6.4.3（月度预算） | 月度预算额 |
| used_amount | DECIMAL(18,2) | §6.4.3（已占用，可用=月度−已占用） | 已占用额 |
| version | INT | 内部管理（并发兜底） | 乐观锁版本 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 3.5 budget_occupy_log（预算占用流水）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| budget_line_id | BIGINT | §6.4.3（额度控制键 部门×科目×月） | 预算明细行 ID |
| biz_type | TINYINT | §6.4.3（各环节预算占用）、§5.1.1 | 1=APPLY 2=AWARD 3=ORDER 4=SETTLEMENT 5=ADJUST |
| biz_id | BIGINT | §6.4.3 | 业务单据 ID |
| action | TINYINT | §6.4.3（占用/释放/核销） | 0=占用 1=释放 2=核销 3=调整 |
| amount | DECIMAL(18,2) | §6.4.3 | 本笔发生额 |
| balance_before | DECIMAL(18,2) | §6.4.3（台账余额快照） | 动作前 used_amount |
| balance_after | DECIMAL(18,2) | §6.4.3 | 动作后 used_amount |
| operator | BIGINT | §6.4.3（财务发起调整） | 操作人 |
| remark | VARCHAR(512) | 待确认 | 备注 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

---

## 模块四：采购申请与寻源（Purchase / Sourcing）

### 4.1 purchase_apply（采购申请）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| dept_id | BIGINT | PR-01（填写部门）、§6.4.3 | 部门 ID |
| apply_no | VARCHAR(50) | §6.5、§10.2（request_no） | 申请编号 |
| title | VARCHAR(200) | PR-01（采购申请标题） | 标题 |
| type | TINYINT | PR-01、§0.2（日常/项目采购） | 0=标准/项目 1=日常/框架 2=线下补录 |
| status | TINYINT | 审计/状态机（§6.5.2、§8 状态机） | 草稿/待预算审批/待采购审批/已通过/已驳回 |
| budget_status | TINYINT | PR-05/PR-06（预算校验/审批） | 0=未校验 1=通过 2=超预算 |
| budget_subject_id | BIGINT | PR-01（预算科目）、QA2-01 | 预算科目 ID（额度控制键） |
| applicant_id | BIGINT | PR-01（申请人） | 申请人 ID |
| remark | VARCHAR(255) | 待确认（业务备注） | 备注 |
| expected_date | DATE | PR-01（需求时间 need_time） | 期望到货日期 |
| purpose | VARCHAR(500) | PR-01（用途）、P3c A3 | 采购用途 |
| project_name | VARCHAR(200) | PR-01（手工项目名）、P3c A3 | 手工项目名（非档案外键） |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 4.2 purchase_apply_item（采购申请明细）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| apply_id | BIGINT | §6.5（明细归属） | 申请 ID |
| sku_id | BIGINT | PR-02、BR-01 | SKU ID |
| qty | DECIMAL(18,3) | PR-02（填写数量） | 数量 |
| apply_qty | DECIMAL(18,3) | 待确认（与 qty 语义重叠，申请数量冗余） | 申请数量 |
| ordered_qty | DECIMAL(18,3) | §6.7.1（已下单数量） | 已下单数量 |
| remain_qty | DECIMAL(18,3) | §6.7.1（剩余可下单/可收） | 剩余数量 |
| unit_snapshot | VARCHAR(100) | 待确认（已弃用停写，由 purchase_unit 等替代，见 P2 §1.3.2） | 单位快照（弃用） |
| purchase_unit | VARCHAR(32) | PR-02（采购单位）、PM-06 | 采购单位 |
| qty_in_purchase_unit | DECIMAL(18,4) | PR-02 | 采购单位数量 |
| qty_in_base_unit | DECIMAL(18,4) | PR-02、PM-06、BR-02 | 基本单位数量 = qty×换算率 |
| conv_rate_snapshot | DECIMAL(18,6) | PM-07、BR-02（换算快照） | 换算率快照 |
| price_estimate | DECIMAL(18,2) | PR-02（预估单价） | 预估单价 |
| item_type | TINYINT | PR-03、BR-25（明细类型 0=物料 1=服务） | 行级采购项类型 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| version | INT | 内部管理（下单余量扣减并发兜底） | 乐观锁版本 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 4.3 inquiry（询价单）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| apply_id | BIGINT | §6.6.1（申请发起继承）、BR-07/D9 | 来源申请（独立寻源为 NULL） |
| inquiry_no | VARCHAR(50) | §6.6、§10.2（rfq_no） | 询价单号 |
| status | TINYINT | 审计/状态机（§6.6.5、§8 状态机） | 草稿/询价中/待比价/定标待审批/定标已驳回/已定标 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| deadline | DATETIME | RFQ-06（截止时间）、§6.6.2 | 截标时间 |
| created_by_dept | BIGINT | §6.6.1（部门数据权限） | 创建部门 |
| source_type | VARCHAR(20) | BR-07（无申请来源必填寻源原因）、§6.6.1 | APPLY=申请转询价 / OFFLINE=独立寻源 |
| source_reason | VARCHAR(500) | BR-07、§6.6.1 | 寻源原因（OFFLINE 必填） |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 4.4 quotation（报价）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| inquiry_id | BIGINT | §6.6（归属询价）、§6.6.4 | 询价单 ID |
| supplier_id | BIGINT | RFQ-03（供应商范围）、§6.6.2 | 供应商 ID |
| sku_id | BIGINT | RFQ-02/RFQ-09（报价产品明细） | SKU ID |
| price | DECIMAL(18,2) | §6.6.3（含税单价×数量）、§6.6.4 | 报价单价/金额 |
| conv_snapshot | VARCHAR(100) | PM-07、BR-02 | 换算快照 |
| status | TINYINT | 审计/状态机（§6.6.4 中标/未中标、RFQ-10 版本） | 报价状态 |
| batch_no | VARCHAR(32) | RFQ-10（报价版本） | 报价批次 BJ-{inquiry_no}-{seq} |
| purchase_unit | VARCHAR(32) | RFQ-02（报价单位）、PM-06 | 报价单位 |
| qty_in_base_unit | DECIMAL(18,4) | RFQ-02、PM-06 | 基本单位数量 |
| file_key | VARCHAR(255) | RFQ-08/RFQ-09（原始报价附件） | 报价附件 file_key |
| invalid | TINYINT | RFQ-10（新批次导入旧批次失效，不物理删） | 0=有效 1=失效 |
| tax_rate | DECIMAL(5,2) | RFQ-09、§6.6.3、P3c A2/S4（含税口径） | 税率% |
| freight | DECIMAL(18,2) | RFQ-09、§6.6.4（比价维度）、P3c A2 | 运费 |
| delivery_days | INT | RFQ-09、§6.6.3（承诺交期）、P3c A2 | 承诺交期天数 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 4.5 award（定标）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| inquiry_id | BIGINT | §6.6.4（来源询价）、BR-09、D9 | 来源询价（线下登记为 NULL） |
| supplier_id | BIGINT | §6.6.4（中标供应商）、L718 | 中标供应商 |
| amount | DECIMAL(18,2) | §6.6.4（定标金额）、L721 | 定标金额 |
| status | TINYINT | 审计/状态机（§6.6.5、§8） | 定标待审批/定标已驳回/已定标 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| award_no | VARCHAR(32) | §6.6.4（定标申请/单号） | 定标单号 DB-{yyyyMM}-{seq} |
| apply_id | BIGINT | §6.6.1（定标→申请上溯）、D2 | 追溯申请（线下补录可空） |
| dept_id | BIGINT | §6.6.1、§6.4.3（线下登记预算部门锚点） | 预算部门 |
| subject_id | BIGINT | §6.6.1、§6.4.3（线下登记预算科目） | 预算科目 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

---

## 模块五：合同与订单（Contract / Order）

### 5.1 contract（合同）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| supplier_id | BIGINT | §6.9.1、BR-26 | 供应商 |
| no | VARCHAR(50) | §6.9.1（合同编号）、§10.2（contract_no） | 合同号 |
| title | VARCHAR(200) | §6.9.1（合同名称） | 名称 |
| amount | DECIMAL(18,2) | §6.9.1（金额）、§6.7.1（剩余额度） | 合同金额 |
| valid_from | DATE | §6.9.1（开始日期）、§6.9.2 | 开始日期 |
| valid_to | DATE | §6.9.1（结束日期） | 结束日期 |
| status | TINYINT | 审计/状态机（§8 合同状态机） | 草稿/待审批/已驳回/履行中/已到期/已终止 |
| available_amount | DECIMAL(18,2) | §6.9.1（剩余额度）、BR-26 | 合同可用额度 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| award_id | BIGINT | §6.9.1（继承定标）、D2 | 来源定标（线下补录可空） |
| contract_type | TINYINT | §6.9.1（合同类型） | 0=物料 1=服务 2=综合 |
| file_keys | TEXT | §6.9.1（附件） | 附件 JSON 数组 |
| renewed_from_id | BIGINT | §6.9.1（续签关联原合同）、D1 | 续签来源 |
| terminate_reason | VARCHAR(512) | §6.9.1（终止原因） | 终止原因 |
| version | INT | 内部管理（额度扣减并发兜底） | 乐观锁版本 |
| subject_id | BIGINT | §6.9.1（预算科目，S8 统计冗余） | 预算科目 ID |
| source_contract_id | BIGINT | §6.9.1（补充签订关联原合同） | 关联原合同 |
| relation_type | VARCHAR(20) | §6.9.1（补充签订） | SUPPLEMENT=补充签订 |
| type_id | BIGINT | §6.9.1（类型字典引用）、P4 §5.1 | 合同类型字典 ID |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

> 注：PRD §6.9.1 L839 明示合同应维护「经办部门、经办人、签订日期、（标的、项目）」。当前 `contract` 表无 `owner_dept`/`owner`/`sign_date`/`project_id` 列。详见文末风险提示。

### 5.2 contract_price_item（合同价格清单）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| contract_id | BIGINT | §6.9.1（价格清单）、BR-26、P3c A1 | 合同 ID |
| sku_id | BIGINT | §6.9.1（采购项）、BR-26 | SKU |
| unit_price | DECIMAL(18,2) | §6.9.1（价格清单单价）、BR-26（价格一致）、P3c A1 | 含税单价 |
| qty | DECIMAL(18,3) | §6.9.1（数量） | 数量 |
| source_type | TINYINT | §6.9.1（继承/手工）、P3c A1 | 1=定标继承 2=手工维护 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 5.3 contract_sku_whitelist（合同 SKU 白名单）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| contract_id | BIGINT | §6.9.1（合同供货范围兜底）、D15 方案 A | 合同 ID |
| sku_id | BIGINT | 同上（D15 无清单无定标合同兜底） | 允许下单 SKU |
| remark | VARCHAR(255) | 待确认 | 维护原因（审计） |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 5.4 contract_type（合同类型字典）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| type_code | VARCHAR(50) | §6.9.1（类型新增/编辑/停用）、P4 §5.1（PRD L840） | 类型编码 |
| type_name | VARCHAR(100) | §6.9.1（类型名称） | 类型名称 |
| enabled | TINYINT | §6.9.1（停用后新建不可选） | 1=启用 0=停用 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 5.5 purchase_order（采购订单）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| contract_id | BIGINT | §6.7.1（关联合同，必需前置）、BR-10/BR-26 | 合同 ID |
| apply_id | BIGINT | §6.7.1（可对应多订单；框架可不关联）、§0.2 | 来源申请（可空） |
| supplier_id | BIGINT | §6.7.1（供应商资格）、§6.7.2 | 供应商 ID |
| order_no | VARCHAR(50) | §6.7.2（订单号全局唯一）、§10.2 | 订单号 |
| status | TINYINT | 审计/状态机（§8 订单状态机） | 物料四态+服务三态 |
| budget_occupied | DECIMAL(18,2) | §6.7.1（保留预算占用） | 已占用预算 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| authorized_by | BIGINT | §6.7.1（日常采购授权控制，D14） | 授权人 ID |
| authorized_name | VARCHAR(50) | §6.7.1（授权留痕，D14） | 授权人姓名 |
| authorized_time | DATETIME | §6.7.1（D14） | 授权时间 |
| auth_over_limit | TINYINT | §6.7.1（超单笔授权额度升级，设计补充） | 0=否 1=是 |
| auto_authorized | TINYINT | D17 高频补货自动授权（设计补充） | 0=否 1=是 |
| source_type | VARCHAR(20) | §6.7.1（无申请来源需求留痕，D16） | APPLY/OFFLINE |
| source_reason | VARCHAR(500) | §6.7.1（无申请来源说明，D16） | 需求来源说明 |
| order_type | TINYINT | §6.7.2（订单类型）、PR-03/BR-25 | 0=物料 1=服务 |
| phase_plan | TEXT | §6.10.1（阶段结算比例） | 阶段比例 JSON |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 5.6 order_item（订单明细）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| order_id | BIGINT | §6.7（明细归属） | 订单 ID |
| sku_id | BIGINT | §6.7.2（产品明细） | SKU |
| qty_purchase | DECIMAL(18,3) | §6.7.2（数量 采购单位） | 采购单位数量 |
| qty_base | DECIMAL(18,3) | §6.7.2（基本数量） | 基本单位数量 |
| conv_snapshot | VARCHAR(100) | PM-07、BR-02（换算快照） | 换算快照 |
| price | DECIMAL(18,2) | §6.7.2（含税单价）、§6.7.1 | 单价 |
| source_award_item | BIGINT | §6.6.4（定标作为价格来源）、§6.7.1 | 来源定标明细 |
| apply_item_id | BIGINT | §6.5.1（来源追溯申请明细）、§6.7.1 | 申请明细 ID |
| plan_date | DATE | §6.7.2（计划交期/到货计划）、P2-T09 逾期扫描 | 到货计划日期 |
| planned_qty | DECIMAL(18,4) | §6.7.2（计划数量） | 计划数量（基本单位） |
| contract_item_id | BIGINT | BR-26（价格与合同清单一致）、P3c A1（第四重校验） | 命中合同价格清单行 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 5.7 arrival（到货验收）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| order_id | BIGINT | §6.8.1（从订单登记到货） | 订单 ID |
| actual_qty | DECIMAL(18,3) | §6.8.1/§6.8.2（实际到货数量） | 实际到货数量 |
| diff_qty | DECIMAL(18,3) | §6.8.2（差异 退货/补货） | 差异数量 |
| voucher_file_key | VARCHAR(200) | §6.8.3（验收凭证，至少一份）、§6.3.1 | 凭证 file_key |
| status | TINYINT | 审计/状态机（§8 到货验收状态机） | 待验收入库/部分验收入库/入库完成/拒收退货/部分履约/服务验收完成 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| arrival_no | VARCHAR(32) | §6.8（到货单号）、§10.2（receipt_no） | 到货单号 DH-{order_no}-{seq} |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

> 注：`arrival` 为单据级；逐行验收明细（`arrival_item`，含 `actual_weight`/`qualified_qty` 计重口径）属 P2/P4 扩展表，不在本 Batch 3 范围内，故未列入。PRD §6.8.2 计重「合格量≤实到重量」落地于 `arrival_item`，与本表字段级映射暂不涉及。

---

## 模块六：结算与付款（Settlement / Payment）

### 6.1 settlement（结算单）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| order_id | BIGINT | §6.10.1（来源订单）、§10.2 | 订单 ID |
| arrival_id | BIGINT | §6.10.1（关联验收明细）、§6.8.3 | 到货/验收 ID |
| type | TINYINT | §6.10.1（物料）/§6.10.2（服务） | 0=物料 1=服务 |
| amount | DECIMAL(18,2) | §6.10.1（结算金额）、§6.10.2（应付=结算−扣款） | 结算金额 |
| status | TINYINT | 审计/状态机（§6.10.3、§8 状态机） | 待财务审核/已审核/已驳回 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| settle_no | VARCHAR(32) | §6.10（结算单号）、§10.2（settlement_no） | 结算单号 JS-{yyyyMM}-{seq} |
| settled_qty_base | DECIMAL(18,4) | §6.10.1（结算数量不超合格量） | 本次结算数量（基本单位） |
| deduct_amount | DECIMAL(18,2) | §6.10.2（服务考核扣款）、BR-15 | 服务扣款合计 |
| contract_id | BIGINT | §6.10.1（合同追溯） | 合同 ID |
| settle_mode | TINYINT | §6.10.1（一次性/阶段/尾款） | 0=一次性 1=阶段 2=尾款 |
| phase_no | INT | §6.10.1（阶段号） | 阶段号 |
| phase_ratio | DECIMAL(5,2) | §6.10.1（阶段比例） | 阶段比例% |
| is_final | TINYINT | §6.10.1（尾款结清） | 是否尾款 |
| payment_stage | TINYINT | §6.10.1（预付款/进度款/尾款）、BR-16/BR-18、PAY-01 | 付款阶段 1=预付 2=进度 3=尾款 |
| prepayment_deduction | DECIMAL(18,2) | §6.10.1（预付款抵扣）、R4、PAY-07 | 本单抵扣预付款合计 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 6.2 payment（付款登记）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| settlement_id | BIGINT | §6.11（关联结算）、§10.2（payment_task.settlement_id） | 结算 ID |
| pay_amount | DECIMAL(18,2) | §6.11.1（实付金额）、PAY-03 | 实付金额 |
| pay_method | VARCHAR(50) | §6.11.1（登记回单）、PAY-04 | 付款方式 |
| voucher_file | VARCHAR(200) | §6.11.2 PAY-04（回单附件，最多 5 个） | 回单附件 |
| status | TINYINT | 审计/状态机（§6.11.3、§8 付款状态） | 未付款/部分付款/已付清（R5/R6：付款免审批） |
| remark | VARCHAR(255) | 待确认 | 备注 |
| pay_no | VARCHAR(32) | §6.11（付款单号）、§10.2（payment_no） | 付款单号 FK-{yyyyMM}-{seq} |
| pay_date | DATE | §6.11.1（付款日期登记时填） | 付款日期 |
| reviewed_by | BIGINT | §6.11.1（财务审核人，PAYMENT 回调；R6 修正：付款免审批，字段留存审计） | 审核人 |
| reviewed_at | DATETIME | §6.11.1 | 审核时间 |
| confirmed_by | BIGINT | §6.11.1（线下登记确认人） | 登记确认人 |
| confirmed_at | DATETIME | §6.11.1 | 登记确认时间 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

> 注：PRD §6.11.1「生产版本需补齐实付登记、**作废或冲销**能力」、`PAY-08` 提及冲销，当前 `payment` 表无冲销/作废状态字段。详见文末风险提示。

### 6.3 service_deduction_item（服务扣款明细）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| assess_id | BIGINT | §6.10.2（服务验收考核单）、L812、P3c A5 | 服务考核单 ID |
| item_name | VARCHAR(100) | §6.8.2（扣款项目取服务考核指标）、L812 | 扣款项目 |
| amount | DECIMAL(18,2) | §6.10.2（扣款金额不得致应付为负）、BR-15 | 扣款金额 |
| reason | VARCHAR(200) | §6.8.2（扣款原因） | 扣款原因 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

---

## 模块七：审批中心（Approval）

### 7.1 approval_flow_def（审批流定义）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| flow_key | VARCHAR(50) | §6.16（审批类别/流程键）、P4 §3.1 | 流程键（=biz_type） |
| biz_type | VARCHAR(50) | §6.16（业务类型，七/八类） | 业务类型 |
| flow_version | INT | P4 §3.1（流程版本） | 流程版本 |
| flow_name | VARCHAR(100) | §6.16（审批名称） | 流程名称 |
| enabled | TINYINT | §6.14（审批流可配置）、§6.16 | 1=启用 0=停用 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 7.2 approval_node_def（审批节点定义）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| flow_key | VARCHAR(50) | §6.16（节点归属流程）、P4 | 所属流程 |
| node_code | VARCHAR(50) | P4（节点编码） | 节点编码 |
| node_name | VARCHAR(100) | §6.16（节点展示） | 节点名称 |
| seq | INT | §6.16（节点序/流转） | 节点序 |
| approver_type | VARCHAR(30) | §6.14（审批角色配置）、§2.2 | ROLE/DEPT_HEAD/USER |
| approver_value | VARCHAR(200) | §6.14（角色/部门负责人/用户） | 解析值 |
| amount_min | DECIMAL(18,2) | 待确认（金额阈值升级，§16.1 待确认，核对报告 R3 自行补参） | 节点生效金额下界 |
| amount_max | DECIMAL(18,2) | 待确认（同上） | 节点生效金额上界 |
| sign_type | VARCHAR(10) | 待确认（或签/会签，Q13 未拍板） | ANY/ALL |
| timeout_hours | INT | 待确认（超时升级，PRD 预留未实现） | 超时时限 |
| free_review | TINYINT | §6.16（本期一律必审） | 0=必审 |
| enabled | TINYINT | §6.14（节点启用） | 启用 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 7.3 approval_task（审批单据）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| biz_type | VARCHAR(50) | §6.16（审批类别 七类） | 业务类型 |
| biz_id | BIGINT | §6.16（来源单据） | 业务单据 ID |
| flow_key | VARCHAR(50) | §6.16（流程键） | 流程键 |
| status | TINYINT | 审计/状态机（§6.16 审批状态） | 待审批/已通过/已驳回 |
| current_node | VARCHAR(50) | §6.16（当前节点） | 当前节点 |
| remark | VARCHAR(255) | 待确认 | 备注 |
| payload_json | TEXT | §6.16（审批负载落库，P3 §4） | 审批负载 JSON |
| applicant | VARCHAR(64) | §6.16（申请人快照） | 申请人用户名 |
| applicant_id | BIGINT | §6.16（申请人） | 申请人 ID |
| amount | DECIMAL(18,2) | §6.16（金额摘要，节点金额区间匹配） | 金额摘要 |
| flow_version | INT | P4（提交时流程版本快照） | 流程版本快照 |
| version | INT | 内部管理（乐观锁防重审） | 乐观锁版本 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 7.4 approval_node（审批节点）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| task_id | BIGINT | §6.16（节点归属任务） | 任务 ID |
| node_def | VARCHAR(100) | §6.16（节点定义） | 节点定义引用 |
| approver | BIGINT | §6.16（审批人） | 审批人 |
| action | VARCHAR(50) | §6.16（审批动作 通过/驳回） | 审批动作 |
| comment | VARCHAR(255) | §6.16（审批意见） | 审批意见 |
| status | TINYINT | 审计/状态机（节点状态） | 节点状态 |
| node_code | VARCHAR(50) | P4（节点编码快照） | 节点编码快照 |
| seq | INT | P4（节点序快照） | 节点序快照 |
| sign_type | VARCHAR(10) | P4（签类型快照） | 签类型快照 |
| version | INT | 内部管理（乐观锁） | 乐观锁版本 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 7.5 approval_record（审批记录）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| task_id | BIGINT | §6.16（记录归属任务） | 任务 ID |
| node_id | BIGINT | §6.16（节点） | 节点 ID |
| approver | BIGINT | §6.16（审批人） | 审批人 |
| action | VARCHAR(50) | §6.16（动作） | 审批动作 |
| comment | VARCHAR(255) | §6.16（意见） | 审批意见 |
| approver_name | VARCHAR(64) | §6.16（审批人姓名快照，防改名留痕） | 审批人姓名 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 7.6 user_notice（站内通知）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| user_id | BIGINT | §6.16（通知接收人） | 接收人 |
| title | VARCHAR(200) | §6.16（审批待办/终态通知） | 通知标题 |
| content | VARCHAR(1000) | §6.16（正文含意见摘要） | 通知正文 |
| biz_type | VARCHAR(50) | §6.16（关联业务类型） | 业务类型 |
| biz_id | BIGINT | §6.16（关联业务单据） | 业务单据 ID |
| channel | VARCHAR(20) | §6.14（消息渠道）、PRD L1007/L224（站内/短信/企微预留） | site=站内（预留 sms/wecom/dingtalk） |
| read_flag | TINYINT | 待确认（PRD 未定义已读标记，属基础能力） | 0=未读 1=已读 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

---

## 模块八：集成与文件（Integration / File）

### 8.1 file_meta（文件元数据）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| file_key | VARCHAR(200) | §6.3.1（附件存储键）、§6.8.3 | 文件 key |
| original_name | VARCHAR(200) | §6.3.1（附件文件名） | 原始文件名 |
| size | BIGINT | §6.3.1（单文件≤20MB）、§12.3 | 文件大小 |
| sha256 | VARCHAR(64) | §12.3（文件校验/随机存储名） | 校验值 |
| biz_id | BIGINT | §6.3.1（业务关联） | 业务单据 ID |
| biz_type | VARCHAR(50) | §6.3.1（业务类型关联） | 业务类型 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 8.2 outbox_event（Outbox 事件）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| aggregate | VARCHAR(50) | §6.15.2（写入 integration_outbox）、§6.15.4 | 聚合类型 |
| aggregate_id | BIGINT | §6.15（聚合 ID） | 聚合 ID |
| type | VARCHAR(50) | §6.15（事件类型） | 事件类型 |
| payload_json | LONGTEXT | §6.15（事件载荷） | 载荷 |
| status | TINYINT | 审计/状态机（§6.15.4、§8 同步事件状态） | 0=PENDING 1=SENT 2=RETRY 3=FAILED |
| retry | INT | §6.15.4（重试次数） | 重试次数 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

### 8.3 sync_reconcile（同步对账）

| 字段 | 类型 | PRD 出处 | 说明 |
|-|-|-|-|
| id | BIGINT | 内部管理 | 主键 |
| event_id | VARCHAR(100) | §6.15.4（事件 ID，幂等）、AC-25/26 | 事件 ID |
| target | VARCHAR(50) | §6.15（下游系统） | 下游目标 |
| status | TINYINT | §6.15.4（对账状态） | 对账状态 |
| last_resp | LONGTEXT | §6.15.4（下游回写结果） | 最近响应 |
| created_by / created_at / updated_by / updated_at | BIGINT / DATETIME / BIGINT / DATETIME | 审计列 | 审计字段 |
| deleted | TINYINT | 内部管理 | 逻辑删除 |

---

## 一、「Schema 有、PRD 未直接定义」汇总

以下字段为技术 / 审计 / 内部治理用途，非 PRD 业务字段；其存在属工程实现的**有意为之**，不是 PRD 缺口，列此供评审区分：

1. **主键 id（雪花 ID）**：所有表均有，技术主键，无 PRD 依据（PRD §10.4 亦仅泛称 `id` 为内部主键）。
2. **逻辑删除 deleted**：全表统一逻辑删除位（`TINYINT`/`INT`），属框架约定（`§4``schema.sql` 头部声明），PRD 无对应。
3. **审计列 created_by / created_at / updated_by / updated_at**：全表统一审计字段（`schema.sql` 头部统一审计列声明）；对应 PRD 留痕要求（BR-19、§12.4）但列本身为技术实现。
4. **乐观锁 version**：`unit_conversion`、`budget_line`、`purchase_apply_item`、`contract`、`approval_task`、`approval_node` 等；用于并发兜底（预算占用、额度扣减、订单余量、防重审），工程实现，PRD 无对应列。
5. **通用业务备注 remark**：多数表的 `remark`（及 `budget_header.remark`、`spu.remark` 等）PRD 未单列定义，为通用备注字段，标记「待确认」属口径提示而非阻塞项。
6. **遗留/兼容性字段**：`supplier.category`（自由文本，已由 `supplier_category` 取代）、`supplier_sku.price_range`（遗留价格区间）、`purchase_apply_item.unit_snapshot`（已弃用停写，由 `purchase_unit`/`qty_in_base_unit`/`conv_rate_snapshot` 替代）。
7. **设计补充字段（P2b/P3/D14–D17 等）**：`purchase_order.authorized_by/authorized_name/authorized_time/auth_over_limit/auto_authorized`（D14 授权控制、D17 自动授权）、`purchase_order.source_type/source_reason`（D16 无申请来源留痕）；`approval_node_def.amount_min/amount_max/sign_type/timeout_hours/free_review`（金额区间/签类型/超时，部分 PRD 留白待确认）；`price_history.audit_status/audit_by/audit_at`（价格库审核，PRD §6.12.1 未明确）。

---

## 二、「PRD 要求但 Schema 可能缺」风险提示

基于 PRD 原文可明确佐证、且在本 Batch 3 范围内（所列业务表）的缺口：

1. **`supplier_qual` 缺「有效状态」与「变更类型」维度**：PRD §6.3.2 明确「审核状态」与「有效状态（正常/即将到期/已过期/已失效）」分开管理，并单独列出「变更类型（首次提交/审核中修改/驳回重提/过期更新/失效更新）」与「修改来源」；当前 `supplier_qual.status` 仅覆盖审核状态（待审/通过/驳回），无独立有效状态列（「即将到期/已过期」自动判定无落库列），无 `change_type`，亦无 §10.1 所列独立资质历史表 `supplier_qualification_history`。（核对报告 S5 已记录）
2. **`contract` 缺 PRD §6.9.1 L839 所列字段**：PRD 明示合同应维护「**经办部门、经办人、签订日期**、（标的、项目）」；当前 `contract` 表仅有 `valid_from/valid_to`，无 `owner_dept`/`owner`/`sign_date`，亦无 `项目`（project_id，PRD §6.4.1 注明项目仅统计归属）。经办部门/经办人为明确名词缺口。
3. **`spu` 缺 PRD §6.2.1 要求的 SPU 级「采购项类型」**：PRD §6.2.1「SPU 共享…**采购项类型**…」期望 SPU 层即带物料/服务标识；当前 `spu` 表无 `item_type` 列（仅 `purchase_apply_item.item_type` 存在），若需在 SPU 层区分物料/服务则存在缺口。
4. **`payment` 缺「作废/冲销」能力字段**：PRD §6.11.1「生产版本需补齐实付登记、**作废或冲销**能力」及 `PAY-08` 提及冲销；当前表无冲销/作废状态或反向流水列。（核对报告 R6 亦指出付款需补冲销/重放）

其余 PRD 列名差异（如 PRD §10 数据模型使用 `product_spu`/`purchase_request`/`rfq`/`supplier_quote`/`receipt` 等表名与字段名，与 schema 实际命名 `spu`/`purchase_apply`/`inquiry`/`quotation`/`arrival` 不同）属**命名映射差异**，字段语义已在上文逐表对齐，不列为 Schema 缺口；PRD §10.1 所列 `supplier_qualification_history`、`integration_inbox`、`product_mapping`、`receipt_sync_record` 等独立表不在本 Batch 3 业务表范围内，其缺失另行评估。

---

## 三、说明

- 本表将 `schema.sql` 各业务表字段逐一映射到 PRD v1.1（`参考-PRD_v1.1_原文.md`）章节号，是**上线前契约 / 验收基线**，不是替代性验收测试。
- 字段类型严格抄录 `schema.sql`；`PRD 出处` 仅引用 PRD 中实际存在的章节号（§6.x / BR-xx / §8 / §10 / §16 等），未杜撰编号。
- 状态字段（status 及各 *_status）统一标注「审计/状态机」，其具体枚举值与状态机流转以 PRD §8 状态机汇总与各模块状态图（§6.5.2/§6.6.5/§6.8.4/§6.10.3 等）为权威依据，落地枚举以设计文档（`口径与PRD一致性核对报告`、`P3c字段补齐规格设计`）为准。
- 已剔除纯 RBAC/身份基础设施表（`sys_*` 共 7 张），其字段非 PRD 业务字段，属标准框架表。
- 字段级一致性结论已复用 `口径与PRD一致性核对报告.md`（R1–R9/S1–S12）与 `P3c字段补齐规格设计.md`（A1–A6）的发现；凡 PRD 留白待确认项（如审批金额阈值、计重/超收规则、价格库审核）均在对应字段标注「待确认」。
