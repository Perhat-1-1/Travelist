// ── 后端 AI 聊天 SSE 客户端 ───────────────────────────────────
// AI 能力全部由后端提供(密钥、Prompt、模型调用都在后端),
// 前端只负责:携带全量历史消息 + 可选景点上下文,解析后端 SSE 帧。

/**
 * 后端流式聊天请求
 * @param {object}  opts { messages, spotId, onDelta, signal }
 * @param {number}  [opts.spotId]  景点上下文 id(可选)
 * @param {Function} opts.onDelta  (deltaText) => void 每收到一段增量内容时回调
 * @param {AbortSignal} opts.signal 取消信号
 *
 * 后端 SSE 帧格式(每行一个 JSON,data: 前缀):
 *   data: {"delta":"..."}   增量内容
 *   data: {"error":"..."}   流中途出错
 *   data: {"done":true}     结束帧
 */
export const streamChat = async ({ messages, spotId, onDelta, signal }) => {
  const res = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages, spotId: spotId ?? null }),
    signal,
  })

  if (!res.ok) {
    // 首字节前的错误:后端返回普通 JSON Result
    let detail = `HTTP ${res.status}`
    try {
      const body = await res.json()
      if (body?.message) detail = body.message
    } catch {
      /* 保留 HTTP 状态码 */
    }
    throw new Error(detail)
  }
  if (!res.body) throw new Error('当前环境不支持流式读取')

  // 逐行解析 SSE:每个 data: 行都是独立事件,不依赖空行分隔
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const handleLine = (rawLine) => {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (!line.startsWith('data:')) return false
    const data = line.slice(5).trim()
    if (!data) return false
    try {
      const json = JSON.parse(data)
      if (json.delta) {
        onDelta(json.delta)
        return false
      }
      if (json.error) throw new Error(json.error)
      if (json.done) return true // 结束帧
    } catch (e) {
      if (e instanceof SyntaxError) return false // 忽略无法解析的行
      throw e
    }
    return false
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      // 处理无换行结尾的最后一行
      if (buffer.trim()) handleLine(buffer.trimEnd())
      return
    }
    buffer += decoder.decode(value, { stream: true })

    let idx
    while ((idx = buffer.indexOf('\n')) !== -1) {
      const line = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 1)
      if (handleLine(line)) return // 收到 done 帧
    }
  }
}
