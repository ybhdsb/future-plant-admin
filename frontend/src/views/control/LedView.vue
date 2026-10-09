<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import {
  DEVICE_KEY,
  deleteLedSchedule,
  getDashboard,
  getLedSchedules,
  getLedState,
  ledBroadcastSet,
  ledHealth,
  ledRead,
  ledReadAll,
  ledSet,
  saveLedSchedule,
  setLedScheduleEnabled,
} from '@/api/plant'

/** 每台驱动器光谱（V1.5 手册）；分组按物理两列，不按光谱配对 */
type DeviceDef = {
  address: string
  ch1: string
  ch2: string
  colors: [string, string]
}

const DEVICE_META: Record<string, Omit<DeviceDef, 'address'>> = {
  '0x96': { ch1: '660nm', ch2: '395nm', colors: ['#e11d48', '#7c3aed'] },
  '0x97': { ch1: '450nm', ch2: '530nm', colors: ['#3b82f6', '#16a34a'] },
  '0x98': { ch1: '630nm', ch2: '430nm', colors: ['#dc2626', '#2563eb'] },
  '0x99': { ch1: '730nm', ch2: '全光谱', colors: ['#9f1239', '#94a3b8'] },
  '0x9A': { ch1: '660nm', ch2: '395nm', colors: ['#e11d48', '#7c3aed'] },
  '0x9B': { ch1: '450nm', ch2: '530nm', colors: ['#3b82f6', '#16a34a'] },
  '0x9C': { ch1: '630nm', ch2: '430nm', colors: ['#dc2626', '#2563eb'] },
  '0x9D': { ch1: '730nm', ch2: '全光谱', colors: ['#9f1239', '#94a3b8'] },
}

/** 正确分组：第一列 4 台 = 组 1，第二列 4 台 = 组 2 */
const RACK_GROUPS = [
  {
    id: 1,
    title: '第一组 · 左列',
    desc: '驱动器 0x96～0x99',
    addresses: ['0x96', '0x97', '0x98', '0x99'] as const,
  },
  {
    id: 2,
    title: '第二组 · 右列',
    desc: '驱动器 0x9A～0x9D',
    addresses: ['0x9A', '0x9B', '0x9C', '0x9D'] as const,
  },
]

type LiveEntry = {
  seen: boolean
  ok: boolean
  confirmed: [number | null, number | null]
  draft: [number | null, number | null]
  error: string
}

function emptyLive(): LiveEntry {
  return { seen: false, ok: false, confirmed: [null, null], draft: [null, null], error: '' }
}

const live = reactive<Record<string, LiveEntry>>(
  Object.fromEntries(Object.keys(DEVICE_META).map((a) => [a, emptyLive()])),
)

const schedules = ref<any[]>([])
const dash = ref<any>(null)
const health = ref<any>(null)
const gatewayEnabled = ref(false)
const lastSync = ref('')
const msg = ref('')
const err = ref('')
const busy = ref(false)
const broadcastCh1 = ref(20)
const broadcastCh2 = ref(20)
const activity = ref<{ text: string; kind: string; time: string }[]>([])
const form = ref({
  name: '白天补光',
  scheduleType: 'DAILY_WINDOW',
  brightness: 80,
  onTime: '06:00',
  offTime: '22:00',
  onMinutes: 60,
  offMinutes: 30,
  remark: '',
  enabled: true,
})

const deviceList = computed(() =>
  Object.keys(DEVICE_META).map((address, i) => ({
    address,
    idx: i + 1,
    ...DEVICE_META[address],
  })),
)

const onlineCount = computed(() =>
  Object.values(live).filter((e) => e.seen && e.ok).length,
)

const knownCount = computed(() => Object.values(live).filter((e) => e.seen).length)

const gatewayText = computed(() => {
  if (!gatewayEnabled.value) return '网关未启用'
  if (health.value?.ok === false) return health.value?.message || '网关离线'
  if (health.value?.serialConnected) return '实机 · 串口已连'
  return '实机 · 网关在线'
})

function pct(n: number | null) {
  return n == null ? '—' : `${Math.round((n * 100) / 255)}%`
}

