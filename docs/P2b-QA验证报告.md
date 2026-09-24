# P2b 扩展链路 · QA 独立验证报告（第 1 轮）

> 验证人：QA 严过关（software-qa-engineer-4，fresh eyes 独立复现，不复述开发自验报告 `docs/P2b验证报告.md`）。
> 验证对象：`develop @ 08f4a5f`（P2b 批次1 `8c050ce` / 批次2 `67ab896` / 批次3 `44b8479` / `a46947d` / 报告 `08f4a5f`）。
> 规格：`docs/P2b扩展链路规格设计.md` v1.0（Q1–Q5 按默认：Q2=A 禁止无合同下单，PRD L743 依据）。
> 方法：JDK17(`ms-17.0.18`) + `backend/` Gradle 8.7 wrapper `clean build`；**新建空库 `scm_qa_p2b` 由应用自动初始化**（JDBC `characterEncoding=UTF-8`）；MySQL8@3307 + Redis7@6380；后端运行态 `:18081` HTTP API 实测 + 直查 DB 对账；QA 自建预算行 `990402`(科目2/9月/20000)、`990410`(科目2/10月/30000)、`990403`(科目1/9月/999999，跨科目诱饵)、SKU `991002`(BOX→PCS rate=12)、供应商 `2102778214877458433`+资质。
> 约束：未改业务源码、未动飞书、未 push。

---

## 0. 结论先行

**IS_PASS = false（P1 级 3 项 + P2 级 1 项 + P3 级 2 项；开发声明的 8 项冒烟本体全部复现通过，90 例单测全绿属实）**

- D1/D2/D9/B3 四条链路**正向路径全部独立复现通过**，含预算占用落**正确科目行**、跨科目不互吃、合同额度扣减对账、全库守恒零差异。
- 但线下定标"提交即占"（CP-11 方案A）只做了一半：**占用有了，释放没有** —— AWARD 驳回不释放（P2b-3）、驳回重提**重复叠加占用**（P2b-4），规格明文"释放点=定标作废/驳回"未实现且无作废端点。
- L747 无申请来源下单的**合同额度闸真实兜得住**（超额度被拒、额度不扣），但**订单变更增额完全绕过预算硬控**（P2b-5）——增量既不校验也不占用也不升级，`budget_occupied` 虚增而 `budget_line.used` 不动。
- 开发报告 §4 自述的设计偏离（预算不足直接拦截，不走 BUDGET 升级）**实测成立且语义正确**，评估为可接受（P2b-7，P3），但需规格落笔确认。

### 构建与单测（复验点 1）

| 项 | 结果 |
|---|---|
| `clean build`（JDK17 + 8.7 wrapper） | ✅ BUILD SUCCESSFUL（16s） |
| 单测 | ✅ **19 类 / 90 例 / 0 失败 / 0 错误 / 0 跳过**（QA 逐类解析 `build/test-results/test/TEST-*.xml`，未采信口述） |
| 新增 | `P2bExtensionTest` 6 例（84→90） |

**P2bExtensionTest 断言质量审读**：6 例断言均实质（错误文案关键片段 + 落库字段捕获断言 `sourceType/applyId/deptId/subjectId/inquiryId`），非空转。**但覆盖缺口**：未覆盖 ①线下定标**提交即占**（CP-11 核心行为）、②驳回释放/重提语义（恰是本轮 P2b-3/P2b-4 的洞）——见 P2b-8。

---

## 1. 运行态复现（复验点 2，QA 自建数据，非复用开发数据）

### 1.1 D1 日常/框架免比价

| # | 验证点 | 实测 | 判定 |
|---|---|---|---|
| 1a | DAILY 申请发询价被拒 | `POST /api/v1/inquiries {applyId}` → `3003 当前状态不允许该操作：日常/框架采购为免比价链路：请关联合同下单，不允许发起询价` | ✅ |
| 1b | DAILY 全链（AC1） | DAILY 申请提交 → 占用 440（申请预估价）→ 两级审批 → **APPROVED** → 关联合同下单 → 转移（APPLY −440 → ORDER +440+4840=5280，P3 行6 差额修正口径）→ 订单 `CREATED`、`budget_occupied=5280` → **合同额度 10000→4720 扣减对账** → 守恒零差异 | ✅ |
| 1c | 预算占用时点 | 占用发生在**申请提交时**（锚点=申请），非下单时 | ✅ |

### 1.2 D9 独立寻源

| # | 验证点 | 实测 | 判定 |
|---|---|---|---|
| 2a | 缺寻源原因被拒 | `4000 独立寻源必须填写寻源原因（BR-07）` | ✅ |
| 2b | 带原因成功 | `inquiry.apply_id=NULL`、`source_type=OFFLINE`、`source_reason` 落值 | ✅ |
| 2c | 独立寻源带 applyId 拒绝 | `4000 独立寻源询价不应关联采购申请` | ✅ |
| 2d | B3：FULL_ORDER 再询价 | 运行态成功（`source_type=APPLY`、applyId 落值） | ✅ |

### 1.3 线下定标登记（CP-11 方案A · ★合规重点）

