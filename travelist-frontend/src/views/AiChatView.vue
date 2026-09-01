<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import MarkdownBubble from '@/components/MarkdownBubble.vue'
import { streamChat } from '@/utils/ai'

const route = useRoute()
const router = useRouter()

const input = ref('')
const chatListRef = ref(null)

// 景点上下文(由景点详情页「问问 AI」入口带入;可手动移除)
const spotId = ref(null)
const spotName = ref('')

// 消息:{ id, role, text, status: 'done' | 'streaming' | 'error' }
const messages = ref([
  {
    id: 0,
    role: 'ai',
    text: '你好，我是你的 AI 旅行助手 🤖\n\n可以帮你规划行程、推荐美食、查询交通，随时问我吧！',
    status: 'done',
  },
])

// 流式期间收到的待发消息(不入会话气泡,以指示条提示)
const pendingQueue = ref([])
const isStreaming = ref(false)
let msgId = 0
let controller = null

// 从「问问 AI」入口进入:加载景点上下文并预填问题(不自动发送)
onMounted(() => {
  const qSpotId = route.query.spotId
  const qSpotName = route.query.spotName
  if (qSpotId && qSpotName) {
    spotId.value = Number(qSpotId)
    spotName.value = String(qSpotName)
    if (!input.value.trim()) {
      input.value = `请介绍一下「${spotName.value}」`
    }
  }
})