function isDirty(address: string) {
  const e = live[address]
  if (!e?.seen || !e.ok) return false
  return e.draft.some((v, i) => v !== e.confirmed[i])
}

function anyDirty() {
  return Object.keys(DEVICE_META).some((a) => isDirty(a))
}

function stamp() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function addLog(text: string, kind = 'success') {
  activity.value.unshift({ text, kind, time: stamp() })
  if (activity.value.length > 20) activity.value.length = 20
}

function clamp255(v: unknown): number | null {
  if (v === '' || v == null) return null
  const n = Number(v)
  if (!Number.isFinite(n) || n < 0 || n > 255 || !Number.isInteger(n)) return null
  return n
}

function setDraft(address: string, ch: 0 | 1, raw: unknown) {
  const level = clamp255(raw)
  live[address].draft[ch] = level
}

function applyDeviceRead(dev: {
  busAddress?: string
  ch1?: number | null
  ch2?: number | null
  ok?: boolean
  error?: string
}) {
  const address = String(dev.busAddress || '').trim()
  if (!live[address]) return
  const e = live[address]
  e.seen = true
  const ok =
    dev.ok !== false &&
    Number.isInteger(dev.ch1) &&
    Number.isInteger(dev.ch2) &&
    clamp255(dev.ch1) != null &&
    clamp255(dev.ch2) != null
  e.ok = ok
  e.error = dev.error ? String(dev.error) : ''
  if (ok) {
    e.confirmed = [Number(dev.ch1), Number(dev.ch2)]
    e.draft = [Number(dev.ch1), Number(dev.ch2)]
  }
}

async function loadMeta() {
  ;[dash.value, schedules.value, health.value] = await Promise.all([
    getDashboard().catch(() => null),
    getLedSchedules().catch(() => []),
    ledHealth().catch(() => null),
  ])
  const led = await getLedState().catch(() => null)
  gatewayEnabled.value = !!led?.gatewayEnabled
  if (led?.health) health.value = led.health
  if (led?.syncError) err.value = String(led.syncError)
  ;(led?.channels || []).forEach((ch: any) => {
    const address = String(ch.busAddress || '')
    if (!live[address] || ch.level == null) return
    const slot = Number(ch.channel) === 2 ? 1 : 0
    if (!live[address].seen) {
      live[address].seen = true
      live[address].ok = !!ch.online
    }
    if (ch.online) {
      live[address].confirmed[slot] = Number(ch.level)
      live[address].draft[slot] = Number(ch.level)
      live[address].ok = true
    } else {
      live[address].ok = false
    }
  })
}

async function refreshAll(force = false) {
  if (busy.value) return
  if (!force && anyDirty() && !confirm('有尚未应用的调光修改，读取全部将覆盖。确定继续？')) return
  busy.value = true
  msg.value = ''
  err.value = ''
  try {
    health.value = await ledHealth().catch(() => health.value)
    const result = await ledReadAll()
    const devices = result?.devices || []
    for (const d of devices) applyDeviceRead(d)
    lastSync.value = new Date().toLocaleString('zh-CN', { hour12: false })
    const ok = Object.values(live).filter((e) => e.seen && e.ok).length
    addLog(`读取全部完成：${ok}/8 台成功`, ok === 8 ? 'success' : 'warning')
    msg.value = `已同步 ${ok}/8 台`
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || String(e)
    addLog(`读取全部失败：${err.value}`, 'error')
  } finally {
    busy.value = false
  }
}

async function readOne(address: string) {
  if (busy.value) return
  if (isDirty(address) && !confirm(`${address} 有未应用修改，回读将覆盖。确定？`)) return
  busy.value = true
  err.value = ''
  try {
    const data = await ledRead(address)
    applyDeviceRead({ ...data, busAddress: address, ok: true })
    lastSync.value = new Date().toLocaleString('zh-CN', { hour12: false })
    addLog(`${address} 回读 CH1=${data.ch1} / CH2=${data.ch2}`)
    msg.value = `${address} 回读成功`
  } catch (e: any) {
    live[address].seen = true
    live[address].ok = false
    live[address].error = e?.response?.data?.message || e?.message || String(e)
    err.value = live[address].error
    addLog(`${address} 回读失败：${err.value}`, 'error')
  } finally {
    busy.value = false
  }
}

