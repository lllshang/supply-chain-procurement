import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * P4 来源单据 query 定位（设计 §4.3：审批中心跳转 → 列表页高亮行，最小改动方案）。
 *
 * 目标页现状均无 :id 详情子路由，采用"列表页 + query 定位高亮"：
 * - `locatedBizId`：route.query.bizId（字符串直传，零 Number() 转换，#28/#1 契约）；
 * - `rowClassName`：直接绑定 el-table 的 :row-class-name。
 */
export function useQueryLocate() {
  const route = useRoute()
  const locatedBizId = computed(() =>
    route.query && route.query.bizId ? String(route.query.bizId) : null
  )

  function rowClassName({ row }) {
    if (locatedBizId.value == null || row == null || row.id == null) {
      return ''
    }
    return String(row.id) === locatedBizId.value ? 'located-row' : ''
  }

  return { locatedBizId, rowClassName }
}

export default useQueryLocate
