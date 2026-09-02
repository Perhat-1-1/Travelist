import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import PlanView from '@/views/PlanView.vue'
import AiChatView from '@/views/AiChatView.vue'
import SpotDetailView from '@/views/SpotDetailView.vue'

const router = createRouter({
  // createWebHistory 使用 HTML5 History 模式(URL 不带 #)
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: '首页' },
    },
    {
      path: '/plan',
      name: 'plan',
      component: PlanView,
      meta: { title: '行程规划' },
    },
    {
      path: '/ai-chat',
      name: 'ai-chat',
      component: AiChatView,
      meta: { title: 'AI聊天' },
    },
    // 热门景点详情(可由首页进入;详情页提供「问问 AI」入口)
    {
      path: '/spot/:id',
      name: 'spot-detail',
      component: SpotDetailView,
      meta: { title: '景点详情' },
    },
  ],
})

// 切换路由时同步更新浏览器标签页标题
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · Travelist` : 'Travelist'
})

export default router