// 移除上下文:恢复普通聊天(已预填文字保留,由用户自行处理)
const clearContext = () => {
  spotId.value = null
  spotName.value = ''
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    const el = chatListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

const appendUser = (text) => {
  messages.value.push({ id: ++msgId, role: 'user', text, status: 'done' })
  scrollToBottom()
}

// 发起一轮 AI 流式回复
const streamAI = async (userText) => {
  messages.value.push({
    id: ++msgId,
    role: 'ai',
    text: '',
    reasoning: '',
    waitSec: 0,
    status: 'streaming',
  })
  const msg = messages.value[messages.value.length - 1]
  isStreaming.value = true
  scrollToBottom()

  controller = new AbortController()

  // 首 token 等待计时:用真实流逝秒数让用户明确感知"模型正在生成"
  let waitTimer = null
  const startWaitTimer = () => {
    if (waitTimer) return
    waitTimer = setInterval(() => {
      msg.waitSec += 1
    }, 1000)
  }
  const stopWaitTimer = () => {
    if (waitTimer) {
      clearInterval(waitTimer)
      waitTimer = null
    }
  }
  startWaitTimer()

  // 接收与显示解耦的"恒定可读打字节奏"播放
  let rawPending = ''
  let revealTimer = null
  let streamEnded = false
  let finalize = null

  const TICK_MS = 100

  const startReveal = () => {
    if (revealTimer) return
    revealTimer = setInterval(() => {
      if (rawPending) {
        const step = Math.min(30, Math.max(10, Math.ceil(rawPending.length / 600)))
        msg.text += rawPending.slice(0, step)
        rawPending = rawPending.slice(step)
        scrollToBottom()
      }
      if (!rawPending && streamEnded) {
        clearInterval(revealTimer)
        revealTimer = null
        const fn = finalize
        finalize = null
        if (fn) fn()
      }
    }, TICK_MS)
  }

  // 等待泵把积压全部上屏
  const drainDone = () => new Promise((resolve) => {
    finalize = resolve
  })

  try {
    // 会话上下文(已完成的用户/AI 消息);内部角色 'ai' 映射为协议 'assistant'
    const history = messages.value
      .filter((m) => m.status === 'done' && m.text)
      .map((m) => ({ role: m.role === 'ai' ? 'assistant' : m.role, content: m.text }))

    await streamChat({
      messages: history,
      spotId: spotId.value,
      signal: controller.signal,
      onDelta: (delta) => {
        stopWaitTimer()
        rawPending += delta
        startReveal()
      },
    })
    streamEnded = true
    startReveal()
    await drainDone()
    msg.status = 'done'
  } catch (err) {
    const isAbort = err?.name === 'AbortError'
    const detail = isAbort ? '' : err?.message || '未知错误'
    streamEnded = true
    startReveal()
    await drainDone()
    if (isAbort) {
      if (!msg.text) {
        msg.text = '已停止生成'
        msg.status = 'error'
      } else {
        msg.status = 'done'
      }
      showToast('已停止生成')
    } else {
      msg.text = msg.text ? `${msg.text}\n\n> ⚠️ ${detail}` : `> ⚠️ ${detail}`
      msg.status = 'error'
      showToast('发送失败,请稍后重试')
    }
  } finally {
    stopWaitTimer()
    isStreaming.value = false
    controller = null
  }
}

// 完整一轮:发送用户消息 → 流式回复 → 串行清空队列
const processTurn = async (userText) => {
  appendUser(userText)
  await streamAI(userText)
  while (pendingQueue.value.length) {
    const next = pendingQueue.value.shift()
    appendUser(next.text)
    await streamAI(next.text)
  }
}

const send = async (text) => {
  const content = (text ?? input.value).trim()
  if (!content) return
  input.value = ''

  if (isStreaming.value) {
    // 流式中:不入会话气泡,入队并显示指示条
    pendingQueue.value.push(content)
    showToast(`已加入发送队列(共 ${pendingQueue.value.length} 条)`)
    return
  }
  await processTurn(content)
}

// 停止生成:中断当前流,并清空未发送队列
const stop = () => {
  pendingQueue.value.splice(0)
  controller?.abort()
}

// 清空队列
const clearQueue = () => {
  pendingQueue.value.splice(0)
  showToast('已清空待发送队列')
}

// 思考指示的补充信息:有推理字数显示字数,否则显示等待秒数
const waitingHint = (m) => {
  if (m.reasoning) return `(已推理 ${m.reasoning.length} 字)`
  if (m.waitSec > 0) return `(已等待 ${m.waitSec}s)`
  return ''
}

onUnmounted(() => {
  controller?.abort()
})
</script>

<template>
  <div class="page">
    <van-nav-bar title="AI 旅行助手" fixed placeholder />

    <!-- 消息列表(独立滚动,底部不被遮挡) -->
    <div ref="chatListRef" class="chat-list">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="msg-row"
        :class="[msg.role === 'user' ? 'msg-row-user' : 'msg-row-ai', { 'msg-error': msg.status === 'error' }]"
      >
        <div class="avatar">{{ msg.role === 'user' ? '😊' : '🤖' }}</div>
        <div class="bubble">
          <!-- 首 token 等待期指示:以真实等待秒数告知用户正在生成 -->
          <div
            v-if="msg.role === 'ai' && msg.status === 'streaming' && !msg.text"
            class="thinking"
          >
            🧠 正在思考…{{ waitingHint(msg) }}
          </div>
          <MarkdownBubble v-if="msg.role === 'ai'" :text="msg.text" />
          <template v-else>{{ msg.text }}</template>
          <span v-if="msg.status === 'streaming'" class="stream-cursor">▍</span>
        </div>
      </div>
    </div>

    <!-- 景点上下文指示条 -->
    <div v-if="spotName" class="context-bar">
      <van-icon name="location-o" class="context-icon" />
      <span class="context-text">上下文:{{ spotName }}</span>
      <span class="context-clear" @click="clearContext">移除</span>
    </div>

    <!-- 待发送指示条(流式中且队列非空时显示) -->
    <div v-if="pendingQueue.length" class="pending-bar">
      <van-icon name="clock-o" class="pending-icon" />
      <span class="pending-text">流式回复中 · 待发送 {{ pendingQueue.length }} 条</span>
      <span class="pending-clear" @click="clearQueue">清空</span>
    </div>

    <!-- 输入区(普通流式定位,始终完整显示在 Tabbar 上方) -->
    <div class="input-bar">
      <van-field
        v-model="input"
        placeholder="输入你的旅行问题…"
        clearable
        class="chat-field"
        @keyup.enter="send()"
      />
      <van-button
        :type="isStreaming ? 'danger' : 'primary'"
        round
        :icon="isStreaming ? 'stop-circle-o' : 'send-o'"
        @click="isStreaming ? stop() : send()"
      >
        {{ isStreaming ? '停止' : '发送' }}
      </van-button>
    </div>
  </div>
</template>

<style scoped>
  .page {
    display: flex;
    flex-direction: column;
    height: 100svh;
    /* 预留底部 Tabbar 高度(50px + 安全区),内容永远不被遮挡 */
    padding-bottom: calc(50px + env(safe-area-inset-bottom));
    box-sizing: border-box;
  }

  .chat-list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 12px 16px 16px;
    box-sizing: border-box;
  }

  .msg-row {
    display: flex;
    margin-bottom: 14px;
    gap: 8px;
    align-items: flex-start;
  }

  .msg-row-user {
    flex-direction: row-reverse;
  }

  .avatar {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #f2f3f5;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
  }

  .bubble {
    max-width: 72%;
    padding: 10px 12px;
    border-radius: 10px;
    font-size: 14px;
    line-height: 1.5;
    word-break: break-word;
    box-sizing: border-box;
  }

  .msg-row-ai .bubble {
    max-width: 85%; /* 放宽以容纳表格与代码块 */
    background: #fff;
    color: #323233;
    border-top-left-radius: 2px;
  }

  .msg-row-user .bubble {
    background: #1989fa;
    color: #fff;
    border-top-right-radius: 2px;
    white-space: pre-wrap;
  }

  .msg-error .bubble {
    background: #fef0f0;
    color: #ee0a24;
  }

  /* 流式输出光标 */
  .stream-cursor {
    display: inline-block;
    margin-left: 1px;
    color: #1989fa;
    animation: blink 1s step-start infinite;
  }

  /* 思考中指示 */
  .thinking {
    font-size: 12px;
    color: #969799;
    font-style: italic;
    margin-bottom: 4px;
  }

  @keyframes blink {
    50% {
      opacity: 0;
    }
  }

  /* 景点上下文指示条 */
  .context-bar {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 12px 8px;
    padding: 7px 12px;
    border-radius: 8px;
    background: #e8f3ff;
    color: #1989fa;
    font-size: 12px;
    flex-shrink: 0;
  }

  .context-icon {
    flex-shrink: 0;
  }

  .context-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .context-clear {
    font-weight: 600;
    cursor: pointer;
  }

  .pending-bar {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 12px 8px;
    padding: 7px 12px;
    border-radius: 8px;
    background: #fffbe8;
    color: #ed6a0c;
    font-size: 12px;
    flex-shrink: 0;
  }

  .pending-text {
    flex: 1;
  }

  .pending-clear {
    font-weight: 600;
    cursor: pointer;
  }

  .input-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: #fff;
    border-top: 1px solid #ebedf0;
    box-sizing: border-box;
    flex-shrink: 0;
  }

  .chat-field {
    flex: 1;
    padding: 6px 12px;
    border-radius: 18px;
    background: #f7f8fa;
  }

  .input-bar .van-button {
    flex-shrink: 0;
  }
</style>
