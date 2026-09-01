import { createApp } from 'vue'
import Vant from 'vant'
import 'vant/lib/index.css'
// import './style.css'
import App from './App.vue'
import router from './router'

// 全局注册 Vant 与路由,之后所有页面都可以直接使用 <van-button> 等组件
createApp(App).use(Vant).use(router).mount('#app')