| # | 验证点 | 实测 | 判定 |
|---|---|---|---|
| 3a | 缺部门/科目 | `4000 线下定标登记必须填写预算部门与预算科目（提交时即占预算）` | ✅ |
| 3b | 缺明细 | `4000 线下定标明细至少一条` | ✅ |
| 3c | 登记成功 | `award.inquiry_id=NULL`、`dept_id=1`、`subject_id=2`、`supplier_id`=唯一中标、`amount=10560`（88×10BOX×rate12） | ✅ |
| 3d | **提交即占预算，落正确科目行** | submit → `budget_occupy_log(biz_type=AWARD, biz_id=awardId, action=占用, 10560, balance 0→10560)`，`budget_line 990402(科目2/9月) used 0→10560` | ✅ |
| 3e | **跨科目不互吃（AC3）** | 科目1 预置 999999 元行 → award 挂科目2 提交后**科目1 行 used 仍为 0**（不互吃，R1 科目维度在 D9 锚点路径上真正生效） | ✅ |
| 3f | 科目行不存在 | award 挂不存在科目 777 → `3000 线下定标预算不足：无当月预算行（部门 1 2026年9月）`，**零占用** | ✅ |
| 3g | 预算不足直接拦截 | 8800/105600 元 vs 可用 5000 → `3000 线下定标预算不足：…（请先调整月度预算再登记）`，**零占用、无 AWARD 任务、无 BUDGET 任务** | ✅（偏离，见 P2b-7） |
| 3h | **驳回释放（★）** | 提交占 10560 → AWARD 审批驳回（`award.status=2 REJECTED`）→ **`used` 仍 10560，无 RELEASE 流水** | ❌ **P2b-3** |
| 3i | **驳回后重提（★）** | 调整明细（5280）→ 再提交 → `used 10560→15840`，**两笔 OCCUPY 流水同一 bizId**（应为 5280） | ❌ **P2b-4** |
| 3j | 作废端点 | `AwardController` 无 void/close/release 端点 → 驳回定标的占用**无任何释放路径** | ❌ （P2b-3 佐证） |

### 1.4 D2 线下补录全链

| # | 验证点 | 实测 | 判定 |
|---|---|---|---|
| 4a | OFFLINE 申请（用途必填字段落地） | `purpose` 落值；提交即占 880（申请预估价）落**科目2 正确行** | ✅ |
| 4b | 两级审批 | `PURCHASE_PENDING → APPROVED(3)` | ✅ |
| 4c | 手填合同（awardId=NULL） | 创建成功，`contract.award_id=NULL`、`subject_id=2`（S8 统计冗余）→ `CONTRACT` 审批 → `EFFECTIVE(2)`、`available_amount=10560` | ✅ |
| 4d | 下单三重校验 | 成功；**转移链**：APPLY −880 → ORDER +880 + 差额 9680 = 10560（合同价 88×10×12）；`budget_line.used=10560`；申请 → `FULL_ORDER(6)`；合同额度 10560→0 | ✅ 守恒零差异 |

### 1.5 L747 无申请来源下单（★合同额度闸）

| # | 验证点 | 实测 | 判定 |
|---|---|---|---|
| 5a | applyId=null 下单 | 成功：订单 `CREATED`、`apply_id=NULL`、`budget_occupied=2112`；**合同额度 5000→2888 扣减对账** | ✅ |
| 5b | 无申请明细缺采购单位 | `4000 无申请来源明细必须指定采购单位（无申请明细可取默认单位）：SKU 991002` | ✅ |
| 5c | **超合同额度** | 100 BOX = 105600 vs 可用 2888 → `3000 超出合同可用额度：可用 2888.00，本单 105600.00`，**额度未扣** | ✅ 闸有效 |
| 5d | **变更增额预算校验（★）** | 放开合同额度后 `change 2→50 BOX`（增额 50688）→ **成功**，`budget_occupied 2112→52800`，`budget_line.used` 不动（10560），**无 BUDGET 升级任务** | ❌ **P2b-5** |
| 5e | D9 订单取消 | 合同额度 20000 全额回冲；AWARD 锚点占用 10560 **保留**（锚点不变，合理不误释放） | ✅ |

### 1.6 回归（复验点 3）

| 项 | 实测 |
|---|---|
| 标准链路 | STANDARD 申请 → 提交占用 88（落科目2 正确行，`biz_type=APPLY`）→ 两级审批 → `APPROVED`；守恒零差异 |
| HTTP 契约 | 未登录 401 / 未知路径 404 |
| P2b-1 修复确认 | 新库 `scm_qa_p2b` 自动初始化 **49 表含 `price_history`**（`a46947d` 合并 DDL 生效） |
| P2b-2 修复确认 | `applyId=null` 下单入口放行（原"合同/申请/明细均必填"已放宽为"合同/明细均必填"） |
| 前端 | `AwardIndex.vue`（线下定标 8 处命中）、`InquiryIndex.vue`（独立寻源/寻源原因 13 处）、`ApplyIndex.vue`（用途/项目名 8 处）、`contract/ContractIndex.vue`（合同类型/预算科目 10 处）——静态核对字段存在，**未做渲染级验收** |

---

## 2. 代码对照规格审读（复验点 4）

### 2.1 AwardServiceImpl 线下定标 occupy 分支（§3.3 / CP-11）

`createAward`（`inquiryId==null` 分支）与 `submit`（`applyId==null` 分支）实现与规格方向一致：登记必填部门×科目、`occupy(deptId, subjectId, biz_type=AWARD, bizId=awardId)`。**科目维度被正确传入 `occupyCmd.setSubjectId(...)`** —— 运行态 3e 证明 R1 控制单元（部门×科目×月）在 D9 锚点路径成立。

**缺口（P2b-3/P2b-4 根因）**：`onRejected()` 仅 `setStatus(REJECTED)`，未按 `biz_type=AWARD` 释放占用；`updateAwardItems`+重提路径也无"先释放旧占用"动作 → 提交即占变成"每次提交都净增占用"。规格 §6 Q1-A 明文"**释放点=定标作废/驳回**"，释放侧完全缺失。

### 2.2 OrderServiceImpl L747 分流

