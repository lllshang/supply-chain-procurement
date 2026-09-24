# P2b 扩展链路规格设计（D1 日常/框架 · D2 线下补录 · D9 独立寻源）

> 版本：v1.0（待用户拍板 Q1–Q5 后定稿）
> 依据：`docs/延期需求与占位登记.md` D1/D2/D9/B3、P2 设计 §8 预留口子、P2↔P3 接缝清单 CP-11、审计 R9（PRD BR-07 L1082 / §5.1.1 L341 / §6.6.1 L682）
> 现状核实（2026-09-24 代码实测，Grep 工具复核——shell grep 曾静默失败致首版误判"contract 列漂移"，特此修正）：
> - `PurchaseApplyType` 三链路枚举已在（STANDARD/DAILY/OFFLINE，IEnum 0/1/2）——申请侧口子在；
> - `contract` 表 `award_id` / `renewed_from_id` / `contract_type` 列**均已存在**（实体字段齐），**无漂移**；仅缺 S8 `subject_id`；
> - `inquiry.apply_id` 为 NOT NULL 且无来源字段 → D9 需 ALTER（`apply_id` 可空 + `source_type` + `source_reason`）；
> - `award.inquiry_id` 为 NOT NULL 且无部门/科目列 → 线下定标需 ALTER（`inquiry_id` 可空 + `dept_id` + `subject_id`）；
> - `purchase_order.apply_id` **已可空**（L747 无申请订单有列基础）；`OrderCreateReqVO.applyId` 已在；
> - `InquiryServiceImpl:66` 写死"询价必须关联采购申请"+ 仅 APPROVED → B3/D9 改此处；DAILY 类型未拦截；
> - `AwardServiceImpl:114` award 按 inquiry 唯一；无"线下定标登记"入口；submit 对无 applyId 显式跳过再校验（QA2-02）→ D9 改为 occupy；
> - `OrderServiceImpl:189-215` 三重校验第③重（申请余量）依赖明细挂 `applyItemId` → 无申请来源订单需分流（跳余量闸，保留合同额度闸+价格校验）。

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

## 2. 数据模型变更（4 组 ALTER + 0 新表，实测收窄后）

```sql
-- ① inquiry：独立寻源（D9）
ALTER TABLE inquiry MODIFY COLUMN apply_id BIGINT NULL COMMENT '来源申请（独立寻源为 NULL）';
ALTER TABLE inquiry ADD COLUMN source_type   VARCHAR(20)  NOT NULL DEFAULT 'APPLY' COMMENT '来源：APPLY/OFFLINE';
ALTER TABLE inquiry ADD COLUMN source_reason VARCHAR(500) NULL COMMENT '寻源原因（source_type=OFFLINE 必填，BR-07）';

-- ② award：线下定标登记（D9/CP-11 锚点=award）
ALTER TABLE award MODIFY COLUMN inquiry_id BIGINT NULL COMMENT '来源询价（线下直接登记为 NULL）';
ALTER TABLE award ADD COLUMN dept_id    BIGINT NULL COMMENT '预算部门（线下登记必填，提交即占预算）';
ALTER TABLE award ADD COLUMN subject_id BIGINT NULL COMMENT '预算科目（线下登记必填，提交即占预算）';

-- ③ purchase_apply：S9 剩余字段
ALTER TABLE purchase_apply ADD COLUMN purpose       VARCHAR(500) NULL COMMENT '采购用途（PR-01）';
ALTER TABLE purchase_apply ADD COLUMN project_name  VARCHAR(200) NULL COMMENT '手工项目名（无预算项目时业务归属）';

-- ④ contract：S8 科目（统计冗余，预算锚点仍=申请/award）
ALTER TABLE contract ADD COLUMN subject_id BIGINT NULL COMMENT '预算科目（统计冗余）';
```
幂等迁移脚本：`scripts/sql/p2b_migration.sql`（information_schema 判断，对齐 p3_qa2_01 风格）；同步更新 `backend/src/main/resources/db/schema.sql`（新库自动初始化用）。

## 3. 三条链路的状态机与规则

### 3.1 D1 日常/框架免比价（type=DAILY）
> **PRD 溯源修正**："免比价"编排出自主流程三链路 docx/需求清单；PRD §6.5.1 L656 开发备注明确日常/项目"仅业务分类，后续流程一致"；**§6.7.1 L743 硬规则：订单必须挂履行中且有效期内合同（无例外）**；L747：日常订单可不关联申请但必须保留预算占用+合同来源。本链路是三链路要求的快捷编排，L743 底线不豁免。
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
| Q2 | DAILY 无框架合同时能否下单 | A 禁止（必须先有合同）；B 允许走紧急审批 | **A 禁止**——PRD §6.7.1 **L743 硬规则**"采购订单必须选择履行中且在有效期内的合同"无例外分支；L747 给的弹性是反方向（日常订单**可不关联申请**，但必须保留预算占用+合同来源）。无合同下单=三重校验失闸+裸订单，违背 L747 |
| Q3 | FULL_ORDER 再询价 | A 允许（余量校验防超）；B 维持 3003 | **A 允许** |
| Q4 | D2 线下合同金额校验 | A 仅 >0+准入；B 加"≤申请总额"强校验 | **A**（申请总额校验由申请审批环节承担） |
| Q5 | D13 字段补齐范围 | A 仅 S8+S9（本规格 §2③）；B 连 S4/S10 一起 | **A**（S4/S10 挪 P3b，控范围） |
