# P3b 阶段一 QA 独立验证报告（D10 预付款结算 + D11 订单状态机收敛）

> 验证人：software-qa-engineer-4（fresh eyes 独立运行态验证，不复述开发自验/主理人核验）
> 验证对象：`develop @ c5cf73b`（`4a58a8a` 枚举与DDL基座 → `470f8ff` D10 预付款 → `c8b4db4` D11 状态机收敛 → `c5cf73b` 测试）。
> 方法：JDK17(ms-17.0.18) wrapper `clean build`；**新建空库 `scm_qa_p2b3`→`scm_qa_p3b` 自动初始化** + 后端 `:18081` 运行态 HTTP 实测 + 直查 DB 对账；QA 自建主数据（SPU/SKU `991002` BOX→PCS×12、供应商+营业执照资质、预算行 `990402` 科目2/9月 5 万、`990403` 科目1 诱饵行）。未改业务代码、未 push。

## 0. 结论先行

**IS_PASS = false（五锚中 锚1/4 ✅，锚2 本体 ✅ 但存在 P1 资金口径漏洞，锚3 ❌，锚5 ⚠️）**

| 锚 | 结论 | 级别 |
|---|---|---|
| 构建 + 单测 112 例 | ✅ **22 类 / 112 例 / 0F 0E 0S**（QA 逐类解析 JUnit XML；新增 `SettlementPrepaymentTest` 3 + `OrderStatusConvergenceTest` 3，断言质量合格：`verify(orderMapper, never()).updateById` 钉死零回写、captor 字段断言、派生进度断言） | ✅ |
| ★锚1 D11 订单状态零回写 + 派生进度 | ✅ **通过**（两单全链实测：结算审批通过、付款确认后 DB `purchase_order.status` 恒 =2(RECEIVED)；独立 grep main 无 `setStatus(OrderStatus.SETTLED/PAID)` 写点；VO `settleProgress/paidProgress` 正确推导并封顶 1.0） | ✅ |
| ★锚2 D10 预付款闭环 | ⚠️ **本体全过，但"累计"口径漏在途单 → 超额结算 P1**（见 3.1） | 🔴 |
| ★锚3 PaymentStatus PARTIAL 流转 | ❌ **未落地**：`PARTIAL(1)` 全 main 零赋值点，首笔分期确认后直接 `PAID(2)`（见 3.2） | 🟠 |
| ★锚4 既有 P3 结算/付款链路回归 | ✅ 通过（物料结算/draft/审批/核销、付款 #39 在途封顶、对账单口径、守恒零差异；服务结算逻辑本批零改动——diff 核对，运行态未复测） | ✅ |
| ★锚5 p3b_schema.sql 真实落库 | ⚠️ 手工执行后列存在且值正确；**但新库自动初始化路径不含该 ALTER → 首个结算接口 500**（见 3.3） | 🟠 |

**问题清单：P1×1、P2×2、P3×2**（详见 §3）。

---

## 1. 环境与基线复核

- `git status` 干净、HEAD=`c5cf73b`；4 笔 commit diff 核对：改动集中在 settlement/payment/order 派生字段与枚举/Mapper 字面量，**未触碰服务结算与到货链路**。
- `clean build` BUILD SUCCESSFUL（10s，绝对路径 gradlew `-p backend`）；JUnit XML 汇总 112/112 绿。
- 空库 `scm_qa_p3b` 自动初始化 48 表；QA 种子：供应商 `2102778214877458433`+APPROVED 资质、SKU `991002`（BOX→PCS rate=12，price 88/基本单位）、预算行 `990402`（科目2/9月/50000）、`990403`（科目1/30000，跨科目诱饵）。
- 链路数据：两个订单均走 线下定标→合同(EFFECTIVE)→无申请下单(3 BOX=**3168 元**)→RECEIVED+到货 36 基本单位。

## 2. 五锚运行态证据

### ★锚1 D11 订单状态机收敛 + 派生进度 —— ✅

