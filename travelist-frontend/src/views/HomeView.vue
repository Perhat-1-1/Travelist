<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'

const router = useRouter()
const keyword = ref('')

// 首页轮播位
const banners = [
  { title: '大理 · 苍山洱海', desc: '风花雪月 6 日游', bg: 'linear-gradient(135deg, #1989fa, #39c5bb)' },
  { title: '张家界 · 武陵源', desc: '空中田园 4 日游', bg: 'linear-gradient(135deg, #07c160, #39c5bb)' },
  { title: '三亚 · 蜈支洲岛', desc: '海岛度假 5 日游', bg: 'linear-gradient(135deg, #ff976a, #ff7d00)' },
]

// 功能入口
const categories = [
  { icon: 'location-o', text: '景点' },
  { icon: 'star-o', text: '美食' },
  { icon: 'hotel-o', text: '酒店' },
  { icon: 'guide-o', text: '攻略' },
  { icon: 'cart-o', text: '门票' },
  { icon: 'friends-o', text: '结伴' },
  { icon: 'logistics', text: '自驾' },
  { icon: 'photo-o', text: '游记' },
]

// 热门目的地(来自后端;仅名称/标签/简介,不显示价格)
const spots = ref([])
const spotsError = ref(false)

const loadSpots = async () => {
  spotsError.value = false
  try {
    const res = await fetch('/api/spot/list')
    const body = await res.json()
    if (!res.ok || !body.success) {
      throw new Error(body?.message || `HTTP ${res.status}`)
    }
    spots.value = body.data ?? []
  } catch (err) {
    spotsError.value = true
    showToast('热门景点加载失败,请稍后重试')
  }
}

const goDetail = (spot) => {
  router.push(`/spot/${spot.id}`)
}

onMounted(loadSpots)

const onSearch = () => {
  if (!keyword.value.trim()) {
    showToast('请输入目的地、景点或美食')
    return
  }
  showToast(`搜索「${keyword.value.trim()}」`)
}
</script>

<template>
  <div class="page">
    <van-nav-bar title="Travelist" fixed placeholder />

    <!-- 公告栏 -->
    <van-notice-bar left-icon="volume-o" :scrollable="false">
      <van-swipe
        vertical
        class="notice-swipe"
        :autoplay="3000"
        :touchable="false"
        :show-indicators="false"
      >
        <van-swipe-item style="color: #f08332;">明月直入，无心可猜。</van-swipe-item>
        <van-swipe-item style="color: #f08332;">仙人抚我顶，结发受长生。</van-swipe-item>
        <van-swipe-item style="color: #f08332;">今人不见古时月，今月曾经照古人。</van-swipe-item>
      </van-swipe>
    </van-notice-bar>

    <!-- 搜索框 -->
    <van-search v-model="keyword" placeholder="搜索目的地、景点、美食" @search="onSearch" />

    <!-- 轮播 Banner -->
    <van-swipe class="banner" :autoplay="3000" indicator-color="#fff" lazy-render>
      <van-swipe-item v-for="b in banners" :key="b.title" :style="{ background: b.bg }">
        <p class="banner-title">{{ b.title }}</p>
        <p class="banner-desc">{{ b.desc }}</p>
      </van-swipe-item>
    </van-swipe>

    <!-- 功能入口 -->
    <van-grid :column-num="4" :border="false" class="category-grid">
      <van-grid-item v-for="c in categories" :key="c.text" :icon="c.icon" :text="c.text" />
    </van-grid>

    <!-- 热门目的地(仅简要介绍,点击查看详情) -->
    <div class="section">
      <h2 class="section-title">🔥 热门目的地</h2>
      <van-cell-group inset>
        <van-cell
          v-for="s in spots"
          :key="s.id"
          :title="s.name"
          :label="s.desc"
          style="background-color: #f7f7f7; border-radius: 8px; margin: 5px 0 0;"
          is-link
          @click="goDetail(s)"
        >
          <template #value>
            <van-tag type="danger" plain>{{ s.tag }}</van-tag>
          </template>
        </van-cell>
      </van-cell-group>
      <div v-if="spotsError" class="spots-error" @click="loadSpots">
        加载失败,点击重试
      </div>
    </div>
  </div>
</template>

<style scoped>

  .page {
    min-height: 100%;
    background-color: #f5f5f5;
  }

  .notice-swipe {
    height: 40px;
    line-height: 40px;

  }

  .banner {
    height: 150px;
    margin: 8px 16px;
    border-radius: 10px;
    overflow: hidden;
  }

  .banner-title {
    margin: 0;
    font-size: 26px;
    font-weight: 600;
    text-shadow: 0 2px 8px rgb(0 0 0 / 0.3);
  }

  .banner-desc {
    margin: 6px 0 0;
    font-size: 14px;
    opacity: 0.9;
  }

  .category-grid {
    margin: 8px 0;
  }

  .section {
    padding: 5px 0 0;
    background-color: #fff;
  }

  .section-title {
    margin: 5px 16px;
    font-size: 16px;
    color: var(--text-h);
  }

  .spots-error {
    padding: 12px 16px 16px;
    text-align: center;
    font-size: 13px;
    color: #969799;
    cursor: pointer;
  }
</style>