async function setOne(address: string) {
  if (busy.value) return
  const e = live[address]
  if (!e.ok) {
    err.value = '请先成功读取该设备'
    return
  }
  const [ch1, ch2] = e.draft
  if (ch1 == null || ch2 == null) {
    err.value = '请填写 0–255 的整数光强'
    return
  }
  if (!isDirty(address)) {
    msg.value = '没有待应用的修改'
    return
  }
  busy.value = true
  err.value = ''
  try {
    await ledSet(address, ch1, ch2)
    addLog(`${address} 已写入 CH1=${ch1} / CH2=${ch2}`)
    try {
      const data = await ledRead(address)
      applyDeviceRead({ ...data, busAddress: address, ok: true })
      const match = data.ch1 === ch1 && data.ch2 === ch2
      msg.value = match ? `${address} 设置成功` : `${address} 写入成功，回读不完全一致`
      addLog(`${address} 回读 CH1=${data.ch1} / CH2=${data.ch2}`, match ? 'success' : 'warning')
    } catch (re: any) {
      msg.value = `${address} 已写入，回读失败`
      addLog(`${address} 写入后回读失败`, 'warning')
    }
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || String(e)
    addLog(`${address} 设置失败：${err.value}`, 'error')
  } finally {
    busy.value = false
  }
}

async function broadcast(ch1: number, ch2: number, label: string) {
  if (busy.value) return
  if (!confirm(`确认${label}？\n将影响全部 8 台驱动器。\nCH1=${ch1}，CH2=${ch2}`)) return
  busy.value = true
  err.value = ''
  try {
    await ledBroadcastSet(ch1, ch2)
    addLog(`广播已发送 CH1=${ch1} / CH2=${ch2}，等待回读…`, 'warning')
    msg.value = '广播已发送，正在回读确认…'
    // 平台侧已 sleep 3s；再主动 read-all 刷新卡片
    const result = await ledReadAll()
    for (const d of result?.devices || []) applyDeviceRead(d)
    lastSync.value = new Date().toLocaleString('zh-CN', { hour12: false })
    const matched = (result?.devices || []).filter(
      (d: any) => d.ok !== false && d.ch1 === ch1 && d.ch2 === ch2,
    ).length
    msg.value = `广播完成：${matched}/8 台确认`
    addLog(`广播确认 ${matched}/8`, matched === 8 ? 'success' : 'warning')
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || String(e)
    addLog(`广播失败：${err.value}`, 'error')
  } finally {
    busy.value = false
  }
}

async function saveSchedule() {
  if (busy.value) return
  busy.value = true
  err.value = ''
  try {
    await saveLedSchedule({ deviceKey: DEVICE_KEY, ...form.value })
    schedules.value = await getLedSchedules()
    msg.value = '策略已保存'
    addLog('定时策略已保存')
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || String(e)
  } finally {
    busy.value = false
  }
}

async function toggleSch(s: any) {
  if (busy.value) return
  busy.value = true
  try {
    await setLedScheduleEnabled(s.id, !s.enabled)
    schedules.value = await getLedSchedules()
    msg.value = s.enabled ? '策略已停用' : '策略已启用'
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || String(e)
  } finally {
    busy.value = false
  }
}

async function removeSch(s: any) {
  if (busy.value) return
  busy.value = true
  try {
    await deleteLedSchedule(s.id)
    schedules.value = await getLedSchedules()
    msg.value = '策略已删除'
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || String(e)
  } finally {
    busy.value = false
  }
}

function paramText(s: any) {
  if (s.scheduleType === 'INTERVAL') {
    return `开 ${s.onMinutes || 0} 分钟 / 关 ${s.offMinutes || 0} 分钟`
  }
  return `${s.onTime || '--'} ~ ${s.offTime || '--'}`
}

function deviceMessage(address: string) {
  const e = live[address]
  if (!e.seen) return '请先读取设备'
  if (!e.ok) return e.error || '设备未能回读'
  if (isDirty(address)) return '已修改预设，尚未发送'
  return '已与设备读取值同步'
}

