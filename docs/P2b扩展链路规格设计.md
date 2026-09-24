# P2b 扩展链路规格设计（D1 日常/框架 · D2 线下补录 · D9 独立寻源）

> 版本：v1.0（待用户拍板 Q1–Q5 后定稿）
> 依据：`docs/延期需求与占位登记.md` D1/D2/D9/B3、P2 设计 §8 预留口子、P2↔P3 接缝清单 CP-11、审计 R9（PRD BR-07 L1082 / §5.1.1 L341 / §6.6.1 L682）
> 现状核实（2026-09-24 代码实测）：
> - `PurchaseApplyType` 三链路枚举已在（STANDARD/DAILY/OFFLINE，IEnum 0/1/2）——申请侧口子在；
> - **`contract` 表实际缺 `award_id`、`renewed_from_id` 列**（P2 设计 §8 声称预留、DDL 未落，台账漂移）→ 本阶段补 ALTER；
> - `inquiry.apply_id` 为 NOT NULL 且无来源字段 → D9 需 ALTER（`apply_id` 可空 + `source_type` + `source_reason`）；
> - `InquiryServiceImpl:72` 写死"仅 APPROVED 申请可发起询价" → B3 口径与 D9 都改此处；
> - `AwardServiceImpl:114` award 按 inquiry 唯一；无"线下定标登记"入口 → D9 新增；
> - CP-11：无申请来源定标的预算占用锚点未定义（现 AwardServiceImpl 对无 applyId 显式跳过再校验并留痕）。

---

## 1. 范围（P2b 做 / 不做）

### 做
| 项 | 内容 | 台账 |
|---|---|---|
| D1 | 日常/框架免比价链路：type=DAILY 申请 → 审批通过 → 直接关联合同下单（跳过询价/定标）；框架合同续签 | D1 |
| D2 | 线下补录链路：type=OFFLINE 申请 → 合同手填登记（无定标来源）→ 下单三重校验照走 | D2 |
| D9 | 独立寻源：无申请来源询价（来源类型+寻源原因必填）+ **线下定标登记入口** + **无申请定标预算锚点（CP-11 收口）** | D9 |
| B3 | FULL_ORDER 申请允许再次询价（补充采购场景，余量校验天然防超转） | B3 |
| D13(P2b 部分) | S8 合同补类型/科目字段；S9 申请补用途/需求时间/项目名 | D13 |

### 不做（挪 P3b/后续）
S4 报价税率/运费/交期、S10 到货拒收退货/超收计重、S5 资质批量审核、S11 服务扣款多条明细取数——维持台账登记。

## 2. 数据模型变更（3 ALTER + 0 新表）

```sql
-- ① contract：补 P2 设计声称预留但未落的列
ALTER TABLE contract ADD COLUMN award_id      BIGINT       NULL COMMENT '来源定标（D2 线下补录为 NULL）';
ALTER TABLE contract ADD COLUMN renewed_from_id BIGINT     NULL COMMENT '续签来源合同（D1 框架续签）';
ALTER TABLE contract ADD COLUMN contract_type TINYINT      NOT NULL DEFAULT 0 COMMENT '类型：0物料/1服务（S8）';
ALTER TABLE contract ADD COLUMN subject_id    BIGINT       NULL COMMENT '预算科目（S8，线下补录链路预算锚定备用）';

-- ② inquiry：独立寻源
ALTER TABLE inquiry MODIFY COLUMN apply_id BIGINT NULL COMMENT '来源申请（D9 独立寻源为 NULL）';
ALTER TABLE inquiry ADD COLUMN source_type   VARCHAR(20)  NOT NULL DEFAULT 'APPLY' COMMENT '来源：APPLY/OFFLINE';
ALTER TABLE inquiry ADD COLUMN source_reason VARCHAR(500) NULL COMMENT '寻源原因（source_type=OFFLINE 必填，BR-07）';

-- ③ purchase_apply：S9 剩余字段
ALTER TABLE purchase_apply ADD COLUMN purpose       VARCHAR(500) NULL COMMENT '采购用途（PR-01）';
ALTER TABLE purchase_apply ADD COLUMN project_name  VARCHAR(200) NULL COMMENT '手工项目名（无预算项目时业务归属）';
-- expected_date 已有；"需求时间语义"合并进 expected_date，不加列
```
幂等迁移脚本：`scripts/sql/p2b_migration.sql`（information_schema 判断，对齐 p3_qa2_01 风格）。

## 3. 三条链路的状态机与规则

### 3.1 D1 日常/框架免比价（type=DAILY）
```
申请(DAILY) → 两级审批(同标准链路, APPROVED) → 采购员在"合同台账"直接关联合同下单：
  下单前置 = 申请 APPROVED + 合同 EFFECTIVE(框架) + 三重校验(§4 同 P2)
  申请明细 ⊆ 合同范围内商品；余量校验按申请 remain_qty
框架合同到期 → 续签：renew(id) → 新合同 renewed_from_id=旧id、独立审批（接口已存在 P2 §2.5，补 DB 列）
```
- 不变式：**DAILY 申请不允许创建询价**（`createInquiry` 拒绝 type=DAILY，错误码 3003 复用文案）；预算占用仍在**申请提交时**（同标准链路，锚点=申请）。

