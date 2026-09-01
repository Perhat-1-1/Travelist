<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'

const route = useRoute()
const router = useRouter()

const spot = ref(null)
const loading = ref(true)
const failed = ref(false)

const loadSpot = async () => {
  loading.value = true
  failed.value = false
  try {
    const res = await fetch(`/api/spot/${route.params.id}`)
    const body = await res.json()
    if (!res.ok || !body.success) {
      throw new Error(body?.message || `HTTP ${res.status}`)
    }
    spot.value = body.data
  } catch (err) {
    failed.value = true
    showToast('景点详情加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadSpot)

// 快速把该景点作为 AI 聊天上下文(跳转聊天页并预填问题,不自动发送)
const askAI = () => {
  router.push({
    path: '/ai-chat',
    query: { spotId: spot.value.id, spotName: spot.value.name },
  })
}
</script>

<template>
  <div class="page">
    <van-nav-bar title="景点详情" fixed placeholder left-arrow @click-left="router.back()" />

    <!-- 加载中 -->
    <div v-if="loading" class="state-box">
      <van-loading color="#1989fa" size="28" vertical>加载中…</van-loading>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="failed" class="state-box">
      <p>没能加载到景点信息</p>
      <van-button size="small" round type="primary" plain @click="loadSpot">重试</van-button>
    </div>

    <!-- 详情 -->
    <template v-else-if="spot">
      <div class="hero-card">
        <div class="hero-name">{{ spot.name }}</div>
        <div class="hero-meta">
          <van-tag type="danger" plain>{{ spot.tag }}</van-tag>
          <span class="hero-desc">{{ spot.desc }}</span>
        </div>
      </div>

      <div class="card">
        <h3 class="card-title">📖 景点介绍</h3>
        <p class="detail-text">{{ spot.detail }}</p>
      </div>

      <div class="card">
        <h3 class="card-title">✨ 亮点推荐</h3>
        <div v-for="(h, i) in spot.highlights" :key="i" class="highlight-row">
          <van-icon name="checked" color="#07c160" class="highlight-icon" />
          <span>{{ h }}</span>
        </div>
      </div>

      <div class="card" v-if="spot.bestSeason">
        <h3 class="card-title">🗓️ 最佳季节</h3>
        <p class="detail-text">{{ spot.bestSeason }}</p>
      </div>

      <div class="ask-bar">
        <van-button block round type="primary" icon="chat-o" @click="askAI">
          问 AI 这个景点
        </van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background-color: #f5f5f5;
  padding-bottom: 16px;
  box-sizing: border-box;
}

.state-box {
  padding: 60px 16px;
  text-align: center;
  color: #969799;
  font-size: 14px;
}

.hero-card {
  margin: 12px 16px 0;
  padding: 20px 16px;
  border-radius: 12px;
  background: linear-gradient(135deg, #e8f3ff, #e6f7ef);
}

.hero-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-h);
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.hero-desc {
  font-size: 13px;
  color: var(--text);
}

.card {
  margin: 12px 16px 0;
  padding: 14px 16px;
  border-radius: 10px;
  background: #fff;
}

.card-title {
  margin: 0 0 8px;
  font-size: 15px;
  color: var(--text-h);
}

.detail-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #323233;
}

.highlight-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 0;
  font-size: 14px;
  color: #323233;
}

.highlight-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.ask-bar {
  margin: 20px 16px 0;
}
</style>