| 步骤 | 实测 |
|---|---|
| 结算审批通过（预付款 3000 / 168 / 尾款 2168 等多单） | `SELECT status FROM purchase_order` 恒 **=2(RECEIVED)**，从未出现 3(SETTLED)/5(PAID) |
| 付款确认（UNPAID→confirm→PAID，两单共 4 笔） | 订单 DB status 仍 **=2(RECEIVED)**；VO `status:"RECEIVED"` |
| 独立 grep（不复述工程师） | `grep -rn 'setStatus(OrderStatus\.(SETTLED\|PAID))' src/main/java` **零命中**；`OrderStatus.SETTLED/PAID` 在 main 仅剩 `FulfillmentAdjustServiceImpl:157-158` 的读比较（语义保留，合法） |
| 派生字段 | 结算后 `settleProgress=1.0`、付款前 `paidProgress=0.0`；付 1000/2168 后 `paidProgress=0.6843`；付满后 `paidProgress=1.0`（封顶正确）。计算口径实测 = `sumSettledAmount / Σ明细 price×qty_base`、`sumPaidAmountByOrder(status=2) / 同基数` |
| 守恒 | 每步后 `Σ(OCCUPY)−Σ(RELEASE) == used_amount` 全库零差异（writeOff 不参与该恒等式，符合 P3 口径） |

### ★锚2 D10 预付款闭环 —— 本体 ✅ / P1 漏洞

| # | 场景 | 实测 |
|---|---|---|
| ① | `POST /settlements/prepayment/{orderId}` | 200；DB：`type=2(PREPAYMENT) status=0(PENDING) payment_stage=1 prepayment_deduction=0.00 settled_qty_base=NULL arrival_id=NULL` ✅ |
| ② | 预付款 SETTLEMENT 审批通过 | `settlement.status=1`；**订单状态不变(RECEIVED)**；`budget_occupy_log` 出现 `action=2(WRITE_OFF) amount=1000.00(正值) biz_id=orderId`；`budget_line.used_amount` **不变**（核销=转移，与 P3 既有口径一致）✅；守恒零差异 |
| ③ | 单笔超额拦截 | 预付 4000 > 订单有效金额 3168 → `3000 预付款累计 4000.00 超出订单有效金额 3168.00`，**不落库** ✅（拦截码 3000=BIZ_ERROR，与系统业务硬控惯例一致） |
| ④ | 尾款自动扣减（预付款已 SETTLED 后） | 尾款 `isFinal=1` 自动金额 = 3168−1000(已付预付) = **2168.00** ✅；`prepayment_deduction=1000.00` **回填** ✅；`sumPrepaymentPaid` SQL 实测=1000、draft `prepaidPaid=1000` 一致 ✅；校验 `累计已结(含预付)+本次−预付抵扣 = 应结总额` 通过 ✅ |

**🔴 P1-1（资金口径）：在途（PENDING）预付款不参与"累计上限"与"尾款结清校验"→ 可超额结算 2 倍**

- 运行态复现（订单 3168 元）：连续发起 3 笔预付款 3000/200/168（合计 **3368 > 3168**）全部创建成功——cap 只看 `sumPrepaymentPaid`（`SettlementMapper` `status=1 AND type=2`），PENDING 不可见；随后创建尾款单（此时 3 笔预付款全 PENDING）→ 校验按 `0(已结预付)+0(非预付已结)+3168 == 3168` **通过**，`prepayment_deduction=0`；依次审批全部 4 单后，订单累计已结 = **6336.00 = 订单有效金额 ×2**；对账单 `totalPayable=6336`。
- 影响面：超额部分**可继续发起付款**（付款封顶按单张结算单金额各自独立，#39 守卫拦不住跨单合计超额）；`settleProgress` 被封顶 1.0 **静默掩盖**超额；预算侧无资金损失（`writeOff` 有 `take = balance.min(remaining)` 下界，超核销不可能，已核 `BudgetOccupyServiceImpl:225`）。
- 根因（3 处同源）：`createPrepaymentSettlement` 上限、`createSettlement` 尾款分支、`validatePhaseAndFinal` 全部只聚合 `status=1`；`submit()`/审批回调**无再校验**。
- 修复方向（供工程参考）：预付 cap 与尾款校验改为 `status IN (0,1)` 口径（含在途），或审批回调时再校验；尾款单 `prepayment_deduction` 在**审批通过时**按实际已付预付回填而非创建时快照。