onMounted(async () => {
  try {
    await loadMeta()
    await refreshAll(true)
  } catch (e: any) {
    err.value = e?.message || String(e)
  }
})
</script>

<template>
  <div class="fp-page led-page">
    <FpPageHeader
      kicker="Lighting"
      title="LED 补光控制"
      subtitle="8 台驱动器按物理两列分为两组；滑条改动后需「应用设置」。定时策略为本平台扩展能力。"
    >
      <template #actions>
        <span class="status" :class="health?.ok ? 'is-on' : 'is-off'">{{ gatewayText }}</span>
        <span class="meta">{{ dash?.mockEnabled ? 'Mock' : '实机' }}</span>
        <FpButton variant="secondary" size="sm" :disabled="busy" @click="refreshAll()">
          读取全部设备
        </FpButton>
      </template>
    </FpPageHeader>

    <p v-if="msg" class="ok">{{ msg }}</p>
    <p v-if="err" class="bad">{{ err }}</p>

    <div class="metrics">
      <div class="metric">
        <div class="metric-label">API 服务</div>
        <strong>{{ health?.ok === false ? '异常' : gatewayEnabled ? '运行正常' : '未启用' }}</strong>
        <span>{{ health?.message || health?.baseUrl || '经平台转发至同事网关' }}</span>
      </div>
      <div class="metric">
        <div class="metric-label">RS485 串口</div>
        <strong>{{ health?.serialConnected ? '已连接' : health?.ok ? '待打开' : '—' }}</strong>
        <span>9600 8N1 · Modbus RTU</span>
      </div>
      <div class="metric">
        <div class="metric-label">设备响应</div>
        <strong>{{ knownCount ? onlineCount : '—' }} <small>/ 8 台</small></strong>
        <span>{{ lastSync ? `最近读取 ${lastSync}` : '等待第一次读取' }}</span>
      </div>
    </div>

    <div class="section-head">
      <div>
        <p class="eyebrow">DEVICE CONTROL</p>
        <h2>两组机架 · 独立调光</h2>
        <p class="sub">
          左列四台为一组，右列四台为另一组（不是按光谱两两配对）。每台设备独立设置 CH1 / CH2；光强为协议值 0–255。
        </p>
      </div>
      <span class="legend">成功回读后才显示为已确认</span>
    </div>

    <div class="rack-grid">
      <section v-for="g in RACK_GROUPS" :key="g.id" class="rack-group">
        <div class="rack-head">
          <span class="group-badge">组 {{ g.id }}</span>
          <div>
            <h3>{{ g.title }}</h3>
            <p>{{ g.desc }} · 同列独立控制</p>
          </div>
        </div>
        <div class="device-stack">
          <article
            v-for="address in g.addresses"
            :key="address"
            class="device-card"
            :class="{ dirty: isDirty(address), offline: live[address].seen && !live[address].ok }"
          >
            <div class="device-top">
              <div class="id-block">
                <span class="idx">{{
                  String(deviceList.find((d) => d.address === address)?.idx || 0).padStart(2, '0')
                }}</span>
                <div>
                  <h4>驱动器 {{ address }}</h4>
                  <p>
                    CH1 {{ DEVICE_META[address].ch1 }} · CH2 {{ DEVICE_META[address].ch2 }}
                  </p>
                </div>
              </div>
              <span
                class="pill"
                :class="!live[address].seen ? '' : live[address].ok ? 'on' : 'off'"
              >
                {{ !live[address].seen ? '尚未读取' : live[address].ok ? '通信正常' : '读取失败' }}
              </span>
            </div>

            <div
              v-for="ch in [0, 1] as const"
              :key="ch"
              class="channel"
              :style="{ '--spectrum': DEVICE_META[address].colors[ch] }"
            >
              <div class="ch-label">
                <span>
                  <i class="dot" />
                  CH{{ ch + 1 }} · {{ ch === 0 ? DEVICE_META[address].ch1 : DEVICE_META[address].ch2 }}
                </span>
                <b>{{ pct(live[address].draft[ch]) }}</b>
              </div>
              <div class="ch-controls">
                <input
                  type="range"
                  min="0"
                  max="255"
                  :value="live[address].draft[ch] ?? 0"
                  :disabled="busy || !live[address].ok"
                  :style="{
                    '--fill': `${live[address].draft[ch] == null ? 0 : (100 * (live[address].draft[ch] as number)) / 255}%`,
                  }"
                  @input="setDraft(address, ch, ($event.target as HTMLInputElement).value)"
                />
                <input
                  type="number"
                  min="0"
                  max="255"
                  step="1"
                  :value="live[address].draft[ch] ?? ''"
                  :disabled="busy || !live[address].ok"
                  placeholder="—"
                  @input="setDraft(address, ch, ($event.target as HTMLInputElement).value)"
                />
              </div>
              <p class="ch-sub">
                {{
                  live[address].confirmed[ch] == null
                    ? '暂无已确认数据'
                    : `设备回读：${live[address].confirmed[ch]} / 255${
                        live[address].draft[ch] !== live[address].confirmed[ch] ? ' · 有待应用修改' : ''
                      }`
                }}
              </p>
            </div>

            <div class="device-foot">
              <span>{{ deviceMessage(address) }}</span>
              <div class="ops">
                <FpButton size="sm" variant="secondary" :disabled="busy" @click="readOne(address)">
                  回读
                </FpButton>
                <FpButton
                  size="sm"
                  :disabled="busy || !live[address].ok || !isDirty(address)"
                  @click="setOne(address)"
                >
                  应用设置
                </FpButton>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>

    <div class="fp-grid-2 bottom">
      <FpPanel title="全局广播" desc="向全部 8 台广播 CH1/CH2；发送后回读确认（平台会等待约 3 秒）">
        <div class="bcast">
          <label>全部 CH1<input v-model.number="broadcastCh1" type="number" min="0" max="255" /></label>
          <label>全部 CH2<input v-model.number="broadcastCh2" type="number" min="0" max="255" /></label>
        </div>
        <div class="bcast-ops">
          <FpButton :disabled="busy" @click="broadcast(broadcastCh1, broadcastCh2, '广播指定光强')">
            广播应用指定光强
          </FpButton>
          <FpButton variant="secondary" :disabled="busy" @click="broadcast(0, 0, '关闭所有灯')">
            全部关闭
          </FpButton>
          <FpButton variant="secondary" :disabled="busy" @click="broadcast(255, 255, '将所有灯设为最亮')">
            全部全亮
          </FpButton>
        </div>
      </FpPanel>

      <FpPanel title="操作记录" desc="当前网页会话内记录（平台数据库另有指令流水）">
        <template #extra>
          <FpButton variant="ghost" size="sm" @click="activity = []">清空</FpButton>
        </template>
        <ul class="activity">
          <li v-for="(a, i) in activity" :key="i" :class="a.kind">
            <span>{{ a.text }}</span>
            <time>{{ a.time }}</time>
          </li>
          <li v-if="!activity.length" class="empty">尚无操作记录</li>
        </ul>
      </FpPanel>
    </div>

    <div class="fp-grid-2">
      <FpPanel title="定时补光策略" desc="本平台扩展：每天时段 / 间隔循环，到点经广播下发统一亮度">
        <div class="form">
          <label>策略名称<input v-model="form.name" /></label>
          <label>
            类型
            <select v-model="form.scheduleType">
              <option value="DAILY_WINDOW">每天时段</option>
              <option value="INTERVAL">间隔循环</option>
            </select>
          </label>
          <label>亮度 %<input v-model.number="form.brightness" type="number" min="0" max="100" /></label>
          <template v-if="form.scheduleType === 'DAILY_WINDOW'">
            <label>开启<input v-model="form.onTime" /></label>
            <label>关闭<input v-model="form.offTime" /></label>
          </template>
          <template v-else>
            <label>开(分)<input v-model.number="form.onMinutes" type="number" /></label>
            <label>关(分)<input v-model.number="form.offMinutes" type="number" /></label>
          </template>
          <label>备注<input v-model="form.remark" placeholder="可选" /></label>
          <FpButton :disabled="busy" @click="saveSchedule">保存策略</FpButton>
        </div>
      </FpPanel>
      <FpPanel title="已有策略">
        <ul class="sch">
          <li v-for="s in schedules" :key="s.id">
            <div>
              <b>{{ s.name }} · {{ s.brightness }}%</b>
              <div class="meta">
                {{ s.scheduleType === 'INTERVAL' ? '间隔循环' : '每天时段' }} · {{ paramText(s) }}
              </div>
            </div>
            <div class="ops">
              <FpButton size="sm" variant="secondary" :disabled="busy" @click="toggleSch(s)">
                {{ s.enabled ? '停用' : '启用' }}
              </FpButton>
              <FpButton size="sm" variant="danger" :disabled="busy" @click="removeSch(s)">删除</FpButton>
            </div>
          </li>
          <li v-if="!schedules.length" class="empty">暂无策略</li>
        </ul>
      </FpPanel>
    </div>
  </div>
