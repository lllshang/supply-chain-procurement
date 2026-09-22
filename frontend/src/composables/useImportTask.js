import { onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 异步导入/导出任务轮询（P1 前端设计 §3.3 / §6 R6）。
 *
 * @param {Function} taskApi 形如 (taskId) => request.get(`.../tasks/${taskId}`)，返回统一响应体 R
 * @param {object}   options { interval 轮询间隔(ms)，默认 1500 }
 * @returns {{ running, task, start, stop }}
 *   task() = ImportTaskVO { taskId, status: RUNNING|SUCCESS|FAILED, totalRows, errorRows, errors, fileName }
 */
export function useImportTask(taskApi, options = {}) {
  const interval = options.interval || 1500
  const running = ref(false)
  const task = ref(null)
  let timer = null

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    running.value = false
  }

  function start(taskId) {
    stop()
    running.value = true
    task.value = { taskId, status: 'RUNNING' }
    timer = setInterval(async () => {
      try {
        const res = await taskApi(taskId)
        task.value = res?.data || task.value
        const status = task.value?.status
        if (status === 'SUCCESS' || status === 'FAILED') {
          stop()
          if (status === 'SUCCESS') {
            ElMessage.success('任务完成')
          } else {
            ElMessage.error('任务失败')
          }
        }
      } catch (e) {
        // 轮询异常则停止（错误已由拦截器提示）
        stop()
      }
    }, interval)
  }

  onUnmounted(stop)

  return { running, task, start, stop }
}

export default useImportTask
