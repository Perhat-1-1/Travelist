<script setup>
  import { reactive, ref, computed, nextTick } from 'vue'
  import { showToast } from 'vant'

  // 后端接口(经 Vite 代理转发到 Spring Boot)
  const API_URL = '/api/travel/recommend'
  // 请求超时时间(ms):后端 LLM 生成可能长达 60s,放宽到 90s
  const TIMEOUT_MS = 90000

  // 表单数据:所有字段统一用 reactive() 存为一个对象
  // tripType 区分单程/往返; returnDate 仅往返模式使用
  const form = reactive({
    tripType: 'one-way', // 'one-way' 单程 | 'round-trip' 往返
    originCity: '', // 起始城市
    destination: '',
    departDate: '',
    returnDate: '',
    budget: '',
    days: '', // 单程手动输入;往返由日期自动计算
    requirements: '', // 具体行程要求(不超过 200 字)
  })

  const tripTypes = [
    { key: 'one-way', label: '单程' },
    { key: 'round-trip', label: '往返' },
  ]

  // 天数按"含头含尾"计算:返程 - 出发 + 1
  const DAY_MS = 86400000

  // 'YYYY-MM-DD' → Date(按本地时区解析,避免 UTC 解析导致跨天偏差)
  const toDate = (str) => {
    const [y, m, d] = str.split('-').map(Number)
    return new Date(y, m - 1, d)
  }

  // 城市选择弹层(起始城市/目的地共用一个弹层,按模式区分)
  const cities = ['北京', '上海', '成都', '大理', '张家界', '三亚', '西安', '重庆'].map(
    (city) => ({ text: city, value: city }),
  )
  const cityPickerMode = ref('') // '' 关闭 | 'origin' 起始城市 | 'destination' 目的地
  const showCityPicker = computed({
    get: () => cityPickerMode.value !== '',
    set: (value) => {
      if (!value) cityPickerMode.value = ''
    },
  })
  const cityPickerTitle = computed(() =>
    cityPickerMode.value === 'origin' ? '选择起始城市' : '选择目的地',
  )
  const openCityPicker = (mode) => {
    cityPickerMode.value = mode
  }
  const onCityConfirm = ({ selectedOptions }) => {
    const city = selectedOptions[0].text
    if (cityPickerMode.value === 'origin') {
      form.originCity = city
    } else {
      form.destination = city
    }
    cityPickerMode.value = ''
  }

  // ── 日期选择弹层(出发/返程共用一个弹层,按模式区分) ──
  const datePickerMode = ref('') // '' 关闭 | 'depart' 出发 | 'return' 返程
  const showDatePicker = computed({
    get: () => datePickerMode.value !== '',
    set: (value) => {
      if (!value) datePickerMode.value = ''
    },
  })
  const dateValue = ref([])
  const datePickerTitle = computed(() =>
    datePickerMode.value === 'depart' ? '选择出发日期' : '选择返程日期',
  )
  const maxDate = new Date()
  maxDate.setFullYear(maxDate.getFullYear() + 1)
  maxDate.setHours(0, 0, 0, 0)
  // 返程日期的最小可选值不早于出发日期
  const dateMin = computed(() => {
    if (datePickerMode.value === 'return' && form.departDate) return toDate(form.departDate)
    return new Date()
  })

  const openDatePicker = (mode) => {
    datePickerMode.value = mode
    dateValue.value = []
  }

  const onDateConfirm = ({ selectedValues }) => {
    const value = selectedValues.join('-')
    if (datePickerMode.value === 'depart') {
      form.departDate = value
      // 出发日期选晚了:若已选返程日期且更早,则清空并提醒重新选择
      if (form.tripType === 'round-trip' && form.returnDate && form.returnDate < value) {
        form.returnDate = ''
        showToast('返程日期早于出发日期,请重新选择')
      }
    } else {
      form.returnDate = value
    }
    datePickerMode.value = ''
  }

  // ── 天数:往返模式只读,按往返日期自动计算;单程模式手动输入 ──
  const daysAuto = computed(() => {
    if (form.tripType === 'round-trip' && form.departDate && form.returnDate) {
      const diff = Math.round((toDate(form.returnDate) - toDate(form.departDate)) / DAY_MS)
      return diff + 1 >= 1 ? diff + 1 : null
    }
    return null
  })

  const daysReadonly = computed(() => form.tripType === 'round-trip')
  const daysDisplay = computed(() =>
    daysAuto.value !== null ? String(daysAuto.value) : form.days,
  )
  const daysPlaceholder = computed(() =>
    form.tripType === 'round-trip'
      ? form.departDate
        ? '选择返程日期后自动计算'
        : '选择日期后自动计算'
      : '请输入天数(1-30)',
  )
  const daysCount = computed(() => Number(daysDisplay.value))

  // x 天 x 晚
  const daysText = computed(() => {
    const n = daysCount.value
    return Number.isInteger(n) && n > 0 ? `${n} 天 ${n - 1} 晚` : ''
  })

  // ── 提交状态:true 时播放加载动画并禁用提交按钮 ──
  const submitting = ref(false)

  // 数据合法性校验:返回 '' 表示合法,否则返回错误提示
  const validateForm = () => {
    if (!form.originCity) return '请先选择起始城市'
    if (!form.destination) return '请先选择目的地'
    if (!form.departDate) return '请选择出发日期'
    if (form.tripType === 'round-trip') {
      if (!form.returnDate) return '请选择返程日期'
      if (form.returnDate < form.departDate) return '出发日期不能晚于返程日期'
    } else {
      const d = Number(form.days)
      if (!form.days || !Number.isInteger(d) || d < 1 || d > 30) return '请输入有效天数(1-30)'
    }
    if (!form.budget || Number(form.budget) <= 0) return '请输入预算金额 (>0)'
    const req = form.requirements.trim()
    if (!req) return '请填写具体行程要求'
    if (req.length > 200) return '行程要求不能超过 200 字'
    return ''
  }

  // 组装发送给后端的 JSON 数据(destination → city,days 往返取自动值)
  const buildPayload = () => ({
    originCity: form.originCity,
    city: form.destination,
    tripType: form.tripType,
    departDate: form.departDate,
    returnDate: form.tripType === 'round-trip' ? form.returnDate : null,
    budget: Number(form.budget),
    days: form.tripType === 'round-trip' ? daysAuto.value : Number(form.days),
    requirements: form.requirements.trim(),
  })

  // ── 规划结果(同页展示,可展开/收起) ──
  const plan = ref(null)
  const showResult = ref(false)

  const slotLabels = { morning: '上午', afternoon: '下午', evening: '晚间' }
  const budgetItems = [
    { key: 'accommodation', label: '住宿' },
    { key: 'food', label: '餐饮' },
    { key: 'transportation', label: '交通' },
    { key: 'tickets', label: '门票' },
    { key: 'other', label: '其他' },
  ]

  const num = (v) => {
    const n = Number(v)
    return Number.isFinite(n) ? n : 0
  }

  const scrollToResult = () => {
    nextTick(() => {
      document.querySelector('.result-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  const onPlan = async () => {
    if (submitting.value) return
    // 发送到后端前必须先校验数据是否合法
    const err = validateForm()
    if (err) return showToast(err)

    submitting.value = true
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)

    try {
      const res = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
        signal: controller.signal,
      })
      const body = await res.json().catch(() => null)
      if (!res.ok || !body?.success) {
        throw new Error(body?.message || `HTTP ${res.status}`)
      }
      plan.value = body.data
      showResult.value = true
      scrollToResult()
    } catch (err) {
      const detail = err?.message || '未知错误'
      showToast(`规划失败:${detail}`)
    } finally {
      clearTimeout(timer)
      submitting.value = false
    }
  }

  // 重新规划:清空结果,回到表单
  const resetPlan = () => {
    plan.value = null
    showResult.value = false
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
</script>

<template>
  <div class="page">
    <van-nav-bar title="规划你的旅程" fixed placeholder />

    <!-- 单程 / 往返切换 -->
    <div class="trip-type">
      <button
        v-for="t in tripTypes"
        :key="t.key"
        :class="['pill', { 'pill-active': form.tripType === t.key }]"
        @click="form.tripType = t.key"
      >
        {{ t.label }}
      </button>
    </div>
    <p class="tip">
      {{ form.tripType === 'one-way' ? '单程规划:手动指定天数' : '往返规划:自动计算行程时间' }}
    </p>

    <!-- 表单卡片 -->
    <van-cell-group inset class="form-card">
      <van-field
        v-model="form.originCity"
        readonly
        is-link
        label="起始城市"
        placeholder="请选择出发城市"
        @click="openCityPicker('origin')"
      />
      <van-field
        v-model="form.destination"
        readonly
        is-link
        label="目的地"
        placeholder="请选择城市"
        @click="openCityPicker('destination')"
      />
      <van-field
        v-model="form.departDate"
        readonly
        is-link
        label="出发日期"
        placeholder="请选择出发日期"
        @click="openDatePicker('depart')"
      />
      <van-field
        v-if="form.tripType === 'round-trip'"
        v-model="form.returnDate"
        readonly
        is-link
        label="返程日期"
        placeholder="请选择返程日期"
        @click="openDatePicker('return')"
      />
      <van-field
        v-model="form.budget"
        type="number"
        label="预算（元）"
        placeholder="请输入预算金额"
        clearable
      />
      <van-field
        :model-value="daysDisplay"
        :readonly="daysReadonly"
        type="digit"
        label="天数"
        :placeholder="daysPlaceholder"
        @update:model-value="form.days = $event"
      >
        <template v-if="daysReadonly" #right-icon>
          <van-tag type="primary" plain>自动</van-tag>
        </template>
      </van-field>
      <van-field
        v-model="form.requirements"
        type="textarea"
        rows="3"
        :autosize="{ maxHeight: 70 }"
        maxlength="200"
        show-word-limit
        label="行程要求"
        placeholder="描述您的具体行程要求,如偏好、必玩景点、线路安排等(200 字以内)"
        required
      />
    </van-cell-group>

    <div class="submit">
      <van-button block round type="primary" :loading="submitting" @click="onPlan">
        开始规划
      </van-button>
    </div>

    <!-- 规划结果(同页展示,头部可展开/收起) -->
    <div v-if="plan" class="result-section">
      <div class="result-header" @click="showResult = !showResult">
        <h2 class="result-title">
          🗺️ 规划结果 · {{ form.originCity }} → {{ plan.city }} · {{ plan.days }} 天
        </h2>
        <van-icon :name="showResult ? 'arrow-up' : 'arrow-down'" class="result-arrow" />
      </div>

      <div v-show="showResult" class="result-body">
        <!-- 每日行程 -->
        <div v-for="day in plan.dailyItineraryList || []" :key="day.day" class="day-card">
          <div class="day-title">
            第 {{ day.day }} 天 · {{ day.date }}
          </div>
          <div v-for="slot in ['morning', 'afternoon', 'evening']" :key="slot" class="slot-row">
            <span class="slot-label">{{ slotLabels[slot] }}</span>
            <div class="slot-body">
              <div class="slot-spot">{{ day[slot]?.spot || '自由活动' }}</div>
              <div class="slot-meta">
                <van-tag plain type="primary" size="mini">{{ day[slot]?.duration || '—' }}</van-tag>
                <van-tag plain size="mini">{{ day[slot]?.transportation || '—' }}</van-tag>
              </div>
              <div class="slot-desc">{{ day[slot]?.description || '' }}</div>
            </div>
          </div>
        </div>

        <!-- 简易交通流程 -->
        <div v-if="plan.transportPlan" class="transport-card">
          <div class="transport-title">🚄 简易交通流程</div>
          <div class="transport-route">
            <span class="route-node">{{ form.originCity }}</span>
            <van-icon name="arrow" class="route-arrow" />
            <span class="route-node">{{ plan.city }}</span>
            <template v-if="plan.transportPlan.returnRoute">
              <van-icon name="arrow" class="route-arrow" />
              <span class="route-node">{{ form.originCity }}</span>
            </template>
          </div>
          <div v-if="plan.transportPlan.toDestination" class="transport-row">
            <span class="transport-label">去程</span>
            <span class="transport-text">{{ plan.transportPlan.toDestination }}</span>
          </div>
          <div v-if="plan.transportPlan.local" class="transport-row">
            <span class="transport-label">市内</span>
            <span class="transport-text">{{ plan.transportPlan.local }}</span>
          </div>
          <div v-if="plan.transportPlan.returnRoute" class="transport-row">
            <span class="transport-label return-label">返程</span>
            <span class="transport-text">{{ plan.transportPlan.returnRoute }}</span>
          </div>
        </div>

        <!-- 预算构成 -->
        <div class="budget-card">
          <div class="budget-title">💰 预算构成(共 ¥{{ num(plan.totalBudget) }})</div>
          <div class="budget-grid">
            <div v-for="item in budgetItems" :key="item.key" class="budget-item">
              <div class="budget-label">{{ item.label }}</div>
              <div class="budget-value">¥{{ num(plan.budgetBreakdown?.[item.key]) }}</div>
            </div>
          </div>
        </div>

        <!-- 实用建议 -->
        <div v-if="plan.tips?.length" class="tips-card">
          <div class="tips-title">💡 实用建议</div>
          <div v-for="(t, i) in plan.tips" :key="i" class="tip-row">{{ t }}</div>
        </div>

        <!-- 注意事项 -->
        <div v-if="plan.warnings?.length" class="warnings-card">
          <div class="warnings-title">⚠️ 注意事项</div>
          <div v-for="(w, i) in plan.warnings" :key="i" class="warn-row">{{ w }}</div>
        </div>

        <van-button class="reset-btn" block plain round type="primary" @click="resetPlan">
          重新规划
        </van-button>
      </div>
    </div>

    <!-- 城市选择(起始城市/目的地共用) -->
    <van-popup v-model:show="showCityPicker" position="bottom" round>
      <van-picker
        title="选择城市"
        :columns="cities"
        @confirm="onCityConfirm"
        @cancel="cityPickerMode = ''"
      />
    </van-popup>

    <!-- 日期选择(出发/返程共用) -->
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        v-model="dateValue"
        :title="datePickerTitle"
        :min-date="dateMin"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="datePickerMode = ''"
      />
    </van-popup>

    <!-- 提交等待:加载动画(等待后端响应,超时后自动报错) -->
    <van-overlay :show="submitting" z-index="2000">
      <div class="loading-box">
        <van-loading color="#1989fa" size="36" vertical>行程生成中…</van-loading>
      </div>
    </van-overlay>
  </div>
</template>

<style scoped>

  .page {
    min-height: 100%;
    background-color: #f5f5f5;
  }

  .trip-type {
    display: flex;
    gap: 10px;
    margin: 12px 16px 0;
  }

  .pill {
    flex: 1;
    padding: 9px 0;
    border: 1px solid #ebedf0;
    border-radius: 8px;
    background: #fff;
    font-size: 14px;
    color: #646566;
    cursor: pointer;
  }

  .pill-active {
    border-color: #1989fa;
    color: #1989fa;
    background: #ecf5ff;
  }

  .tip {
    margin: 8px 16px 0;
    font-size: 12px;
    color: #969799;
  }

  .form-card {
    margin-top: 12px;
  }

  .submit {
    margin: 24px 16px;
  }

  /* ── 结果区 ── */
  .result-section {
    margin: 0 12px 24px;
    padding: 12px 14px;
    border-radius: 10px;
    background: #fff;
  }

  .result-header {
    display: flex;
    align-items: center;
    cursor: pointer;
  }

  .result-title {
    flex: 1;
    margin: 0;
    font-size: 15px;
    color: var(--text-h);
  }

  .result-arrow {
    color: #969799;
    font-size: 16px;
  }

  .result-body {
    margin-top: 12px;
  }

  .day-card {
    margin-bottom: 12px;
    padding: 10px 12px;
    border-radius: 8px;
    background: #f7f8fa;
  }

  .day-title {
    font-size: 14px;
    font-weight: 600;
    color: #1989fa;
    margin-bottom: 8px;
  }

  .slot-row {
    display: flex;
    gap: 8px;
    margin: 6px 0;
  }

  .slot-label {
    flex-shrink: 0;
    width: 36px;
    margin-top: 2px;
    font-size: 12px;
    color: #969799;
  }

  .slot-body {
    flex: 1;
    min-width: 0;
  }

  .slot-spot {
    font-size: 14px;
    font-weight: 600;
    color: #323233;
  }

  .slot-meta {
    display: flex;
    gap: 6px;
    margin-top: 4px;
  }

  .slot-desc {
    margin-top: 4px;
    font-size: 12px;
    color: #646566;
  }

  /* 简易交通流程卡片 */
  .transport-card {
    margin-bottom: 12px;
    padding: 10px 12px;
    border-radius: 8px;
    background: #eef7ff;
  }

  .transport-title {
    font-size: 13px;
    font-weight: 600;
    color: #1989fa;
  }

  .transport-route {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 8px 0;
    font-size: 13px;
  }

  .route-node {
    padding: 3px 8px;
    border-radius: 12px;
    background: #fff;
    border: 1px solid #d6e9ff;
    color: #1989fa;
    font-weight: 600;
  }

  .route-arrow {
    color: #969799;
    font-size: 14px;
  }

  .transport-row {
    display: flex;
    gap: 8px;
    margin: 6px 0;
    font-size: 12px;
    line-height: 1.6;
  }

  .transport-label {
    flex-shrink: 0;
    width: 32px;
    color: #1989fa;
    font-weight: 600;
  }

  .return-label {
    color: #ed6a0c;
  }

  .transport-text {
    flex: 1;
    color: #323233;
  }

  .budget-card {
    margin-bottom: 12px;
    padding: 10px 12px;
    border-radius: 8px;
    background: #fffbe8;
  }

  .budget-title {
    font-size: 13px;
    font-weight: 600;
    color: #ed6a0c;
  }

  .budget-grid {
    display: flex;
    margin-top: 8px;
  }

  .budget-item {
    flex: 1;
    text-align: center;
  }

  .budget-label {
    font-size: 11px;
    color: #969799;
  }

  .budget-value {
    margin-top: 2px;
    font-size: 13px;
    font-weight: 600;
    color: #323233;
  }

  .tips-card,
  .warnings-card {
    margin-bottom: 12px;
    padding: 10px 12px;
    border-radius: 8px;
  }

  .tips-card {
    background: #ecf9f0;
  }

  .warnings-card {
    background: #fef0f0;
  }

  .tips-title,
  .warnings-title {
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 6px;
  }

  .tips-title {
    color: #07c160;
  }

  .warnings-title {
    color: #ee0a24;
  }

  .tip-row,
  .warn-row {
    font-size: 12px;
    color: #323233;
    margin: 4px 0;
    line-height: 1.5;
  }

  .reset-btn {
    margin-top: 16px;
  }

  .loading-box {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    padding: 24px 32px;
    border-radius: 10px;
    background: #fff;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  }
</style>