### ★锚3 PARTIAL 流转 —— ❌ 未落地

- 枚举：`PaymentStatus = UNPAID(0)/PARTIAL(1)/PAID(2)`，REJECTED 已删 ✅；`PaymentMapper` 字面量已同步（`sumPaidAmount status=2`、`sumCommittedAmount IN (0,1,2)`，行为实测正确：付满后再付 1 → `3000 累计付款（含在途）超出结算金额` ✅ #39 口径保持）。
- **但 `PaymentStatus.PARTIAL` 在 main 全无赋值点**（grep 零命中）：`confirmPayment` 对每张付款单一律 `setStatus(PAID)`。实测两单 4 笔分期：首笔（累计 1000 < 2168 / 2000 < 3168）确认后付款单 status = **2(PAID)**，非任务书预期的 1(PARTIAL)。
- 判定：任务书锚3"分期第一笔→PARTIAL"**未实现**（P2-1）。若语义本意是"结算单维度累计部分付款"，则该状态落在错误实体上（付款单自身一旦确认即整单付清），需产品/工程澄清口径后落地或删除该枚举值。

### ★锚4 既有链路回归 —— ✅

| 项 | 实测 |
|---|---|
| 物料结算全链 | draft(`storedQtyBase=36, suggestAmount=3168`) → create → submit → SETTLEMENT 审批 → SETTLED → 核销流水 → 付款，全程无 NPE/状态错乱 |
| #39 在途封顶 | 已承诺 3168 + 本次 1 → `3000` 拒绝 ✅ |
| 对账单 | `payable = Σ SETTLED 结算单金额`、`paid = Σ status=2 付款`（实测两单 9504/5336 与 DB 逐单一致）；order2 正常单 payable=3168 精确 |
| 守恒 | 全程零差异（含 6336 超额场景——writeOff min 守卫防污染） |
| 服务结算 | 本批 diff 未触碰服务结算代码（`git diff 8f4f158..c5cf73b` 核对）；运行态未复测（边界，见 §4） |

### ★锚5 p3b_schema.sql 真实落库 —— ⚠️

- 手工执行 `scripts/sql/p3b_schema.sql` 后：`settlement.payment_stage tinyint NULL`、`prepayment_deduction decimal(18,2) NOT NULL DEFAULT 0` 两列在位；预付款行 `payment_stage=1`、尾款行 `prepayment_deduction=1000.00` 回填正确 ✅。
- **但应用自动初始化不执行它** → P2-2（见下）。

---

## 3. 问题清单

### 🔴 P1-1【后端·P1】在途预付款不参与累计上限/尾款校验 → 可超额结算 2 倍并可付款
见 §2 锚2。复现：3168 元订单 → 3 笔在途预付款 3368 元全部创建成功 → 尾款单（创建时点在途不可见）校验通过 → 全部审批后累计已结 6336 元、对账单 payable=6336。文件：`SettlementMapper.sumPrepaymentPaid`（status=1 口径）、`SettlementServiceImpl.createPrepaymentSettlement/createSettlement/validatePhaseAndFinal`（同口径 + 无审批时再校验）。

### 🟠 P2-1【后端·P2】PaymentStatus.PARTIAL 无赋值点，分期首笔确认即 PAID
见 §2 锚3。`PaymentServiceImpl.confirmPayment` 一律 `setStatus(PaymentStatus.PAID)`；枚举 javadoc 宣称的 `UNPAID→PARTIAL→PAID` 流转不存在。