</template>

<style scoped>
.ok { color: var(--fp-ok); font-size: 13px; font-weight: 600; }
.bad { color: var(--fp-danger); font-size: 13px; font-weight: 600; }
.status {
  display: inline-flex; align-items: center; height: 32px; padding: 0 12px;
  border-radius: var(--fp-radius-sm); border: 1px solid var(--fp-line-strong);
  font-size: 12px; font-weight: 600; color: var(--fp-muted); background: var(--fp-bg-elev);
}
.status.is-on { color: var(--fp-ok); border-color: rgba(15, 122, 78, 0.25); background: rgba(15, 122, 78, 0.06); }
.status.is-off { color: var(--fp-danger); border-color: rgba(194, 65, 12, 0.25); background: rgba(194, 65, 12, 0.05); }
.meta { font-size: 12px; font-weight: 500; color: var(--fp-faint); }

.metrics {
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 8px 0 22px;
}
.metric {
  background: var(--fp-bg-elev); border: 1px solid var(--fp-line); border-radius: var(--fp-radius-lg);
  padding: 16px 18px; box-shadow: var(--fp-shadow-sm);
  display: flex; flex-direction: column; gap: 4px;
}
.metric-label { font-size: 11px; font-weight: 600; color: var(--fp-faint); }
.metric strong { font-size: 22px; font-weight: 700; letter-spacing: -0.02em; }
.metric strong small { font-size: 13px; color: var(--fp-faint); font-weight: 500; }
.metric > span { font-size: 11px; color: var(--fp-muted); }