- 入口校验放宽 ✅（`contractId/items` 必填，`applyId` 可空）；无申请明细必填 `purchaseUnit` ✅。
- 申请头闸门/余量闸仅在有 `applyId`/`applyItemId` 时执行 ✅；合同额度闸（`deductAvailable` 条件更新 + version 兜底重试 1 次）对无申请订单**真实生效**（5c 实测拒绝且不扣额度）✅。
- **P2b-5 根因**：`changeOrder` 的预算增量分支只从 `order.getApplyId()` 回填 `deptId/subjectId`（`OrderServiceImpl.java:489-505`）；无申请来源订单 `applyId=null` → `occupyCmd.deptId=null` → `if (occupyCmd.getDeptId() != null)` 整体跳过 → **增量无校验、无占用、无升级**。而 `insertOrder` 无条件写 `budget_occupied=Σ金额`，导致字段与预算系统脱钩（P2b-6）。
- 可用锚点其实存在：D9 订单的合同带 `award_id` → `award.dept_id/subject_id`，或合同自身的 `subject_id`，均可回填；实现未接。

### 2.3 预算不足直接拦截的设计偏离（报告 §4）

**评估：偏离可接受，但必须落规格。** 实测行为语义正确（3000 + 明确指引、零占用、不产生残留审批任务）；不改动 P3 已验证的 `BudgetApprovalHandler` 放行链路、控制回归面，是合理的工程取舍。风险点：规格 §3.3 与 §6 Q1 仍写"不足→BUDGET 升级审批（复用既有分支）"，两处文档不一致，后续验收/回归会按旧口径判不符 → 建议规格补记"Q1 修订：线下定标不足=直接拦截"。

---

## 3. 问题清单（分级，带证据）

### P1（须修）

**P2b-3【后端·P1】线下定标 AWARD 驳回不释放预算占用，且无作废端点 → 永久假占用**
- 规格 §6 Q1-A："释放点=定标作废/驳回"。实现：`AwardServiceImpl.onRejected()` 只置状态。
- 复现：award 2103007756356349953 submit（占 10560，log `占用 10560, balance 0→10560`）→ `POST /approvals/tasks/{id}/reject` 成功 → `award.status=2(REJECTED)`，`budget_line.used` **仍 10560**，无 RELEASE 流水；`AwardController` 无 void/close 端点。
- 影响：驳回的线下定标占用**永久冻结月度预算额度**（与 P3 已修 #36"申请作废假占用"同性质）。

**P2b-4【后端·P1】驳回后调整重提 → 预算重复叠加占用（双倍冻结）**
- 复现：同一 award 驳回后 `PUT /api/v1/awards/{id}/items`（明细 5280 元）→ `submit` → `used 10560→15840`，`budget_occupy_log` 出现**两笔 OCCUPY 同 bizId**（10560 + 5280）。
- 正确语义：释放旧占用（−10560）后新占 5280（终态 used=5280）。
- 注：`Σ(log)==used` 守恒**仍成立**（因为都如实记账）——这说明"守恒零差异"不能替代业务正确性断言，QA 后续对此类路径需断言**终态金额**而非仅守恒。

**P2b-5【后端·P1】无申请来源（D9/D2 之外的 L747 直连）订单变更增额完全绕过预算硬控**
- 复现：D9 订单（award 锚点占用 10560）下单 2112 → `POST /orders/{id}/change` `newQty 2→50`（增额 50688）→ **200 成功**：`budget_occupied 2112→52800`，`budget_line.used` 不变（10560），无 BUDGET 升级任务。
- 根因：`OrderServiceImpl.changeOrder` 预算增量分支仅从 `order.getApplyId()` 回填部门/科目，无申请来源 → `deptId=null` → 占用分支整体跳过。
- 与 PRD §6.4.3 月度硬控、P3 #47（变更加量被预算拦截→生成升级任务）意图相悖；L747"必须保留预算占用"在变更后失真。合同额度闸兜不住（合同额度手填，可远大于预算——本次正是手动放大合同额度后暴露）。
- 建议：无申请来源订单的 `occupyCmd` 从 `contract.award_id → award.dept_id/subject_id`（或 `contract.subject_id`）回填，增量走 occupy/不足升级。

### P2（建议修）

**P2b-6【后端·P2】无申请来源订单 `budget_occupied` 落订单金额但无任何预算占用流水（字段语义失真）**
- 证据：D9 下单后 `purchase_order.budget_occupied=2112`，而 `biz_type=ORDER` 的 `budget_occupy_log` 为 **0 条**；取消时 `occupiedTotal(ORDER,id)=0` → 释放逻辑与字段脱节。
- 影响：L747"必须保留预算占用"的字段承诺与 DB 实际不符；台账/审计以该字段口径会得出错误执行率。P3 轮 QA2-07 同类问题在 P2b 新链路上的扩散。
- 建议：D9 订单要么真正按 award→order 转移（补 ORDER 流水），要么 `budget_occupied` 置 0 并在 VO 另给"预算锚点=award"展示字段。

### P3（口径/测试）

**P2b-7【文档·P3】"预算不足直接拦截"为规格偏离，需落规格**
- 实测偏离行为本身正确（零占用 + 明确提示 + 无残留任务）；风险在于规格 §3.3/§6 Q1 与实现不一致，后续验收会误判。建议规格补记 Q1 修订（开发报告 §4 已自述，规格未同步）。

**P2b-8【测试·P3】P2bExtensionTest 未覆盖两个核心新行为**
- 缺：①线下定标**提交即占**断言（CP-11 核心，现仅有开发手工冒烟）；②驳回释放/重提语义（恰是 P2b-3/P2b-4 洞）；③无申请来源订单超合同额度拒绝。建议补 3 例。

---

## 4. 已验证 / 未验证边界

**已验证**：构建+90 例；D1/D2/D9/B3 正向全链（含金额对账与守恒）；线下定标 6 项正向 + 3 项边界（跨科目/无科目行/不足拦截）；L747 合同额度闸 3 项；D9 订单取消回冲；标准链路回归；401/404；P2b-1/P2b-2 修复确认；前端 4 页面字段静态核对；代码审读 3 处关键分支。

