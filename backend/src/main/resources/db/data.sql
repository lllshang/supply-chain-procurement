-- ============================================================
-- 供应链采购协同中台 —— 种子数据（幂等，可重复执行）
-- 默认管理员账号：admin / admin123（BCrypt，首次登录请修改）
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------- 部门（根） ----------------
INSERT IGNORE INTO sys_dept (id, parent_id, dept_name, data_scope, sort, status, created_at, updated_at, deleted)
VALUES (1, 0, '采购协同中台', 3, 1, 0, NOW(), NOW(), 0);

-- ---------------- 角色 ----------------
INSERT IGNORE INTO sys_role (id, role_code, role_name, remark, status, created_at, updated_at, deleted)
VALUES (1, 'SUPER_ADMIN', '超级管理员', '系统内置超级管理员', 0, NOW(), NOW(), 0);

-- ---------------- 菜单（12 个一级模块） ----------------
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort, status, created_at, updated_at, deleted) VALUES
(1, 0, '系统管理',   1, '/system',    'layout',        'setting',  'system:view',    1,  0, NOW(), NOW(), 0),
(2, 0, '商品库管理', 1, '/catalog',   'catalog/index','box',      'catalog:view',   2,  0, NOW(), NOW(), 0),
(3, 0, '供应商管理', 1, '/supplier',  'supplier/index','shop',     'supplier:view',  3,  0, NOW(), NOW(), 0),
(4, 0, '采购管理',   1, '/purchase',  'purchase/index','shopping','purchase:view',  4,  0, NOW(), NOW(), 0),
(5, 0, '预算管理',   1, '/budget',    'budget/index',  'money',    'budget:view',    5,  0, NOW(), NOW(), 0),
(6, 0, '合同管理',   1, '/contract',  'contract/index','document','contract:view',  6,  0, NOW(), NOW(), 0),
(7, 0, '报价定标',   1, '/quotation', 'quotation/index','trend',   'quotation:view', 7,  0, NOW(), NOW(), 0),
(8, 0, '订单与验收', 1, '/order',     'order/index',   'list',     'order:view',     8,  0, NOW(), NOW(), 0),
(9, 0, '结算与付款', 1, '/settlement','settlement/index','credit-card','settlement:view',9,0, NOW(), NOW(), 0),
(10,0, '审批中心',   1, '/approval',  'approval/index', 'audit',    'approval:view',  10, 0, NOW(), NOW(), 0),
(11,0, '报表中心',   1, '/report',    'report/index',  'chart',    'report:view',    11, 0, NOW(), NOW(), 0),
(12,0, '基础设置',   1, '/setting',   'setting/index', 'tools',    'setting:view',   12, 0, NOW(), NOW(), 0);

-- ---------------- P1 二级菜单（商品库/供应商/预算，parent_id 指向一级菜单；幂等） ----------------
-- perms 同时含 read 与 write 键，RbacService.getUserPerms 汇总后供前端 v-permission 使用。
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort, status, created_at, updated_at, deleted) VALUES
-- 商品库管理（parent_id = 2）
(201, 2, '品类配置', 2, '/catalog/category',   'catalog/category/index',  'list',   'catalog:category:read,catalog:category:write', 1, 0, NOW(), NOW(), 0),
(202, 2, '单位配置', 2, '/catalog/unit',       'catalog/unit/index',      'scale-to-original', 'catalog:unit:read,catalog:unit:write', 2, 0, NOW(), NOW(), 0),
(203, 2, '规格配置', 2, '/catalog/spec',       'catalog/spec/index',      'operation','catalog:spec:read,catalog:spec:write',       3, 0, NOW(), NOW(), 0),
(204, 2, '价格规则', 2, '/catalog/price-rule', 'catalog/price/index',     'price-tag','catalog:price:read,catalog:price:write',     4, 0, NOW(), NOW(), 0),
(205, 2, '产品库',   2, '/catalog/product',    'catalog/product/index',   'goods',   'catalog:spu:read,catalog:spu:write',          5, 0, NOW(), NOW(), 0),
(206, 2, '产品导入', 2, '/catalog/import',     'catalog/import/index',    'upload',  'catalog:import',                              6, 0, NOW(), NOW(), 0),
-- 供应商管理（parent_id = 3）
(301, 3, '供应商分类', 2, '/supplier/category', 'supplier/category/index','share',   'supplier:read,supplier:write',                1, 0, NOW(), NOW(), 0),
(302, 3, '供应商档案', 2, '/supplier/list',    'supplier/list/index',     'shop',    'supplier:read,supplier:write',                2, 0, NOW(), NOW(), 0),
(303, 3, '产品绑定',   2, '/supplier/bind',    'supplier/bind/index',     'link',    'supplier:read,supplier:write',                3, 0, NOW(), NOW(), 0),
(304, 3, '资质管理',   2, '/supplier/qual',    'supplier/qual/index',     'medal',   'supplier:read,supplier:write',                4, 0, NOW(), NOW(), 0),
-- 预算管理（parent_id = 5）
(501, 5, '预算科目',   2, '/budget/subject', 'budget/subject/index', 'notebook','budget:subject:read,budget:subject:write', 1, 0, NOW(), NOW(), 0),
(502, 5, '预算项目',   2, '/budget/project', 'budget/project/index', 'folder',  'budget:project:read,budget:project:write', 2, 0, NOW(), NOW(), 0),
(503, 5, '年度预算导入', 2, '/budget/import','budget/import/index',  'upload',  'budget:import',                            3, 0, NOW(), NOW(), 0),
(504, 5, '预算台账',   2, '/budget/ledger',  'budget/ledger/index',  'money',   'budget:read',                              4, 0, NOW(), NOW(), 0);

