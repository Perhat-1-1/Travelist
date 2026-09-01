<script setup>

import { ref } from 'vue'

const active = ref(0)

</script>

<template>
  <!-- 路由出口:当前路由对应的页面会渲染在这里 -->
  <router-view />

  <!-- 底部导航:route 模式下根据 to 自动高亮当前页 -->
  <van-tabbar route safe-area-inset-bottom v-model="active">
    <van-tabbar-item replace to="/" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item replace to="/plan" icon="map-marked">规划</van-tabbar-item>
    <van-tabbar-item replace to="/ai-chat" icon="chat-o">AI聊天</van-tabbar-item>
  </van-tabbar>
</template>