**未验证（如实列）**：
1. 独立寻源询价 → 发布 → 报价导入 → 比价 → **以 inquiryId 关联**定标 的完整报价链（两端点各自已验，中间链未串）。
2. 框架合同续签 `renew` 落 `renewed_from_id`（接口在，未实测）。
3. 前端渲染级/UI 交互验收（仅静态字段核对）。
4. 403 权限矩阵（种子仅 1 个 SUPER_ADMIN，无低权限账号，不可复现）。
5. MinIO 真实上传（降级验证）。
6. 多实例/真实 DB 行锁下的并发（沿用 P2/P3 轮结论，本轮未重跑）。

---

## 5. 路由建议

- **P2b-3 / P2b-4 → Engineer（software-engineer-4）**：`onRejected`/新增作废端点按 `biz_type=AWARD` 释放占用；重提前先释放旧占用。修复后 QA 需复验：驳回→used 回退、重提→终态 used=新金额（非叠加）。
- **P2b-5 → Engineer**：变更增量锚点回填（award/合同科目）+ 不足升级；复验 D9 订单加量被拦截并生成 BUDGET 任务。
- **P2b-6 → Engineer（可与 P2b-5 一并）**。
- **P2b-7 → 主理人/架构落规格**。**P2b-8 → Engineer 补单测**。
- 台账 D1/D2/D9/B3 **暂不建议清零**，待 P2b-3/4/5 修复复验通过后再清。

---

# 第 2 轮聚焦复验（P2b-3/4/5/6 修复验证）

> 验证对象：`develop @ 334a8dc`（`d3b94f6` P2b-3/4 → `0b851bf` P2b-5/6 → `334a8dc` 测试+16）。
> 方法：JDK17 + gradlew `clean build`；新建空库 `scm_qa_p2b2` 自动初始化；后端 `:18081` 运行态实测 + 直查 DB 对账；QA 自建预算行 `990402`(科目2/9月)/`990403`(科目1诱饵)/`990410`、SKU `991002`(BOX→PCS×12)、供应商+资质。
> 第 1 轮反例原样重跑为主；第 1 轮问题编号 P2b-3/4/5/6 对应验证。

## 3.0 结论先行

**IS_PASS = false（P2b-3 ✅ / P2b-4 ❌ / P2b-5 ⚠️部分 / P2b-6 ✅；新发现 P1×2：P2b-9、P2b-10）**

| 复验项 | 判定 | 依据 |
|---|---|---|
| 构建与单测（106 例） | ✅ 属实 | `clean build` 15s；JUnit XML 逐类核对 **20 类 / 106 例 / 0 失败 0 错误 0 跳过**（P2bBudgetLifecycleTest 8 + P2bExtensionTest 10 + OrderTripleCheckTest 12 + AwardSingleSupplierTest 4） |
| P2b-3 驳回释放 | ✅ **通过** | 提交即占 10560 → AWARD 驳回 → `used` 回退 0 + RELEASE 负向流水 `('0','10560.00'),('1','-10560.00')` |
| P2b-4 重提幂等（终态=新金额） | ❌ **失败（必现）** | 驳回→调整 5280→重提 → **`3002 预算行并发冲突` 100% 复现**（受控时序 + sleep 隔离非并发竞态），终态 `used=0`≠5280。根因 P2b-9 |
| P2b-5 变更硬控 | ⚠️ **部分修复** | ①有锚超额：拦截 + BUDGET 任务 payload **含 subjectId:2** + 变更回滚 ✅；②有锚余额足：ORDER OCCUPY 1056 + used +1056 ✅；**③无锚（D2 手填合同）变更增额 2→100 BOX(+103488) 静默成功** ❌（见 P2b-10） |
| P2b-6 占用落 ORDER 流水 | ✅ **通过** | 下单转移 `AWARD(2) RELEASE -2112` + `ORDER(3) OCCUPY 2112` 成对落账；变更增/减额均落 ORDER 流水；`budget_occupied` 与预算系统挂钩（2112→3168→2112）；守恒零差异 |
| 作废端点闭环 | ✅ 合规项全过 | 干净定标 void：3168→0 释放 + remark「作废：QA作废验证」留痕；重复 void → 3003 拒；已登记合同 void → 3003「请先终止合同（避免预算锚点悬空）」+ 状态不变；**VOIDED 经 API 序列化为 name `"VOIDED"`**（#33 契约）✅ |
| 回归（第 1 轮正向抽 4 项+标准链） | ✅ 全过 | D1 DAILY 拒询价 3003（文案正确）；独立寻源缺原因 4000(BR-07)/带原因成功（`source_type=OFFLINE, source_reason` 落库）；标准链 STANDARD submit→两级审批→APPROVED + 占用 88 落账；无申请下单合同额度扣减对账 5000−2112=2888、超额 105600 拒且 available 不变 |
| 守恒 | ✅ 每场景后全库零差异（但见 P2b-9 的"守恒盲区"警示） |

## 3.1 🔴 新发现 P1×2

### P2b-9【后端·🔴】`sumBizOccupied` 对 RELEASE 双重取反——有释放历史的单据，占用余额计算翻倍（释放/作废/重提全链被污染）

**根因**：`BudgetOccupyLogMapper.sumBizOccupied`（`BudgetOccupyLogMapper.java:20-23`）对 `action IN (1,2)` 取 `-amount`，但 `BudgetOccupyServiceImpl.release()` 写 RELEASE 流水时已 `take.negate()`（amount 本身为负）→ **双重取反**。任何 `occupiedTotal/releaseAwardOccupation` 对存在 RELEASE 历史的 bizId 都返回「净占用+|累计释放|」的翻倍值。

**三重运行态实锤**：