-- ---------------- P2 二级菜单（采购主链路：采购/合同/订单/审批，对齐设计 §5 路由；幂等） ----------------
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort, status, created_at, updated_at, deleted) VALUES
-- 采购管理（parent_id = 4）
(401, 4, '采购申请', 2, '/purchase/apply',     'purchase/apply/index',     'list',   'purchase:apply:read,purchase:apply:write,purchase:apply:export', 1, 0, NOW(), NOW(), 0),
(402, 4, '常购清单', 2, '/purchase/frequent',  'purchase/frequent/index',  'goods',  'purchase:frequent:read,purchase:frequent:write',                 2, 0, NOW(), NOW(), 0),
(403, 4, '询价管理', 2, '/purchase/inquiry',   'purchase/inquiry/index',   'trend',  'purchase:inquiry:read,purchase:inquiry:write',                   3, 0, NOW(), NOW(), 0),
(404, 4, '报价管理', 2, '/purchase/quotation', 'purchase/quotation/index', 'upload', 'purchase:quotation:read,purchase:quotation:write',               4, 0, NOW(), NOW(), 0),
(405, 4, '比价定标', 2, '/purchase/award',     'purchase/award/index',     'medal',  'purchase:award:read,purchase:award:write,purchase:award:submit', 5, 0, NOW(), NOW(), 0),
-- 合同管理（parent_id = 6）
(601, 6, '合同台账', 2, '/contract/list', 'contract/list/index', 'document', 'contract:read,contract:write,contract:submit,contract:terminate', 1, 0, NOW(), NOW(), 0),
(602, 6, '到期预警', 2, '/contract/warn', 'contract/warn/index', 'audit',    'contract:read',                                                   2, 0, NOW(), NOW(), 0),
-- 订单与验收（parent_id = 8）
(801, 8, '采购订单', 2, '/order/list',   'order/list/index',   'list',  'order:read,order:write,order:change',                            1, 0, NOW(), NOW(), 0),
(802, 8, '到货验收', 2, '/order/arrival', 'order/arrival/index','box',   'arrival:read,arrival:write,arrival:confirm',                     2, 0, NOW(), NOW(), 0),
(803, 8, '入库台账', 2, '/order/ledger',  'order/ledger/index', 'money', 'arrival:read',                                                   3, 0, NOW(), NOW(), 0),
(804, 8, '履约调整', 2, '/order/adjust',  'order/adjust/index', 'tools', 'adjust:read,adjust:write',                                       4, 0, NOW(), NOW(), 0),
(805, 8, '服务考核', 2, '/order/assess',  'order/assess/index', 'audit', 'order:assess:read,order:assess:write',                           5, 0, NOW(), NOW(), 0),
-- 审批中心（parent_id = 10）
(1001,10, '待我审批', 2, '/approval/tasks', 'approval/tasks/index', 'audit', 'approval:read,approval:approve', 1, 0, NOW(), NOW(), 0);

-- ---------------- 管理员用户（密码 admin123，BCrypt $2a$10$） ----------------
INSERT IGNORE INTO sys_user (id, username, password_hash, nickname, main_dept_id, status, created_at, updated_at, deleted)
VALUES (1, 'admin', '$2a$10$pxjuW3BEyUH2EunQavbwWOTURXWJ/fLDpzVNrkKgjhsisXNhR8iwK', '管理员', 1, 0, NOW(), NOW(), 0);

-- ---------------- 用户-角色 ----------------
INSERT IGNORE INTO sys_user_role (id, user_id, role_id, created_at, updated_at, deleted)
VALUES (1, 1, 1, NOW(), NOW(), 0);

