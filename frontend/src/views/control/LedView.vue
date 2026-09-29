<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import {
  DEVICE_KEY,
  deleteLedSchedule,
  getDashboard,
  getLedSchedules,
  postCommand,
  saveLedSchedule,
  setLedScheduleEnabled,
} from '@/api/plant'

const LED_CHANNELS: Record<number, { name: string; wave: string; color: string }> = {
  1: { name: '深红', wave: '660nm', color: '#e11d48' },
  2: { name: '蓝光', wave: '450nm', color: '#2563eb' },
  3: { name: '白光', wave: '全光谱', color: '#94a3b8' },
  4: { name: '远红', wave: '730nm', color: '#9f1239' },
  5: { name: '红光', wave: '630nm', color: '#dc2626' },
  6: { name: '蓝光', wave: '460nm', color: '#3b82f6' },
  7: { name: '暖白', wave: '3000K', color: '#f59e0b' },
  8: { name: '紫外', wave: 'UVA', color: '#7c3aed' },
}

const brightness = ref<Record<string, number>>({})
const schedules = ref<any[]>([])
const dash = ref<any>(null)
const msg = ref('')
const dirty = ref(false)
const bulk = ref(80)
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

for (let i = 1; i <= 8; i++) brightness.value[`led.ch${i}`] = 0

const channelList = computed(() =>
  Array.from({ length: 8 }, (_, i) => {
    const n = i + 1
    return { n, id: `led.ch${n}`, ...LED_CHANNELS[n] }
  }),
)

async function load() {
  dash.value = await getDashboard()
  schedules.value = await getLedSchedules()
  const map: Record<string, any> = {}
  ;(dash.value?.actuators || []).forEach((a: any) => {
    map[a.actuatorId] = a.state || {}
  })
  channelList.value.forEach((c) => {
    if (map[c.id]?.brightness != null) brightness.value[c.id] = Number(map[c.id].brightness)
  })
  dirty.value = false
}

function onSlide() {
  dirty.value = true
}

function fillBulk() {
  channelList.value.forEach((c) => {
    brightness.value[c.id] = Number(bulk.value) || 0
  })
  dirty.value = true
}

async function applyChannels(chs: { actuatorId: string; brightness: number }[]) {
  await postCommand({
    deviceKey: DEVICE_KEY,
    clientRequestId: `ui-${Date.now()}`,
    commandType: 'LED_SET',
    payload: { channels: chs },
  })
  dirty.value = false
  msg.value = '光谱指令已下发'
  await load()
}

async function applyLive() {
  await applyChannels(
    channelList.value.map((c) => ({
      actuatorId: c.id,
      brightness: brightness.value[c.id] || 0,
    })),
  )
}

async function applyBulk() {
  fillBulk()
  await applyLive()
}

async function allOff() {
  channelList.value.forEach((c) => {
    brightness.value[c.id] = 0
  })
  await applyLive()
}

async function saveSchedule() {
  await saveLedSchedule({ deviceKey: DEVICE_KEY, ...form.value })
  msg.value = '策略已保存'
  await load()
}

async function toggleSch(s: any) {
  await setLedScheduleEnabled(s.id, !s.enabled)
  await load()
}

async function removeSch(s: any) {
  await deleteLedSchedule(s.id)
  await load()
}

function paramText(s: any) {
  if (s.scheduleType === 'INTERVAL') {
    return `开 ${s.onMinutes || 0} 分钟 / 关 ${s.offMinutes || 0} 分钟`
  }
  return `${s.onTime || '--'} ~ ${s.offTime || '--'}`
}

async function refresh() {
  if (dirty.value && !confirm('有未应用到设备的亮度修改，确定刷新并丢弃？')) return
  await load()
}

