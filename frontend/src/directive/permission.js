import { useUserStore } from '@/store/user'

// v-permission 指令：依据用户权限键隐藏元素。
//   用法：<el-button v-permission="'catalog:spu:write'">新建</el-button>
//   也支持数组（任一命中即显示）：v-permission="['a:read','a:write']"
//
// 阶段一说明（P1 前端设计 §5）：后端当前为 hasAnyPerm 粗粒度门禁，
// 前端隐藏仅为 UX 优化，非安全边界。若当前用户尚未拿到 perms（如 /auth/me 未返回），
// 为避免误隐藏整片操作区，此时放行渲染（由后端做真实拦截）。
//
// 【修复 #11】原实现仅在 mounted 一次性读取 perms，且直接移除 DOM 节点：
//   - 非响应式：SPA 导航后 perms 已就绪反而误隐藏；
//   - 不可逆：节点被移除后无法恢复。
// 现改为：记录原始 display、以 style.display 切换，并同时挂载 updated 钩子，
// 当组件重渲染（含 perms 变化）时重新判定，保证刷新与 SPA 导航行为一致。

/** 判定给定 required 是否被 perms 命中（perms 缺失时放行）。 */
function isAllowed(perms, required) {
  if (!required) return true
  // 无 perms 信息时不隐藏（阶段一兜底，真实拦截在后端）
  if (!Array.isArray(perms) || perms.length === 0) return true
  const need = Array.isArray(required) ? required : [required]
  if (need.length === 0) return true
  return need.some((p) => perms.includes(p))
}

/** 依据当前 store 权限状态应用显示/隐藏。 */
function apply(el, binding) {
  const store = useUserStore()
  const perms = store.userInfo?.perms || []
  if (isAllowed(perms, binding.value)) {
    el.style.display = el._permDisplay || ''
  } else {
    el.style.display = 'none'
  }
}

export const permission = {
  mounted(el, binding) {
    // 记录原始 display，便于权限恢复时还原
    el._permDisplay = el.style.display || ''
    apply(el, binding)
  },
  updated(el, binding) {
    apply(el, binding)
  }
}

export default permission
