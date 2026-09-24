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