### 🟠 P2-2【后端·P2】新库自动初始化路径缺 P3b 两列 → 结算接口 500
`db/schema.sql` 的 `ALTER TABLE settlement`（:936）仅含 P3 列，**未追加 `payment_stage`/`prepayment_deduction`**；`scripts/sql/p3b_schema.sql` 不在任何自动初始化链路。实测：空库自动初始化（48 表）后 `POST /api/v1/settlements/prepayment/{orderId}` → **HTTP 500**，应用日志 `Unknown column 'payment_stage'`（3 次）；手工执行迁移后同接口 200。与此前批次（P1/P2b/P3 的 ALTER 均同步进 schema.sql）做法不一致，新环境/CI 起库即坏。

### 🟡 P3-1【文档·P3】`PaymentServiceImpl` 类 javadoc 仍宣称"结算单全部付清 → 订单 PAID"
与 D11 实现矛盾（该方法注释已改，类头注释未同步），易误导后续维护。

### 🟡 P3-2【规格·P3】尾款结算单 `payment_stage` 落 NULL
列注释定义"1=预付 2=进度款 3=尾款"，但 `createSettlement` 尾款分支未置 `paymentStage=3`（实测尾款行为 NULL）。实体注释"一次性/物料可空"与列注释口径不一，建议规格落笔（预付=1 已正确落库）。

---

## 4. 已验 / 未验边界

**已验**：112 例单测复跑；锚1 全部（2 单 × 结算+付款全链）；锚2 ①②③④（含干净序与漏洞序）；锚3（4 笔分期行为 + Mapper 字面量口径）；锚4（物料全链 + #39 + 对账单 + 守恒）；锚5（迁移生效 + 自动初始化缺列实证）；独立 grep D11 写点。

**未验/边界**：服务结算运行态（本批零代码改动，仅 diff 核对）；前端 VO 消费（settleProgress/paidProgress 渲染）；MinIO 仍降级；超额结算后对预算台账的长期影响（writeOff min 守卫已核，结构性安全）。

## 5. QA 环境留痕

- 实例 `:18081`（scm_qa_p3b，48+2 表）保留；脚本 `/tmp/qa3/rt4.py`、`p3b_chain.py`、`p3b_anchors*.py`、`p3b_clean.py`。
- 测试库含 P1-1 复现数据（订单 2103104985115103233 累计已结 6336），非交付数据。

---

# 第 2 轮聚焦回归（P1-1/P2-1/P2-2 修复验证 · `bdf2af0`）

> 验证对象：`develop @ bdf2af0`（= 任务书基线 `e9f5abf` + 1 笔 B9 台账 docs 提交，不影响代码）。修复链：`f5af0b9`（P1-1 committed 双保险 + P2-2 schema.sql 合并 + P3-1/P3-2）→ `354aafe`（口径B 生产代码）→ `35ed7f6`（D13 docs）→ `e9f5abf`（PB-01 AC 测试）。
> 方法：`clean build`（JDK17，绝对路径 gradlew）+ **新建空库 `scm_qa_p3b2` 自动初始化**（顺带验证 P2-2 修复）+ 后端 `:18081` 重部署 + 运行态 HTTP 实测 + DB 对账。QA 自建两订单链路（3168 元 / 2112 元）。未改业务源码、未 push。

## 6.0 结论先行

**IS_PASS = false（仅剩 P2×1；P1-1 / P2-2 / 口径B 本体全部修复证实生效，锚A/C/D 全过）**

