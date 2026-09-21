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
(12, 1, 12, NOW(), NOW(), 0);

SET FOREIGN_KEY_CHECKS = 1;