### 3.2 D2 线下补录（type=OFFLINE）
```
申请(OFFLINE, 金额手填+用途必填) → 两级审批 → 合同手填登记（award_id=NULL，金额=Σ申请明细，可改）→ 合同审批 → EFFECTIVE → 下单(三重校验)
```
- 合同登记校验：供应商准入（EXPIRED 阻断，三处拦截照旧）；金额 >0；`contract.subject_id` 选填（预算锚点仍=申请，合同科目仅统计冗余）。
- 不变式：OFFLINE 申请同样**提交即占预算**；跳过询价/定标是链路编排差异，三重校验/审批/预算组件零改动复用。

### 3.3 D9 独立寻源（无申请来源）
```
询价: createInquiry(source_type=OFFLINE, source_reason 必填, apply_id=NULL) → 发布/报价/比价 照旧
定标: 线下定标登记入口 createOfflineAward(inquiryId|直接登记, 部门+预算科目+金额 必填)
      → submit 时预算占用 occupy(deptId, subjectId, 当月, awardAmount, biz_type=AWARD, bizId=awardId)
      → 不足 → BUDGET 升级审批（复用既有分支，QA2-02 的"跳过"分支退役）
      → 合同登记(award 来源) → 下单：三重校验中"申请余量"闸以合同金额替代（无申请明细）
```
- **CP-11 收口（Q1 拍板项）**：推荐方案 A——**线下定标提交即占预算**（锚点=award），与"提交即占"口径对齐；释放点=定标作废/驳回。替换 AwardServiceImpl 中"无 applyId 显式跳过"分支。
- 下单闸门调整：无申请来源链路的三重校验第③重（Σ订单 ≤ 申请 remain_qty）替换为 **Σ订单 ≤ 合同 available_amount**（第①②重不变）。

## 4. 服务/接口变更清单

| 层 | 变更 |
|---|---|
| InquiryServiceImpl | :72 校验放宽：APPROVED 申请（含 FULL_ORDER，B3）或 source_type=OFFLINE+reason 必填；DAILY 申请 3003 |
| AwardService | 新增 `createOfflineAward`（无 inquiry 直登或 source_type=OFFLINE 询价转定标）；submit 无 applyId 分支改为 occupy |
| ContractService | 登记允许 award_id=NULL（D2）；renew 落 renewed_from_id；下单校验支持"无申请来源"模式 |
| OrderService | 三重校验第③重按链路分流（有申请=余量闸；无申请=合同额度闸） |
| VO | ApplySaveReqVO +purpose/projectName；InquirySaveReqVO +sourceType/sourceReason；ContractSaveReqVO +contractType/subjectId |
| 前端 | ApplyIndex 类型=DAILY/OFFLINE 时隐藏询价入口+用途/项目名字段；InquiryIndex 支持"独立寻源"新建；AwardIndex 新增"线下定标登记"按钮+部门/科目/金额必填；ContractIndex 合同类型/科目下拉 |

## 5. 验收要点（AC 摘录）
1. DAILY 申请发起询价被拒（3003），APPROVED 后关联合同下单成功，预算占用=申请提交时。
2. OFFLINE 申请全链：手填合同（无 award）→ 审批 → 下单三重校验通过；金额改超合同额度被拒。
3. 独立寻源询价：source_reason 空被拒；填后走完 报价→**线下定标登记（部门+科目+金额）**→ 提交占用落科目行 → 跨科目不互吃 → 合同下单。
4. FULL_ORDER 后再次询价成功；累计转单 ≤ 明细数量。
5. 回归：标准链路（STANDARD）全流程不受影响；84+ 例单测全绿。

## 6. 待拍板（Q1–Q5，均有最低风险默认）

| # | 问题 | 选项 | 默认建议 |
|---|---|---|---|
| Q1 | **无申请定标预算锚点**（CP-11） | A 定标提交即占（锚点=award）；B 合同生效时占；C 下单时占 | **A**（与"提交即占"口径一致，释放点=作废/驳回） |
| Q2 | DAILY 无框架合同时能否下单 | A 禁止（必须先有合同）；B 允许走紧急审批 | **A 禁止** |
| Q3 | FULL_ORDER 再询价 | A 允许（余量校验防超）；B 维持 3003 | **A 允许** |
| Q4 | D2 线下合同金额校验 | A 仅 >0+准入；B 加"≤申请总额"强校验 | **A**（申请总额校验由申请审批环节承担） |
| Q5 | D13 字段补齐范围 | A 仅 S8+S9（本规格 §2③）；B 连 S4/S10 一起 | **A**（S4/S10 挪 P3b，控范围） |