1. **驳回后重提 100% 失败（P2b-4 验证 FAILED）**：驳回释放 10560 后重提，`releaseAwardOccupation` 计算出 `occupiedTotal=21120(=10560×2)` → 释放 CAS `used 0−21120≥0` 不成立 → `3002 预算行并发冲突`（误导性文案）。应用日志铁证：`changeUsedAmount 参数: -21120.00, 990402, version=2 → Updates: 0`。
2. **驳回后作废吞掉同行其他业务占用 + 对方永久卡死**：定标 A（占 10560→驳回释放）+ 定标 B（占 21120）同行。`void A` 成功却按 21120 释放 → `used 21120→0`，**B 的真实占用被 A 全额吞掉**（A 流水净额 −21120，出现"负占用"）；随后 `void B` 按真实 21120 释放撞 `used−21120≥0` 守卫 → 3002 **永久卡死，无法释放/核销**。
3. **订单取消多释放**（第三方操作在我的 18081 实例上触发，见 3.3 环境备注）：ORDER 净占用 3168（转移 3168+增额 1056−减额 1056），取消却释放 **4224(=3168+1056)**——减额 RELEASE −1056 被 `sumBizOccupied` 回加为 +1056。

**影响面**：`releaseAwardOccupation`（定标驳回/作废/重提）、订单取消释放、变更减额后再次释放——凡"释放后再按余额释放"的路径全部翻倍。**守恒校验盲区**：Σlog==used 仍成立但语义已腐坏（负占用对冲正占用），现有 `conservation` 对账无法发现，需按 bizId 净额≥0 校验补充。
**修复方向**（供工程参考）：`sumBizOccupied` 改 `action=1 THEN amount`（RELEASE 日志已带符号，不取反；WRITE_OFF 日志为正数保留 `-amount`）；或统一 release 写正数并由 SQL 取反——二者取一，不可共存。修复后需回归 `BudgetOccupyConcurrencyTest` 8 例 + `P2bBudgetLifecycleTest` 8 例（其 mock 亦按此签名）。

### P2b-10【后端·🔴】无锚（D2 手填合同）订单变更增额仍静默绕过预算硬控——"4000 拦截"分支实为死代码，P2b-5 仅修复有锚场景

- 运行态复现：D2 手填合同（`award_id=NULL`）→ 无申请下单（`budget_occupied=0`，P2b-6 语义）→ 放开合同额度闸 → `POST /orders/{id}/change` 2→100 BOX（**+103488 元**）→ **200 成功**：qty 已改、`used` 纹丝不动、`budget_occupied` 仍 0、无任何 BUDGET 任务。预算硬控被完全绕过。
- 根因：`OrderServiceImpl.changeOrder` 的预算对冲块（含 `buildChangeOccupyCmd` 返回 null → 4000 硬控拦截分支）整体包在 `if (order.getBudgetOccupied() != null && order.getBudgetOccupied().compareTo(BigDecimal.ZERO) > 0)` 内——无锚订单 occupied=0 **根本进不了该块**；有锚订单则锚链必命中，`occupyCmd==null` 分支实际不可达（死代码）。
- 单测给出虚假信心：`P2bBudgetLifecycleTest.changeOrder_noAnchor_hardBlocked` 通过**手工 mock `budgetOccupied>0`** 构造无锚场景，与真实链路（P2b-6 将无锚订单 occupied 置 0）脱节——单测绿但生产行为未修复。
- 修复方向：锚点缺失拦截应放在 `budgetOccupied>0` 门**之外**（增额时无论 occupied 是否为 0 都必须先解析锚点；解析失败→4000 硬控），并把 `changeOrder_noAnchor_hardBlocked` 改为不 mock occupied 的真实形态。

## 3.2 代码审读结论（复验点 6）

- `buildChangeOccupyCmd`（`OrderServiceImpl.java:597-626`）：两级锚链 `order.applyId → apply.dept/subject/expectedDate`；`contract.award_id → award.dept/subject` 实现正确，**对有锚订单无绕过口**；减额路径不经过锚点解析（直接按 `occupiedTotal(ORDER)` 释放），**未被误拦**（运行态 ②b 实证 RELEASE -1056 成功）。
- `consumeApprovedBudgetUpgrade`（`OrderServiceImpl.java:682-712`）：去 applyId 门**安全**——匹配条件仍为 `bizType='BUDGET' + bizId=orderId + status=APPROVED + payload.orderChange=true + orderId 匹配 + amount 精确匹配 + consumed 未消费`，不会误消费其他 bizType 任务；一次消费置 `consumed=true` 防重放。✅
- `BudgetApprovalHandler` 升级 force 占用补 `subjectId`（payload→cmd）✅（与 5d-① 任务 payload 实测一致）。

## 3.3 环境备注（如实记录）

验证期间（QA 批次 B/C 之后）检测到**非 QA 操作**在我的 `:18081`/`scm_qa_p2b2` 实例上执行（流水 biz_id 时间戳晚于 QA 全部批次：线下定标 10560 → 下单转移 3168 → 变更 → **订单取消释放 4224** 等）。该操作恰好构成 P2b-9 的第 3 个独立实锤（订单取消多释放 1056），但会造成实例状态污染——本报告所有判定均基于 QA 自有批次输出（过程输出已完整留存）+ 当时 DB 快照，未受污染影响。

## 3.4 第 2 轮结论

- **P2b-3 / P2b-6 修复确认有效**（运行态实测 + 守恒）；作废端点合规项全过。
- **P2b-4 修复无效**（P2b-9 连带：重提 100% 必现 3002，终态幂等不可达）。
- **P2b-5 部分修复**（有锚三态全过；无锚场景宣称的 4000 硬控不成立，见 P2b-10）。
- 新增 **P1×2（P2b-9 / P2b-10）**，均为修复引入或修复未覆盖的预算硬控核心缺陷，建议修复后做第 3 轮聚焦回归（重提幂等 / 驳回后作废 / 无锚变更拦截 / 订单取消释放回冲）。

---

# 第 2 轮聚焦复验（P2b-3/4/5/6 修复验证 · `334a8dc`）

