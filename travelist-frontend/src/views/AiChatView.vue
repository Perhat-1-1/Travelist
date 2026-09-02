<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import MarkdownBubble from '@/components/MarkdownBubble.vue'
import { streamChat } from '@/utils/ai'

const route = useRoute()

const input = ref('')
const chatListRef = ref(null)

// 景点上下文(由景点详情页「问问 AI」入口带入;可手动移除)
const spotId = ref(null)
const spotName = ref('')

// ── 会话管理 ──
const sessions = ref([])          // [{ id, title, updatedAt }]
const activeSessionId = ref(null)
// 窄屏默认折叠,避免挤压对话区;左上角按钮可随时展开/收起
const sidebarCollapsed = ref(window.innerWidth < 640)
const sessionKeyword = ref('')

const WELCOME_TEXT =
  '你好，我是你的 AI 旅行助手 🤖\n\n可以帮你规划行程、推荐美食、查询交通，随时问我吧！'
const welcomeMessage = () => ({ id: 0, role: 'ai', text: WELCOME_TEXT, status: 'done' })

const filteredSessions = computed(() => {
  const kw = sessionKeyword.value.trim().toLowerCase()
  if (!kw) return sessions.value
  return sessions.value.filter((s) => (s.title || '').toLowerCase().includes(kw))
})

