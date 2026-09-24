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