> 验证对象：`develop @ 334a8dc`（`d3b94f6` P2b-3/4 生命周期闭环 → `0b851bf` P2b-5/6 锚点回填+ORDER 流水 → `334a8dc` P2b-8 专项单测 +6）。
> 方法：`clean build`（JDK17 wrapper）+ **新建空库 `scm_qa_p2b2` 自动初始化** + 后端 `:18081` 运行态 HTTP 实测 + 直查 DB/SQL 日志对账；第 1 轮反例（3h/3i/5d）原样重跑 + 终态金额断言。未改源码、未 push。

## 3.0 结论先行

**IS_PASS = false（第 1 轮 P1 修复 3 过 2 不过；新增 P1×2 + P3×2）**

| 复验点 | 结论 | 级别 |
|---|---|---|
| 构建 + 单测 106 例 | ✅ 20 类 / 106 例 / 0F 0E 0S（QA 逐类解析 JUnit XML；P2bBudgetLifecycleTest 8、P2bExtensionTest 10、OrderTripleCheckTest 12） | ✅ |
| ★P2b-3 驳回释放（3h 重跑） | ✅ submit 占 10560 → AWARD 驳回 → `used` **回退 0** + RELEASE −10560 负向流水，守恒零差异 | ✅ |
| 作废端点闭环 | ✅ 释放+`VOIDED`+remark 留痕；重复作废 3003；**有合同作废 3003 拒绝**（防锚点悬空守卫生效）；API 序列化 `name="VOIDED"` | ✅ |
| ★P2b-4 重提覆盖式幂等（3i 重跑） | ❌ **驳回后重提永远 3002「预算行并发冲突」**——覆盖式释放试图释放 **21120**（真实余额 0）→ CAS 守卫失败。**运行态重提链路完全不可用**（→ P2b-9 形态①） | 🔴 |
| ★P2b-5① 有锚超额 → 拦截+BUDGET 升级 | ✅ 拦截 + 任务 payload **含 `subjectId:2`**；升级通过 force 占用落锚点科目行（990402，跨科目诱饵行 990403 未被吃）；重提消费放行标记（`consumed:true`）无重复计账 | ✅ |
| ★P2b-5② 有锚余额足 | ✅ 变更 +1056 → ORDER 占用流水、`budget_occupied` 2112→3168、`used` 10560→11616 | ✅ |
| ★P2b-5③ 无锚（D2 手填合同）变更 | ❌ **未拦截**：+2640 静默成功（仅走合同额度），无校验/无占用/无升级任务（→ P2b-10） | 🔴 |
| P2b-6 ORDER 流水 | ✅ 下单转移落 `biz_type=ORDER` 流水；无锚订单 `budget_occupied=0`；**但减额后取消超额释放**（→ P2b-9 形态②） | ⚠️ |
| 回归 D1/D9/STANDARD | ✅ D1 拒询价 3003；D9 缺原因 4000/带原因成功；STANDARD 带科目正常占用（**新硬要求：申请必须填预算科目**，QA2-01 修复生效） | ✅ |
| R2 单中标回归 | ✅ 该逻辑未触碰（`singleSupplierOf` 无 diff）+ `AwardSingleSupplierTest` 4 例全绿 | ✅ |
| 守恒（Σlog==used，signed 口径） | ✅ 全程零差异；**但 biz 级口径（Σ某单占用==其实际占用）被 P2b-9 破坏** | ⚠️ |

## 3.1 🔴 P2b-9【P1】RELEASE 流水落账负值 × `sumBizOccupied` 取负口径 → 已释放占用被"反向加倍"，重提永久失败 + 取消超额释放

**记账口径自相矛盾（三处证据）**：
- `BudgetOccupyServiceImpl.release()` **:181**：`writeLog(..., BudgetAction.RELEASE, take.negate(), ...)` —— 释放流水 amount 落**负值**（实测 `action=1, amount=-10560.00`）；
- `BudgetOccupyServiceImpl.writeOff()` **:229**：`writeLog(..., BudgetAction.WRITE_OFF, take, ...)` —— 核销流水落**正值**（同表格两种约定并存）；
- `BudgetOccupyLogMapper.sumBizOccupied` **:19-25**（`occupiedTotal` :370 同口径）：`SUM(CASE WHEN action=0 THEN amount WHEN action IN (1,2) THEN -amount ...)` —— 按"1/2 落正数"假设**再取负**。

→ 对任何"曾发生过 RELEASE"的 biz：`occupiedTotal = 占用 + |释放|`（应为 占用 − 释放）。实测：占 10560 + 释 10560 → **sumBizOccupied=21120（真实 0）**。

**形态①（阻断，3i 反例运行态铁证）**：线下定标驳回（used 已回 0）→ 改明细重提 → `submit()` 的覆盖式释放 `releaseAwardOccupation` 算出 21120 → `updateUsedWithRetry(line, -21120)` 被 `changeUsedAmount` 的守卫 `AND used_amount + #{delta} >= 0`（BudgetLineMapper :60）拒绝（used=0）→ 重试同因失败 → **3002**。SQL 日志铁证：`UPDATE budget_line ... WHERE id=990402 AND version=2 ... 参数: -21120.00, 990402, 2, -21120.00`。**重试两次均 3002，重提链路永久不可用**（单测 `awardResubmit_finalAmountNotDoubled` 绿是 mock 未走真实 CAS/落账的假象）。

**形态②（静默腐蚀）**：订单 3BOX（ORDER 占 3168）→ 减额变更至 2BOX（RELEASE −1056）→ 取消：`occupiedTotal(ORDER)=4224`（真实 2112）→ 取消释放 **4224，超额 2112**。因同行还有 AWARD 锚点剩余占用（7392），超额部分直接吃掉**其他 biz 的 used 承载**——`Σ(各 biz occupiedTotal) > used_amount`，后续该行任意 biz 的释放/核销都可能触发形态①或继续腐蚀。两形态同一根因，按场景分别表现为"崩溃"或"静默错账"。