// 相对时间:刚刚 / N 分钟前 / N 小时前 / N 天前
const relTime = (iso) => {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return ''
  const m = Math.floor((Date.now() - t) / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  return `${Math.floor(h / 24)} 天前`
}

// 消息:{ id, role, text, status: 'done' | 'streaming' | 'error' }
const messages = ref([welcomeMessage()])
const pendingQueue = ref([])
const isStreaming = ref(false)
let msgId = 0
let controller = null

const scrollToBottom = () => {
  nextTick(() => {
    const el = chatListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

const loadSessions = async () => {
  try {
    const res = await fetch('/api/ai/session/list')
    const body = await res.json()
    if (!body.success) throw new Error(body.message)
    sessions.value = body.data ?? []
  } catch {
    /* 列表加载失败不阻塞聊天 */
  }
}

const loadHistory = async (sessionId) => {
  try {
    const res = await fetch(`/api/ai/history?sessionId=${sessionId}`)
    const body = await res.json()
    if (!body.success) throw new Error(body.message)
    const list = body.data ?? []
    messages.value = list.length
      ? list.map((m) => ({
          id: ++msgId,
          role: m.role === 'assistant' ? 'ai' : 'user',
          text: m.content,
          status: 'done',
        }))
      : [welcomeMessage()]
  } catch {
    messages.value = [welcomeMessage()]
    showToast('历史消息加载失败')
  }
  scrollToBottom()
}

// 切换会话:中断当前流,装载该会话最近 10 条
const openSession = async (s) => {
  if (isStreaming.value) controller?.abort()
  activeSessionId.value = s.id
  await loadHistory(s.id)
}

// 新建会话
const createSession = async () => {
  try {
    const res = await fetch('/api/ai/session', { method: 'POST' })
    const body = await res.json()
    if (!body.success) throw new Error(body.message)
    activeSessionId.value = body.data.id
    pendingQueue.value.splice(0)
    messages.value = [welcomeMessage()]
    await loadSessions()
    scrollToBottom()
  } catch {
    showToast('新建会话失败')
  }
}

// 删除会话(同步删除数据库中的会话与其消息记录)
const removeSession = async (s) => {
  const confirmed = await showConfirmDialog({
    title: '删除会话',
    message: `确定删除「${s.title || '新会话'}」吗?聊天记录将一并删除。`,
    confirmButtonText: '删除',
    confirmButtonColor: '#ee0a24',
  })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  try {
    const res = await fetch(`/api/ai/session/${s.id}`, { method: 'DELETE' })
    const body = await res.json()
    if (!body.success) throw new Error(body.message)
    sessions.value = sessions.value.filter((x) => x.id !== s.id)
    if (activeSessionId.value === s.id) {
      activeSessionId.value = null
      if (sessions.value.length) {
        await openSession(sessions.value[0])
      } else {
        messages.value = [welcomeMessage()]
      }
    }
    showToast('会话已删除')
  } catch {
    showToast('删除失败,请稍后重试')
  }
}

const clearContext = () => {
  spotId.value = null
  spotName.value = ''
}

onMounted(async () => {
  // 从「问问 AI」入口进入:加载景点上下文并预填问题(不自动发送)
  const qSpotId = route.query.spotId
  const qSpotName = route.query.spotName
  if (qSpotId && qSpotName) {
    spotId.value = Number(qSpotId)
    spotName.value = String(qSpotName)
    if (!input.value.trim()) {
      input.value = `请介绍一下「${spotName.value}」`
    }
  }
  await loadSessions()
  if (sessions.value.length) {
    await openSession(sessions.value[0]) // 默认打开最近会话
  } else {
    messages.value = [welcomeMessage()]
  }
})

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

  const drainDone = () => new Promise((resolve) => {
    finalize = resolve
  })

  try {
    const history = messages.value
      .filter((m) => m.status === 'done' && m.text)
      .map((m) => ({ role: m.role === 'ai' ? 'assistant' : m.role, content: m.text }))

    await streamChat({
      messages: history,
      spotId: spotId.value,
      sessionId: activeSessionId.value,
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
    loadSessions() // 刷新标题/时间(非阻塞)
  }
}

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
    pendingQueue.value.push(content)
    showToast(`已加入发送队列(共 ${pendingQueue.value.length} 条)`)
    return
  }
  // 确保有会话:无会话时先创建(标题随首条消息生成)
  if (!activeSessionId.value) {
    await createSession()
  }
  await processTurn(content)
}

const stop = () => {
  pendingQueue.value.splice(0)
  controller?.abort()
}

const clearQueue = () => {
  pendingQueue.value.splice(0)
  showToast('已清空待发送队列')
}

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
  <div class="chat-layout">
    <!-- 会话侧边栏(左上角按钮可折叠) -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-top">
        <button
          class="collapse-btn"
          :title="sidebarCollapsed ? '展开会话列表' : '收起会话列表'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <van-icon :name="sidebarCollapsed ? 'arrow' : 'wap-nav'" />
        </button>
        <span v-if="!sidebarCollapsed" class="brand">Travelist AI</span>
      </div>

      <template v-if="!sidebarCollapsed">
        <button class="new-session-btn" @click="createSession">
          <van-icon name="plus" />
          <span>新会话</span>
        </button>

        <div class="search-box">
          <van-icon name="search" class="search-icon" />
          <input v-model="sessionKeyword" placeholder="搜索会话…" />
        </div>

        <div class="session-group">会话</div>
        <div class="session-list">
          <div
            v-for="s in filteredSessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === activeSessionId }"
            @click="openSession(s)"
          >
            <span class="session-title">{{ s.title || '新会话' }}</span>
            <van-icon name="delete-o" class="session-del" @click.stop="removeSession(s)" />
            <span class="session-time">{{ relTime(s.updatedAt) }}</span>
          </div>
          <div v-if="!filteredSessions.length" class="session-empty">暂无会话</div>
        </div>
      </template>
    </aside>

    <!-- 主聊天区 -->
    <div class="chat-main">
      <van-nav-bar title="AI 旅行助手" fixed placeholder />

      <div ref="chatListRef" class="chat-list">
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="msg-row"
          :class="[msg.role === 'user' ? 'msg-row-user' : 'msg-row-ai', { 'msg-error': msg.status === 'error' }]"
        >
          <div class="avatar">{{ msg.role === 'user' ? '😊' : '🤖' }}</div>
          <div class="bubble">
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

      <!-- 输入区 -->
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
  </div>
</template>

<style scoped>
  /* ── 整体布局:侧边栏 + 聊天区 ── */
  .chat-layout {
    display: flex;
    height: 100svh;
    padding-bottom: calc(50px + env(safe-area-inset-bottom));
    box-sizing: border-box;
    overflow: hidden;
  }

  /* ── 会话侧边栏(浅色,与聊天区配色统一;窄屏默认折叠) ── */
  .sidebar {
    flex-shrink: 0;
    width: 220px;
    background: #f7f8fa;
    color: #323233;
    border-right: 1px solid #ebedf0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    transition: width 0.18s ease;
    /* 顶部固定导航栏(46px)不遮挡侧栏内容,按钮始终可点击 */
    padding-top: 46px;
    box-sizing: border-box;
  }

  .sidebar.collapsed {
    width: 48px;
    border-right: 1px solid #ebedf0;
  }

  .sidebar-top {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
  }

  .collapse-btn {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: #969799;
    font-size: 17px;
    cursor: pointer;
  }

  .collapse-btn:hover {
    background: #eceff1;
    color: #323233;
  }

  .brand {
    font-size: 14px;
    font-weight: 600;
    color: #323233;
    white-space: nowrap;
  }

  .new-session-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    margin: 2px 12px 10px;
    padding: 9px 0;
    border: none;
    border-radius: 8px;
    background: #1989fa;
    color: #fff;
    font-size: 13px;
    cursor: pointer;
  }

  .new-session-btn:hover {
    background: #0f73d9;
  }

  .search-box {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 12px 10px;
    padding: 7px 10px;
    border-radius: 8px;
    background: #fff;
    border: 1px solid #ebedf0;
  }

  .search-icon {
    color: #969799;
    font-size: 14px;
  }

  .search-box input {
    flex: 1;
    min-width: 0;
    background: transparent;
    border: none;
    outline: none;
    color: #323233;
    font-size: 12px;
  }

  .search-box input::placeholder {
    color: #c8c9cc;
  }

  .session-group {
    padding: 4px 14px 6px;
    font-size: 11px;
    color: #969799;
  }

  .session-list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding-bottom: 8px;
  }

  .session-item {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 2px 8px;
    padding: 8px 10px;
    border-radius: 8px;
    font-size: 13px;
    color: #646566;
    cursor: pointer;
  }

  .session-item:hover {
    background: #eceff1;
  }

  .session-item.active {
    background: #fff;
    color: #1989fa;
    font-weight: 600;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  }

  .session-title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .session-del {
    flex-shrink: 0;
    font-size: 14px;
    color: #c8c9cc;
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  .session-item:hover .session-del {
    opacity: 1;
  }

  .session-del:hover {
    color: #ee0a24;
  }

  .session-time {
    flex-shrink: 0;
    font-size: 11px;
    color: #969799;
  }

  .session-empty {
    padding: 20px 14px;
    font-size: 12px;
    color: #969799;
    text-align: center;
  }

  /* ── 主聊天区 ── */
  .chat-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    background: #f5f5f5;
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
    max-width: 85%;
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

  .stream-cursor {
    display: inline-block;
    margin-left: 1px;
    color: #1989fa;
    animation: blink 1s step-start infinite;
  }

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

</style>
