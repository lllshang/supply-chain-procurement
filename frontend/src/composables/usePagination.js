import { ref } from 'vue'

/**
 * 统一分页逻辑（P1 前端设计 §3.3）。
 *
 * fetcher(params) 应返回统一响应体 R；本组合式从中取 data.records / data.total。
 *
 * @param {Function} fetcher 形如 (params) => request.get(url, { params })
 * @param {object}   options { size }
 * @returns {{ current, pageSize, total, loading, load, onCurrentChange, reset }}
 *   load() 返回当前页记录数组，调用方自行赋给列表。
 */
export function usePagination(fetcher, options = {}) {
  const current = ref(1)
  const pageSize = ref(options.size || 10)
  const total = ref(0)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await fetcher({ current: current.value, size: pageSize.value })
      const data = res?.data || {}
      total.value = data.total || 0
      return data.records || []
    } catch (e) {
      // 请求失败回退空列表（错误已由拦截器提示）
      total.value = 0
      return []
    } finally {
      loading.value = false
    }
  }

  function onCurrentChange(page) {
    current.value = page
    return load()
  }

  function reset() {
    current.value = 1
    return load()
  }

  return { current, pageSize, total, loading, load, onCurrentChange, reset }
}

export default usePagination
