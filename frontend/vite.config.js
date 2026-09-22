import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端 API 前缀：开发环境通过 Vite 代理转发到后端 8080

/**
 * Element Plus 组件依赖拓扑序（P3-19 分包依据）。
 *
 * 背景：element-plus 组件压缩后整包约 700kB，单块必然超过 Vite 默认 500kB 告警阈值。
 * 但**不能按字母或体积随意切分**——组件之间存在交叉 import（如 select→input、table→checkbox），
 * 随意切分会形成 chunk 环，运行时抛 `Cannot access 'x' before initialization`（已实测复现）。
 *
 * 做法：element-plus 的 es/components 依赖图是 DAG，本数组为其拓扑序
 * （依赖方在前、被依赖方在后，如 table 在前、input/button 在后）。
 * 将拓扑序**连续切成两段**，段间依赖必然单向（上段依赖下段），不会成环。
 *
 * ⚠️ 该顺序基于 element-plus 2.7.x 生成，升级大版本需重新生成（见 docs 说明或重新跑依赖分析）。
 */
const EP_TOPO_ORDER = [
  'affix', 'alert', 'anchor', 'anchor-link', 'aside', 'autocomplete', 'avatar', 'avatar-group',
  'backtop', 'breadcrumb', 'breadcrumb-item', 'calendar', 'card', 'carousel', 'carousel-item', 'cascader',
  'check-tag', 'checkbox-button', 'col', 'collapse', 'collapse-item', 'color-picker', 'container', 'countdown',
  'date-picker', 'descriptions', 'drawer', 'dropdown-item', 'dropdown-menu', 'footer', 'form-item', 'header',
  'image', 'infinite-scroll', 'input-otp', 'input-tag', 'link', 'loading', 'main', 'mention',
  'menu', 'menu-item', 'menu-item-group', 'message', 'message-box', 'notification', 'page-header', 'pagination',
  'popconfirm', 'radio-button', 'radio-group', 'rate', 'result', 'segmented', 'skeleton', 'slider',
  'space', 'splitter', 'splitter-panel', 'step', 'steps', 'sub-menu', 'switch', 'tab-pane',
  'table', 'table-column', 'table-v2', 'tabs', 'time-select', 'timeline', 'timeline-item', 'tour',
  'tour-step', 'transfer', 'tree-select', 'tree-v2', 'upload', 'watermark', 'cascader-panel', 'row',
  'color-picker-panel', 'statistic', 'date-picker-panel', 'descriptions-item', 'dialog', 'image-viewer', 'badge', 'divider',
  'popover', 'skeleton-item', 'input-number', 'empty', 'checkbox-group', 'select', 'tree', 'progress',
  'radio', 'time-picker', 'overlay', 'dropdown', 'select-v2', 'option', 'option-group', 'text',
  'checkbox', 'collapse-transition', 'roving-focus-group', 'button', 'button-group', 'tooltip', 'tag', 'virtual-list',
  'input', 'collection', 'config-provider', 'popper', 'scrollbar', 'icon', 'focus-trap', 'form',
  'slot', 'base'
]

/** 拓扑序切点：前 77 个组件归入 upper 桶，其余归入 lower 桶。 */
const EP_TOPO_CUT = 77

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/h5': { target: 'http://localhost:8080', changeOrigin: true }
    }
  },
  build: {
    // 分包消除 chunk > 500kB 告警（P3-19）：
    // 保持默认 chunkSizeWarningLimit=500 不变，靠真实拆包达标，而非抬高阈值。
    rollupOptions: {
      output: {
        manualChunks(id) {
          // 应用自身代码（含懒加载页面）不拆，交给 Vite/Rollup 按入口与动态 import 组织
          if (!id.includes('node_modules')) {
            return undefined
          }
          // 图标库（@element-plus/icons-vue）单独成块
          if (id.includes('node_modules/@element-plus/')) {
            return 'element-icons'
          }
          if (id.includes('node_modules/element-plus/')) {
            // 包入口（es/index.mjs 等 es/ 下顶层模块）保持在默认 chunk：
            // 它 import 全部组件桶，若与工具模块同处 core 会与组件桶形成 chunk 环。
            if (/\/es\/[^/]+\.mjs$/.test(id)) {
              return undefined
            }
            const marker = 'node_modules/element-plus/es/components/'
            const idx = id.indexOf(marker)
            if (idx >= 0) {
              const name = id.substring(idx + marker.length).split('/')[0]
              if (name) {
                const pos = EP_TOPO_ORDER.indexOf(name)
                // 未在拓扑序中登记（element-plus 版本变更）：归入 core，避免误切产生环
                if (pos < 0) {
                  return 'element-plus-core'
                }
                return pos < EP_TOPO_CUT ? 'element-plus-upper' : 'element-plus-lower'
              }
            }
            // 非组件部分（utils / hooks / directives / locale）
            return 'element-plus-core'
          }
          // Vue 运行时全家桶：vue / @vue/* / vue-router / pinia
          if (id.includes('node_modules/vue/')
            || id.includes('node_modules/@vue/')
            || id.includes('node_modules/vue-router/')
            || id.includes('node_modules/pinia/')) {
            return 'vue-vendor'
          }
          // 其余第三方依赖（axios、dayjs、lodash 等）
          return 'vendor'
        }
      }
    }
  }
})