| 锚 | 结论 | 关键证据 |
|---|---|---|
| 构建 + 单测 | ✅ | BUILD SUCCESSFUL 17s；**22 类 / 119 例 / 0F 0E 0S**（QA 逐类解析 JUnit XML；SettlementPrepaymentTest 7 + SettlementPaymentTest 7 + OrderStatusConvergenceTest 4） |
| 锚A P1-1 资金口径 | ✅ | ① cap 含在途：预付 2000 成功 → 第 2 笔 2000 被 `3000 预付款累计（含在途）4000.00 超出订单有效金额 3168.00` 拦截不落库（committed SQL `status IN(0,1)` = 2001 实测）；② 尾款扣减含在途：自动金额 **1999 = 3168 − 1169(在途预付)**、`prepayment_deduction=1169.00` 回填、**`payment_stage=3` 落值**（P3-2 同步修复生效）；③ 审批兜底：脏数据向量（SQL 造 2500 PENDING 绕过 cap）→ approve 报 `3000 预付款审批拦截：累计预付款（含本单）3668.00 超出订单有效金额 3168.00`，**settlement 保持 PENDING(0)、WRITE_OFF 流水数不变（不核销）、task 保持 status=0** ✅；④ PUT 封顶：committedExclThis+本次 ≤ 应结（代码 `SettlementServiceImpl:266-272` 审读 + 单测覆盖）；运行态另证实 SETTLED 单 PUT 被状态闸拦截（`3003 仅待结算可修改`） |
| 锚B PB-01 口径 | ⚠️ | ① 两笔付款确认后均 **PAID(1)**（无中间态，payment 全表 status 分布仅 1）✅；结算派生 payStatus 在**无抵扣单**正确：500/1056 → `PARTIAL/0.4735` → 付满 `PAID/1.0` ✅；**含预付抵扣的尾款单派生错误**（→ P2-R2-1 ❌）；② BR-17：付满 1999 后再付 1 / 0.01 → `3000 累计付款（含在途）超出结算金额` ✅；③ 订单 paidProgress 仅尾款付清时 =0.631(<1.0)，全部结算付清后 =1.0 ✅；④ 独立 grep：`PaymentStatus.PARTIAL`/`REJECTED` 代码引用、mapper `status = 2`/`IN (0,1,2)` 字面量**全部零残留**（枚举仅存 javadoc 历史说明）✅ |
| 锚C 回归面 | ✅ | 物料结算全链（draft→create→submit→审批→核销→付款）；#39 单据封顶；对账单 `payable=4224 paid=4224 balance=0`（= 两订单 3168+1056 精确）；守恒 Σ(OCCUPY)−Σ(RELEASE)==used 全程零差异；**D11：两订单 DB status 全程 =2(RECEIVED)，从未 3/5**；settleProgress/paidProgress 封顶 1.0 |
| 锚D P2-2 | ✅ | fresh 库 `scm_qa_p3b2` 自动初始化 48 表后 `settlement` **两列已在位**（schema.sql 合并生效）；首个预付款接口直接 200（第 1 轮 500 路径封死）；`p3b_schema.sql` 重跑**幂等 no-op**（exit 0 无报错） |
| 锚E B9 留白 | ✅ 确认 | 台账 B9 已登记（`bdf2af0`），本轮双 grep 确认无作废/取消端点，不另测 |

## 6.1 新发现问题

### P2-R2-1【后端·P2】尾款单派生 `payable` 重复扣减预付款——含预付抵扣的尾款单 payStatus 永远提前变 PAID，PARTIAL 不可见