onMounted(load)
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Lighting"
      title="LED 光谱"
      subtitle="八通道亮度与定时策略。滑条改动后需点「应用到设备」。"
    >
      <template #actions>
        <span class="status" :class="dash?.online ? 'is-on' : 'is-off'">{{ dash?.online ? '在线' : '离线' }}</span>
        <span class="meta">{{ dash?.mockEnabled ? 'Mock' : '实机' }}</span>
        <FpButton variant="secondary" size="sm" @click="refresh">刷新</FpButton>
      </template>
    </FpPageHeader>
    <p v-if="msg" class="ok">{{ msg }}</p>

    <div class="fp-stack">
      <FpPanel title="手动调节亮度" desc="先改滑条或批量亮度，再点「应用到设备」发送控制指令">
        <template #extra>
          <FpButton variant="secondary" size="sm" @click="allOff">全部关闭</FpButton>
          <FpButton size="sm" @click="applyLive">应用到设备</FpButton>
        </template>
        <div class="bulk">
          <b>批量设置全部通道</b>
          <input v-model.number="bulk" type="number" min="0" max="100" />
          <span>%</span>
          <FpButton variant="ghost" size="sm" @click="fillBulk">填入滑条</FpButton>
          <FpButton variant="ghost" size="sm" @click="applyBulk">填入并应用到设备</FpButton>
          <span v-if="dirty" class="dirty">有未应用修改</span>
        </div>
        <div class="board">
          <div
            v-for="c in channelList"
            :key="c.id"
            class="card"
            :class="{ active: (brightness[c.id] || 0) > 0 }"
            :style="{ '--led-color': c.color }"
          >
            <div class="hd">
              <span class="name">
                <i class="swatch" />
                CH{{ c.n }} · {{ c.name }}
                <small>{{ c.wave }}</small>
              </span>
              <b>{{ brightness[c.id] || 0 }}%</b>
            </div>
            <input
              v-model.number="brightness[c.id]"
              type="range"
              min="0"
              max="100"
              @input="onSlide"
            />
          </div>
        </div>
      </FpPanel>

      <div class="fp-grid-2">
        <FpPanel title="定时补光策略" desc="每天固定时段 / 开关间隔循环">
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
            <FpButton @click="saveSchedule">保存策略</FpButton>
          </div>
        </FpPanel>
        <FpPanel title="已有策略">
          <ul class="sch">
            <li v-for="s in schedules" :key="s.id">
              <div>
                <b>{{ s.name }} · {{ s.brightness }}%</b>
                <div class="meta">
                  {{ s.scheduleType === 'INTERVAL' ? '间隔循环' : '每天时段' }} · {{ paramText(s) }}
                  <span v-if="s.remark"> · {{ s.remark }}</span>
                </div>
              </div>
              <div class="ops">
                <FpButton size="sm" variant="secondary" @click="toggleSch(s)">
                  {{ s.enabled ? '停用' : '启用' }}
                </FpButton>
                <FpButton size="sm" variant="danger" @click="removeSch(s)">删除</FpButton>
              </div>
            </li>
            <li v-if="!schedules.length" class="empty">暂无策略</li>
          </ul>
        </FpPanel>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ok { color: var(--fp-ok); font-size: 13px; font-weight: 600; }
.status {
  display: inline-flex; align-items: center; height: 32px; padding: 0 12px;
  border-radius: var(--fp-radius-sm); border: 1px solid var(--fp-line-strong);
  font-size: 12px; font-weight: 600; color: var(--fp-muted); background: var(--fp-bg-elev);
}
.status.is-on { color: var(--fp-ok); border-color: rgba(15, 122, 78, 0.25); background: rgba(15, 122, 78, 0.06); }
.status.is-off { color: var(--fp-danger); border-color: rgba(194, 65, 12, 0.25); background: rgba(194, 65, 12, 0.05); }
.meta { font-size: 12px; font-weight: 500; color: var(--fp-faint); }
.bulk {
  display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
  margin-bottom: 18px; padding-bottom: 16px; border-bottom: 1px solid var(--fp-line);
  font-size: 13px; font-weight: 500;
}
.bulk input {
  width: 72px; height: 32px; border-radius: var(--fp-radius-sm);
  border: 1px solid var(--fp-line-strong); padding: 0 8px; background: var(--fp-bg-elev);
}
.dirty { color: var(--fp-warn); font-size: 12px; font-weight: 600; }
.board { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.card {
  border: 1px solid var(--fp-line); border-radius: var(--fp-radius); padding: 14px;
  background: var(--fp-bg-soft); border-top: 2px solid var(--led-color, #94a3b8);
}
.card.active { background: var(--fp-bg-elev); }
.hd {
  display: flex; justify-content: space-between; gap: 8px; font-size: 12px;
  margin-bottom: 10px; font-weight: 600; color: var(--fp-muted);
}
.name { display: inline-flex; align-items: center; gap: 8px; color: var(--fp-ink-2); }
.name small { color: var(--fp-faint); font-weight: 500; margin-left: 4px; }
.swatch { width: 8px; height: 8px; border-radius: 50%; background: var(--led-color); }
.card input[type='range'] { width: 100%; accent-color: var(--led-color); }
.form { display: grid; gap: 12px; }
.form label {
  display: grid; gap: 6px; font-size: 11px; font-weight: 600;
  letter-spacing: 0.04em; text-transform: uppercase; color: var(--fp-faint);
}
.form input, .form select {
  height: 40px; border-radius: var(--fp-radius-sm); border: 1px solid var(--fp-line-strong);
  padding: 0 12px; background: var(--fp-bg-elev); text-transform: none; letter-spacing: 0; font-weight: 500; color: var(--fp-ink);
}
.sch { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0; }
.sch li {
  display: flex; justify-content: space-between; gap: 10px; align-items: center;
  padding: 14px 0; border-bottom: 1px solid var(--fp-line);
}
.sch li:last-child { border-bottom: 0; }
.sch .meta { font-size: 11px; color: var(--fp-faint); margin-top: 4px; text-transform: none; letter-spacing: 0; }
.ops { display: flex; gap: 6px; }
.empty { color: var(--fp-faint); font-size: 13px; }
@media (max-width: 900px) { .board { grid-template-columns: 1fr 1fr; } }
</style>