**修复建议**：统一为"1/2 落正数、查询取负"口径——`release()` :181 改 `writeLog(..., take, ...)`；并评估存量 RELEASE 负值数据（新库无存量、老库 3307 有，需迁移脚本翻正）；修复后必须补**真实 DB 集成测试**（见 P2b-12）。

## 3.2 🔴 P2b-10【P1】无锚（D2 手填合同）订单变更增额绕过预算闸——`budgetOccupied>0` 条件短路整个闸门

- `OrderServiceImpl.java:486`：预算闸整体包在 `if (order.getBudgetOccupied() != null && budgetOccupied > 0)` 内；D2 无锚订单按 P2b-6 设计 `budget_occupied=0`（:767-768）→ **变更增额连"无锚 4000 硬控拦截"都到不了**，直接只走合同额度校验。
- 运行态复现：D2 手填合同（无 awardId/无申请）下单（`budget_occupied=0`）→ 变更 2→5 BOX（+2640）→ **200 成功**，`budget_occupied` 仍 0、无 BUDGET 任务、无预算校验。任务书预期"③无锚 → 4000 硬控拦截"未达成。
- `OrderTripleCheckTest.changeOrder_noAnchor_hardBlocked` 的场景是 `budgetOccupied=100` + 锚数据丢失（:563），未覆盖 `budgetOccupied=0` 分支——与 P2b-9 同属"单测绿、运行态炸"。
- 修复建议：`amountDelta>0` 且 `buildChangeOccupyCmd` 无锚时，无论 budgetOccupied 是否为 0 一律 4000（或强制走线下补录通道）；`budgetOccupied=0` 的减额/对冲场景另行放行。

## 3.3 🟡 P3 级新发现

- **P2b-11【P3】余额守卫失败被误报为"并发冲突"**：`updateUsedWithRetry`（:467-483）对 CAS 失败不区分 `version` 冲突与 `used_amount+delta>=0` 守卫失败，统一抛 3002「预算行并发冲突」——本轮 P2b-9 的真实根因（余额守卫）因此被误导性报文掩盖，排障成本高。建议守卫失败单独报"预算余额不足/非法释放"。
- **P2b-12【P3】mock 单测无法暴露记账口径缺陷**：P2bBudgetLifecycleTest 8 例断言质量良好（cmd captor/终态金额/consumed 标记），但全部 mock `budgetOccupyService`，真实落账链（CAS 守卫+流水符号+sum 口径）零覆盖——P2b-9 的两条 P1 路径单测全绿。建议为"释放→再占用/再释放"生命周期补 1-2 条真实 DB 集成测试（Testcontainers/H2 或 @SpringBootTest+测试库）。

## 3.2bis 已验证通过明细（关键 DB 对账数字）

| 场景 | 证据 |
|---|---|
| P2b-3 驳回释放 | submit 后 `used(990402)=10560`；reject 后 `=0`；流水 `[OCCUPY +10560, RELEASE −10560]`；守恒零差异 |
| 重提终态（在 P2b-9 修复前不可达） | —（形态①阻断，见 3.1） |
| 作废闭环 | void 后 `used 10560→0`、`status=3(VOIDED)`、remark 留痕；重复作废 3003；**有合同作废 3003「请先终止合同（避免预算锚点悬空）」**；`GET /awards/{id}` 返回 `"status":"VOIDED"`（name 契约） |
| P2b-5① 升级链 | 拦截报文含「已生成 BUDGET 升级审批」；payload `{"orderChange":true,"orderId":…,"deptId":1,"subjectId":2,"amount":3168,"overAmount":2668,"balance":500,"budgetStatus":2}`；审批通过后 `used 11616→14784`（force +3168 落 990402，科目1 诱饵行 used 保持 0）；重提成功 `consumed:true`，`budget_occupied=6336`（=2112+4224，无重复计账） |
| P2b-5② 正常增额 | +1056 → `budget_occupied 2112→3168`、`used 10560→11616` |
| P2b-6 | 下单转移落 `biz_type=ORDER` 流水（+3168）；无锚订单 `budget_occupied=0.00` |
| 回归 | D1 `3003 日常/框架采购为免比价链路…不允许发起询价`；D9 缺原因 `4000 独立寻源必须填写寻源原因（BR-07）`；STANDARD 带科目 submit 成功 `budget_status=1` |

## 3.4 未验/边界

- 二次驳回事务回滚探针未完成（无 PENDING 样本可用）；理论上与形态①同根因（release 抛异常连累审批事务）。
- `writeOff` 后再查 `occupiedTotal` 场景未探（核销落正值、口径一致，理论安全）。
- 前端 UI 未回归（本轮聚焦后端生命周期）；MinIO 真实上传仍降级。
- 「重提终态=新金额」的**正向断言**在 P2b-9 修复前无法运行态验证——修复后需回归：驳回→改明细→重提应成功且 `used==新金额`。

---

# 第 3 轮收口回归（P2b-9/10 修复验证 · `4971ebd`）

> 验证对象：`develop @ 4971ebd`（`2ad7253` P2b-9/10 根因修复 → `4971ebd` 迁移脚本合并修正）。
> 方法：**因工作区存在他人未提交的半成品改动（见 4.5），本轮改用 `git worktree` 检出 `4971ebd` 隔离构建**（JDK17 wrapper `clean build`），新建空库 `scm_qa_p2b3` 自动初始化 + 后端 `:18081` 运行态实测 + 直查 DB 对账。未改源码、未 push。

## 4.0 结论先行

**IS_PASS = true（四条验收锚全过；P2b-9/10 修复运行态证实生效；残余仅 P3 级 1 项 + 2 项流程提示）**