.section-head {
  display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; margin-bottom: 14px;
}
.eyebrow {
  margin: 0 0 6px; font-size: 10px; font-weight: 800; letter-spacing: 0.18em; color: var(--fp-brand);
}
.section-head h2 { margin: 0 0 6px; font-size: 20px; font-weight: 700; }
.sub { margin: 0; font-size: 12px; line-height: 1.7; color: var(--fp-muted); max-width: 560px; }
.legend {
  font-size: 11px; color: var(--fp-brand-2); background: var(--fp-brand-soft);
  padding: 8px 12px; border-radius: 999px; white-space: nowrap;
}

.rack-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;
}
.rack-group {
  border: 1px solid var(--fp-line); border-radius: var(--fp-radius-xl);
  background: #ecf3ed; padding: 16px;
}
.rack-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.group-badge {
  display: inline-flex; border-radius: 10px; background: #d4e9d7; color: #266b57;
  padding: 10px 12px; font-size: 12px; font-weight: 800; white-space: nowrap;
}
.rack-head h3 { margin: 0 0 2px; font-size: 16px; }
.rack-head p { margin: 0; font-size: 11px; color: #648076; }
.device-stack { display: flex; flex-direction: column; gap: 12px; }

.device-card {
  border: 1px solid var(--fp-line); background: var(--fp-bg-elev);
  border-radius: 14px; padding: 18px; box-shadow: var(--fp-shadow-sm);
}
.device-card.dirty { border-color: #a5d6bf; }
.device-card.offline { opacity: 0.72; }
.device-top {
  display: flex; justify-content: space-between; gap: 10px; align-items: flex-start;
  padding-bottom: 12px; border-bottom: 1px solid var(--fp-line);
}
.id-block { display: flex; gap: 12px; align-items: center; }
.idx {
  width: 40px; height: 40px; border-radius: 11px; background: #eaf3eb; color: #24755d;
  display: grid; place-items: center; font-size: 12px; font-weight: 800;
}
.device-top h4 { margin: 0 0 3px; font-size: 15px; font-weight: 700; }
.device-top p { margin: 0; font-size: 11px; color: var(--fp-muted); }
.pill {
  border-radius: 999px; background: #f1f3f2; color: #7b8785;
  padding: 6px 10px; font-size: 10px; font-weight: 700; white-space: nowrap;
}
.pill.on { background: #e4f6eb; color: #138368; }
.pill.off { background: #fff0e8; color: #a65f26; }

.channel { margin-top: 14px; }
.ch-label {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px; font-size: 12px; font-weight: 650; color: #607671;
}
.ch-label b { font-size: 11px; color: #87a099; }
.dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: var(--spectrum); margin-right: 6px;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--spectrum) 18%, white);
}
.ch-controls { display: flex; align-items: center; gap: 12px; }
.ch-controls input[type='range'] {
  flex: 1; min-width: 0; appearance: none; height: 7px; border-radius: 100px; outline: none;
  background: linear-gradient(
    to right,
    #1a967e 0%,
    #1a967e var(--fill, 0%),
    #edf1ed var(--fill, 0%),
    #edf1ed 100%
  );
}
.ch-controls input[type='range']::-webkit-slider-thumb {
  appearance: none; width: 18px; height: 18px; border-radius: 50%;
  border: 3px solid #fff; background: #138b76; box-shadow: 0 0 0 1px #acccc3;
}
.ch-controls input[type='number'] {
  width: 68px; height: 36px; border: 1px solid #d9e5df; border-radius: 9px;
  text-align: center; font-weight: 700; background: #fbfcfb; flex-shrink: 0;
}
.ch-sub { margin: 6px 0 0; font-size: 10px; color: #9cad9f; min-height: 14px; }

.device-foot {
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
  margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--fp-line);
  font-size: 10px; color: #78918a;
}
.ops { display: flex; gap: 6px; flex-shrink: 0; }

.bottom { margin-bottom: 16px; }
.bcast {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px;
}
.bcast label {
  display: grid; gap: 6px; font-size: 11px; font-weight: 600; color: var(--fp-faint);
}
.bcast input {
  height: 40px; border-radius: var(--fp-radius-sm); border: 1px solid var(--fp-line-strong);
  padding: 0 12px; background: var(--fp-bg-elev); font-weight: 700;
}
.bcast-ops { display: flex; flex-wrap: wrap; gap: 8px; }

.activity { list-style: none; margin: 0; padding: 0; max-height: 260px; overflow: auto; }
.activity li {
  display: flex; justify-content: space-between; gap: 10px;
  padding: 10px 0; border-bottom: 1px solid var(--fp-line); font-size: 12px;
}
.activity li.warning { color: var(--fp-warn); }
.activity li.error { color: var(--fp-danger); }
.activity time { font-size: 10px; color: var(--fp-faint); white-space: nowrap; }
.activity .empty, .sch .empty { color: var(--fp-faint); font-size: 13px; border: 0; }

.form { display: grid; gap: 12px; }
.form label {
  display: grid; gap: 5px; font-size: 11px; font-weight: 500; color: var(--fp-faint);
}
.form input, .form select {
  height: 40px; border-radius: var(--fp-radius-sm); border: 1px solid var(--fp-line-strong);
  padding: 0 12px; background: var(--fp-bg-elev); font-weight: 500;
}
.sch { list-style: none; margin: 0; padding: 0; }
.sch li {
  display: flex; justify-content: space-between; gap: 10px; align-items: center;
  padding: 14px 0; border-bottom: 1px solid var(--fp-line);
}
.sch li:last-child { border-bottom: 0; }
.sch .meta { font-size: 11px; color: var(--fp-faint); margin-top: 4px; }

@media (max-width: 960px) {
  .metrics, .rack-grid, .bcast { grid-template-columns: 1fr; }
  .legend { display: none; }
}
</style>
