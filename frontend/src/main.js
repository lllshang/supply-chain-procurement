import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 全局主题层：对齐原型 scm.16u.cc 的设计令牌与组件观感（仅样式覆盖）
import '@/styles/theme.css'
import App from './App.vue'
import router from './router'
import { permission } from './directive/permission'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.directive('permission', permission)
app.mount('#app')