- **公式缺陷**：`fillPayProgress`（`SettlementServiceImpl.java:383-384`）`payable = amount − prepaymentDeduction`；但 `createSettlement` 尾款分支的 amount 在创建时**已按"应结总额 − 预付款抵扣"净额化**（自动金额 1999 = 3168 − 1169，且 `validatePhaseAndFinal` 的结清恒等式使"手输 gross 金额"路径不可能通过校验）→ 净额化 + 再减 deduction = **双重扣减**。
- **运行态实锤**：尾款单 amount=1999、deduction=1169 → 派生 payable=830（真实现金义务应为 1999）；**实付 1000 时（1000/1999）即显示 `payStatus=PAID / paidProgress=1.0`**（1000≥830 被封顶），PARTIAL 在该类单据上永不可见；任务书锚B① 预期的 `PARTIAL(0.5)→PAID(1.0)` 依次推进仅在无抵扣单上成立（实测 500/1056 → PARTIAL/0.4735 → PAID/1.0 ✅）。
- **台账自洽性旁证**：各结算单现金义务合计 = 预付 1169 + 尾款 payable 830 = 1999 ≠ 订单应结 3168（而 amount 口径合计 1169+1999=3168 精确）——进一步证明 payable 基数应取 `amount` 本身。
- **影响面**：仅派生展示字段（payStatus/paidProgress/paidAmount/payableAmount 回显）；**资金安全无恙**——付款封顶（BR-17）按 `settlement.amount` 口径（实测 1999 处拒绝第 3 笔），对账单也按 amount 口径精确。
- **单测假绿根因**：PB-01 AC① 用例的结算单 deduction=0（未覆盖"尾款+预付抵扣"组合），与第 1 轮 P1-1 同型的 mock 盲区。
- **修复建议**：`fillPayProgress` 的 payable 改为 `amount`（净额已含抵扣）；或尾款创建时不净额化 amount 而由 payable 派生——二者取一，同步修订 AC① 用例补一条 deduction>0 断言。**修复后仅需轻量复验（尾款单 PARTIAL→PAID 推进 + AC 用例）即可收口。**

## 6.2 本轮已验 / 未验边界

**已验**：119 例全量单测；锚A ①②③④（含脏数据兜底向量与运行态守恒）；锚B ①②③④（含无抵扣/含抵扣双路径对比）；锚C 全项；锚D（fresh-init + 幂等重跑）；锚E 登记确认。

**未验/边界**：服务结算运行态（本批仍零改动）；前端 payStatus/paidProgress 消费；MinIO 降级；PUT 封顶的"真越界"运行态样本（PENDING 单在越界前会被 3003 状态闸或 cap 拦截，cap 边界由单测覆盖，运行态构造需复杂前置，判定为低风险）。

## 6.3 第 2 轮结论

P1-1（committed 双保险三处切换 + 审批兜底）、P2-2（schema.sql 合并 + 迁移幂等）、口径B（PARTIAL/REJECTED 收敛 + 派生表达 + BR-17）**全部修复证实生效**；剩余 **P2-R2-1** 一项（尾款单派生 payable 重复扣减，展示域缺陷、资金安全无恙），建议修复（改动点单一）+ 补 deduction>0 断言后做轻量复验即可收口 P3b。

---

# 第 3 轮轻量复验（P2-R2-1 修复 · `f63277e`）

> 范围最小化：复用 `:18081`/`scm_qa_p3b2`（重部署新 jar，未重建库）；新建第 3 订单链路（3168 元：预付 1169 + 尾款 1999/抵扣 1169）复跑第 2 轮实锤场景。`clean build` 21s + **22 类 / 120 例 / 0F 0E 0S**（含新增 deduction>0 AC 断言）。

| # | 复验项 | 实测 | 判定 |
|---|---|---|---|
| 1 | ★尾款单 PARTIAL→PAID 推进 | 实付 1000/1999 → `payStatus=PARTIAL, payableAmount=1999.0, paidProgress=0.5003`（**旧缺陷形态 PAID/1.0 消失**，与新增 AC 断言 0.5003 一致）；付满 999 → `PAID/1.0` | ✅ |
| 2 | ★台账口径旁证 | 订单结算单 amount 合计 = **3168.00 精确**（预付 1169 + 尾款 1999）；实付合计 1999（尾款付清、预付未付）→ 订单 `paidProgress=0.631` 正确；守恒零差异 | ✅ |
| 3 | BR-17 / #39 不回退 | 尾款在途 1000 + 再来 1000 → `3000 累计付款（含在途）超出结算金额：已承诺 1000.00，本次 1000，结算 1999.00` ✅（与 #39 同 guard） | ✅ |
| — | D11 顺带复核 | 订单 DB status 全程 RECEIVED(2) | ✅ |

**IS_PASS = true——P2-R2-1 修复证实生效，P3b 后端可收口（R4+R5 完成）。** 残余：B9（预付款作废端点，台账已登记）、服务结算运行态抽样（本批零改动，沿用第 1 轮边界）。
