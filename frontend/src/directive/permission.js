import { useUserStore } from '@/store/user'

// v-permission 指令：依据用户权限键隐藏元素。
//   用法：<el-button v-permission="'catalog:spu:write'">新建</el-button>
//   也支持数组（任一命中即显示）：v-permission="['a:read','a:write']"
//
// 阶段一说明（P1 前端设计 §5）：后端当前为 hasAnyPerm 粗粒度门禁，
// 前端隐藏仅为 UX 优化，非安全边界。若当前用户尚未拿到 perms（如 /auth/me 未返回），
// 为避免误隐藏整片操作区，此时放行渲染（由后端做真实拦截）。

function allowed(perms, required) {
  if (!required) return true
  // 无 perms 信息时不隐藏（阶段一兜底）
  if (!Array.isArray(perms) || perms.length === 0) return true
  const need = Array.isArray(required) ? required : [required]
  if (need.length === 0) return true
  return need.some((p) => perms.includes(p))
}

export const permission = {
  mounted(el, binding) {
    const store = useUserStore()
    const perms = store.userInfo?.perms || []
    if (!allowed(perms, binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

export default permission
