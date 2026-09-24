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