-- ---------------- 角色-菜单（超级管理员拥有全部菜单） ----------------
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, created_at, updated_at, deleted) VALUES
(1,  1, 1,  NOW(), NOW(), 0),
(2,  1, 2,  NOW(), NOW(), 0),
(3,  1, 3,  NOW(), NOW(), 0),
(4,  1, 4,  NOW(), NOW(), 0),
(5,  1, 5,  NOW(), NOW(), 0),
(6,  1, 6,  NOW(), NOW(), 0),
(7,  1, 7,  NOW(), NOW(), 0),
(8,  1, 8,  NOW(), NOW(), 0),
(9,  1, 9,  NOW(), NOW(), 0),
(10, 1, 10, NOW(), NOW(), 0),
(11, 1, 11, NOW(), NOW(), 0),
(12, 1, 12, NOW(), NOW(), 0),
-- P1 二级菜单授权（超级管理员）
(13, 1, 201, NOW(), NOW(), 0),
(14, 1, 202, NOW(), NOW(), 0),
(15, 1, 203, NOW(), NOW(), 0),
(16, 1, 204, NOW(), NOW(), 0),
(17, 1, 205, NOW(), NOW(), 0),
(18, 1, 206, NOW(), NOW(), 0),
(19, 1, 301, NOW(), NOW(), 0),
(20, 1, 302, NOW(), NOW(), 0),
(21, 1, 303, NOW(), NOW(), 0),
(22, 1, 304, NOW(), NOW(), 0),
(23, 1, 501, NOW(), NOW(), 0),
(24, 1, 502, NOW(), NOW(), 0),
(25, 1, 503, NOW(), NOW(), 0),
(26, 1, 504, NOW(), NOW(), 0),
-- P2 二级菜单授权（超级管理员）
(27, 1, 401, NOW(), NOW(), 0),
(28, 1, 402, NOW(), NOW(), 0),
(29, 1, 403, NOW(), NOW(), 0),
(30, 1, 404, NOW(), NOW(), 0),
(31, 1, 405, NOW(), NOW(), 0),
(32, 1, 601, NOW(), NOW(), 0),
(33, 1, 602, NOW(), NOW(), 0),
(34, 1, 801, NOW(), NOW(), 0),
(35, 1, 802, NOW(), NOW(), 0),
(36, 1, 803, NOW(), NOW(), 0),
(37, 1, 804, NOW(), NOW(), 0),
(39, 1, 805, NOW(), NOW(), 0),
(38, 1, 1001, NOW(), NOW(), 0);

-- ---------------- P1 计量单位字典（基础种子，幂等） ----------------
INSERT IGNORE INTO unit (id, code, name, status, created_at, updated_at, deleted) VALUES
(1, 'PCS', '个',   0, NOW(), NOW(), 0),
(2, 'BOX', '箱',   0, NOW(), NOW(), 0),
(3, 'KG',  '千克', 0, NOW(), NOW(), 0),
(4, 'M',   '米',   0, NOW(), NOW(), 0);

-- ---------------- P1 商品品类（示例三级树，幂等） ----------------
INSERT IGNORE INTO product_category (id, parent_id, level, code, name, tree_path, status, created_at, updated_at, deleted) VALUES
(1, 0, 1, 'CAT_L1_BASE', '原材料', '/1',   0, NOW(), NOW(), 0),
(2, 1, 2, 'CAT_L2_METAL', '金属',  '/1/2', 0, NOW(), NOW(), 0),
(3, 2, 3, 'CAT_L3_STEEL', '钢材',  '/1/2/3', 0, NOW(), NOW(), 0);

-- ---------------- P1 供应商分类（示例三级树，幂等） ----------------
INSERT IGNORE INTO supplier_category (id, parent_id, level, code, name, tree_path, status, created_at, updated_at, deleted) VALUES
(1, 0, 1, 'SUP_L1_MFG', '制造商', '/1',   0, NOW(), NOW(), 0),
(2, 1, 2, 'SUP_L2_METAL', '金属制品', '/1/2', 0, NOW(), NOW(), 0),
(3, 2, 3, 'SUP_L3_STEEL', '钢材制造', '/1/2/3', 0, NOW(), NOW(), 0);

-- ---------------- P1 预算科目（示例，幂等） ----------------
INSERT IGNORE INTO budget_subject (id, code, name, parent_id, subject_type, status, created_at, updated_at, deleted) VALUES
(1, 'BS_EXPENSE', '支出', 0, 1, 0, NOW(), NOW(), 0),
(2, 'BS_RAW',     '原材料费', 1, 1, 0, NOW(), NOW(), 0);

SET FOREIGN_KEY_CHECKS = 1;