| # | 验收锚 | 结论 | 关键 DB 对账数字 |
|---|---|---|---|
| 1 | ★重提链路复活 | ✅ | submit 占 10560 → 驳回 `used=0`（RELEASE **+10560 落正**）→ 改明细 5280 → 重提**成功**，**终态 used==5280**（非叠加）；流水 `OCCUPY+10560 / RELEASE+10560 / OCCUPY+5280` |
| 2 | ★取消/减额精确释放 | ✅ | 同行双占用（awX 10560 + awY 5280，used=15840）→ 下单 3BOX 转移（净0）→ 减额 −1056（used 14784）→ 取消**精确释放 2112**（used **12672** = awX 剩 7392 + awY 5280）；**awY 占用分毫未动**（occupiedTotal(AWARD) awX=7392 / awY=5280）——第 2 轮"超额释放吃同行"缺陷确认修复 |
| 3 | ★无锚变更拦截 | ✅ | D2 手填合同订单（budget_occupied=0）变更增额 → **`3000 变更增额无预算锚点…预算硬控拦截`**（明细回滚）；减额放行。`budgetOccupied=0` 分支已纳入硬控（P2b-10 修复生效） |
| 4 | ★全库守恒 + writeOff 抽查 | ✅ | 新口径（used == ΣOCCUPY − ΣRELEASE）**全库零差异**（5 轮混合动作后复检）；结算审批通过 → WRITE_OFF 流水 `action=2, amount=10560（落正）`，**used 核销后不变 10560** |
| 5 | 单测复跑 | ✅ | 隔离 worktree `clean build` 绿；**20 类 / 106 例 / 0F 0E 0S**（QA 逐类解析 JUnit XML） |
| 6 | 迁移脚本核验 | ✅ | `p2b_migration.sql` 134 行 = 原版 4 组 ALTER（inquiry×2/award×2/apply×2/contract×1，共 7 列）**完整保留** + P2b-9 翻正段；翻正行为实测：模拟历史负值行 −777 → 跑脚本 → **+777**；**二次执行 no-op（幂等）** |

## 4.1 P2b-9 修复证实（口径定稿：写侧一律正数、查询侧取负）

- `release()` / `transfer()` 落正数（`writeLog(..., RELEASE, take, ...)`），`sumBizOccupied`/`sumNetOccupiedByLine` 对 action∈(1,2) 取负——读写自洽，第 2 轮"双重取反"三实锤场景（驳回重提/作废吞占/取消多释放）运行态全部消除。
- **注（口径分歧提示）**：`BudgetOccupyLogMapper` javadoc 与 `release()` 内注释分别描述为"查询取负/写侧禁负"与旧"方案A"表述——以**实际代码为准（写正查负）**，建议工程师统一两处注释措辞，避免下一个读者再被误导（P3 级文档问题，不阻断）。
- ⚠️ **QA 守恒口径同步**：本报告历史章节的守恒 SQL（`Σlog==used` 按 signed 直接相加）是**旧口径**；本轮起 QA 对账 SQL 已更新为 `used == Σ(action=0) − Σ(action=1)`（WRITE_OFF/ADJUST 不参与行级恒等式）。

## 4.2 P2b-10 修复证实（锚点解析外置）

- `changeOrder` 锚点解析已提到 `occupied>0` 门之外（OrderServiceImpl.java:485-508）：增额必经 `buildChangeOccupyCmd` → 无锚 3000 拦截；有锚不足仍走 BUDGET 升级（#47 模式）。第 2 轮 P2b-10 的 `budgetOccupied=0` 静默绕过分支运行态消除。
- 拦截码为 **3000（BIZ_ERROR）而非字面 4000（PARAM_ERROR）**——与系统"业务规则拒绝"惯例一致（超额拦截同为 3000），语义正确，不判缺陷；如产品要求统一 4000 再调整。

## 4.3 残余（P3 级）

- **P2b-13【P3】无锚订单减额变更致 `budget_occupied` 落负值**：D2 无锚订单（occupied=0）减额放行时 `newOccupied = currentOccupied + amountDelta = −1056`（无 `max(0, …)` 下界保护）。运行态实测：减额后 `budget_occupied=-1056.00`。无资金风险（该字段对无锚订单不参与闸门），但字段语义失真、报表可能出负数。建议 `newOccupied` 做下界 0 保护（锚点订单有真实占用不受影响）。
- **P2b-12（部分改善，未完全闭环）**：`changeOrder_noAnchor_hardBlocked` 已改真实链路形态（occupied=0、无手工构造拦截），但 `P2bBudgetLifecycleTest` 的"释放→再占用"生命周期仍 mock `budgetOccupyService`——sum 口径类缺陷 mock 依然测不出（本轮由运行态实测弥补）。真实 DB 集成测试建议仍登记为后续项。

## 4.4 本轮环境与边界

- 隔离 worktree `/tmp/qa3/wt-4971ebd`（构建产物）；运行实例 `:18081`（scm_qa_p2b3）保留。翻正验证数据（biz_id=999888）已清理。
- 未验：前端 UI（本轮聚焦后端）；MinIO 仍降级；scm_qa_p2b2 旧库翻正演练被外部清理（翻正行为已在 p2b3 实测覆盖）。

## 4.5 ⚠️ 流程风险提示（非代码缺陷，需主理人跟进）

**主仓库工作区存在未提交的半成品改动（非 QA 产出、QA 未动）**：`BudgetOccupyServiceImpl.java` 11 行未提交 diff + 未跟踪 `scripts/sql/p2b9_release_sign_migration.sql`——内容为把 P2b-9 从已提交的"写侧落正数"口径改回"写侧落负数（方案A）"，但**查询 SQL 与 writeOff 未同步**，若按当前工作区构建部署，P2b-9 双重取反将原样复活。本轮已用 worktree 隔离规避；**请确认该改动归属并处理（提交完整方案或丢弃）后再统一 push 收口**。
