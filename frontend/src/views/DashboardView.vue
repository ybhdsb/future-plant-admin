<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpStatCard from '@/components/ui/FpStatCard.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import FpChart from '@/components/ui/FpChart.vue'
import { DEVICE_KEY, getDashboard, setMock } from '@/api/plant'
import { fpChartBase } from '@/utils/chartTheme'

const LABELS: Record<string, { name: string; unit: string }> = {
  'air.temperature': { name: '空气温度', unit: '°C' },
  'air.humidity': { name: '空气湿度', unit: '%RH' },
  'air.co2': { name: 'CO₂', unit: 'ppm' },
  'nutrient.ph': { name: '营养液 pH', unit: '' },
  'nutrient.ec': { name: '营养液 EC', unit: 'mS/cm' },
  'nutrient.do': { name: '溶氧', unit: 'mg/L' },
  'nutrient.level': { name: '液位', unit: '' },
}
const HERO_KEYS = ['air.temperature', 'air.humidity', 'nutrient.ph', 'nutrient.ec']
const MORE_KEYS = ['air.co2', 'nutrient.do', 'nutrient.level']

const loading = ref(true)
const dash = ref<Record<string, any> | null>(null)
const err = ref('')
let timer: number | undefined

function ageText(v?: string) {
  if (!v) return '暂无采样'
  const d = new Date(v).getTime()
  if (isNaN(d)) return '暂无采样'
  const sec = Math.max(0, Math.floor((Date.now() - d) / 1000))
  if (sec < 60) return `${sec}s 前`
  if (sec < 3600) return `${Math.floor(sec / 60)}m 前`
  return `${Math.floor(sec / 3600)}h 前`
}

function metricOf(key: string) {
  return dash.value?.metrics?.[key]
}

const freshText = computed(() => {
  const times = [...HERO_KEYS, ...MORE_KEYS].map((k) => metricOf(k)?.sampledAt).filter(Boolean)
  if (!times.length) return '等待数据'
  times.sort()
  return ageText(times[times.length - 1])
})

const chartOption = computed(() => {
  const seriesMap = dash.value?.series || {}
  const keys = HERO_KEYS
  return fpChartBase({
    series: keys.map((k, idx) => ({
      name: LABELS[k].name,
      type: 'line',
      smooth: 0.35,
      showSymbol: false,
      lineStyle: { width: 2 },
      data: (seriesMap[k] || []).map((p: any) => [p.sampledAt, p.value]),
      areaStyle:
        idx === 0
          ? {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: 'rgba(10, 122, 110, 0.16)' },
                  { offset: 1, color: 'rgba(10, 122, 110, 0)' },
                ],
              },
            }
          : undefined,
    })),
  })
})

async function load() {
  try {
    err.value = ''
    dash.value = await getDashboard(DEVICE_KEY)
  } catch (e: any) {
    err.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleMock(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  await setMock(checked)
  await load()
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 5000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Operations"
      title="运行看板"
      subtitle="控制器状态、关键环境测点与近时趋势。"
    >
      <template #actions>
        <span class="status" :class="dash?.online ? 'is-on' : 'is-off'">
          <i />{{ dash?.online ? '控制器在线' : '控制器离线' }}
        </span>
        <span class="meta">{{ freshText }}</span>
        <label class="mock">
          <input type="checkbox" :checked="!!dash?.mockEnabled" @change="toggleMock" />
          Mock
        </label>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
        <RouterLink to="/control/led"><FpButton size="sm">设备控制</FpButton></RouterLink>
      </template>
    </FpPageHeader>

    <p v-if="err" class="err">{{ err }}</p>

    <div class="fp-stack">
      <section class="hero">
        <div class="hero-kpis">
          <FpStatCard
            label="测点"
            :value="dash?.summary?.metricCount ?? 0"
            hint="已接入指标"
            tone="brand"
          />
          <FpStatCard label="LED" :value="`${dash?.summary?.ledOnCount ?? 0}/8`" hint="通道开启" />
          <FpStatCard
            label="泵"
            :value="dash?.summary?.pumpOnCount ?? 0"
            hint="水泵 / 氧泵"
            tone="ok"
          />
          <FpStatCard
            label="今日指令"
            :value="dash?.summary?.commandCount ?? 0"
            hint="日志条数"
            tone="warn"
          />
        </div>
        <div class="hero-metrics">
          <div v-for="k in HERO_KEYS" :key="k" class="metric">
            <div class="m-label">{{ LABELS[k].name }}</div>
            <div class="m-value">
              {{ metricOf(k)?.value == null ? '—' : metricOf(k).value }}
              <small>{{ metricOf(k)?.unit || LABELS[k].unit }}</small>
            </div>
            <div class="m-hint">
              {{ metricOf(k) ? ageText(metricOf(k).sampledAt) : '未接入' }}
            </div>
          </div>
        </div>
      </section>

      <div class="fp-grid-2">
        <FpPanel title="近 6 小时趋势" desc="温度 · 湿度 · pH · EC">
          <FpChart :option="chartOption" height="300px" />
        </FpPanel>

        <div class="side-col">
          <FpPanel title="次要测点" desc="CO₂ · 溶氧 · 液位">
            <div class="mini-metrics">
              <div v-for="k in MORE_KEYS" :key="k" class="mini">
                <span>{{ LABELS[k].name }}</span>
                <b>
                  {{ metricOf(k)?.value == null ? '—' : metricOf(k).value }}
                  <small>{{ metricOf(k)?.unit || LABELS[k].unit }}</small>
                </b>
              </div>
            </div>
          </FpPanel>

          <FpPanel title="执行器" desc="当前开关摘要">
            <div class="chips">
              <span
                v-for="a in dash?.actuators || []"
                :key="a.actuatorId"
                class="chip"
                :class="{ on: !!a.state?.on }"
              >
                {{ a.actuatorId }}
                <em>{{ a.state?.on ? 'ON' : 'OFF' }}</em>
              </span>
              <span v-if="!(dash?.actuators || []).length" class="empty">暂无状态</span>
            </div>
          </FpPanel>

          <FpPanel title="最近事件" desc="指令与告警">
            <ul class="list">
              <li v-for="c in (dash?.recentCommands || []).slice(0, 4)" :key="c.commandId || c.id">
                <div>
                  <div class="msg">{{ c.commandType }}</div>
                  <div class="t">{{ c.status }}</div>
                </div>
                <div class="t">{{ c.createdAt }}</div>
              </li>
              <li v-for="e in (dash?.events || []).slice(0, 3)" :key="e.id">
                <div class="msg">{{ e.message || e.code || e.level }}</div>
                <div class="t">{{ e.createdAt }}</div>
              </li>
              <li
                v-if="!(dash?.recentCommands || []).length && !(dash?.events || []).length"
                class="empty"
              >
                运行平稳，暂无新事件
              </li>
            </ul>
          </FpPanel>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.err {
  color: var(--fp-danger);
  font-size: 13px;
}
.status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border-radius: var(--fp-radius-sm);
  font-size: 12px;
  font-weight: 600;
  border: 1px solid var(--fp-line-strong);
  background: var(--fp-bg-elev);
  color: var(--fp-muted);
}
.status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
}
.status.is-on {
  color: var(--fp-ok);
  border-color: rgba(15, 122, 78, 0.25);
  background: rgba(15, 122, 78, 0.06);
}
.status.is-on i {
  background: var(--fp-ok);
}
.status.is-off {
  color: var(--fp-danger);
  border-color: rgba(194, 65, 12, 0.25);
  background: rgba(194, 65, 12, 0.05);
}
.status.is-off i {
  background: var(--fp-danger);
}
.meta {
  font-size: 12px;
  font-weight: 500;
  color: var(--fp-faint);
  font-variant-numeric: tabular-nums;
}
.mock {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fp-muted);
}
.hero {
  display: grid;
  grid-template-columns: 1fr 1.15fr;
  gap: 28px;
  padding: 8px 0 4px;
}
.hero-kpis {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 24px;
}
.hero-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px 28px;
  padding: 8px 0 8px 28px;
  border-left: 1px solid var(--fp-line);
}
.metric .m-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--fp-faint);
}
.metric .m-value {
  margin-top: 8px;
  font-size: 32px;
  font-weight: 600;
  letter-spacing: -0.045em;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.metric .m-value small {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--fp-faint);
  letter-spacing: 0;
}
.metric .m-hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--fp-faint);
}
.side-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.mini-metrics {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mini {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--fp-line);
  font-size: 13px;
  color: var(--fp-muted);
}
.mini:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.mini b {
  font-size: 16px;
  font-weight: 600;
  color: var(--fp-ink);
  font-variant-numeric: tabular-nums;
}
.mini small {
  margin-left: 3px;
  font-size: 11px;
  font-weight: 500;
  color: var(--fp-faint);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--fp-radius-sm);
  font-size: 11px;
  font-weight: 600;
  color: var(--fp-muted);
  background: var(--fp-bg-soft);
  border: 1px solid var(--fp-line);
}
.chip em {
  font-style: normal;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--fp-faint);
}
.chip.on {
  color: var(--fp-brand-2);
  background: var(--fp-brand-soft);
  border-color: rgba(10, 122, 110, 0.2);
}
.chip.on em {
  color: var(--fp-brand);
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.msg {
  font-size: 13px;
  font-weight: 600;
  color: var(--fp-ink-2);
}
.t {
  font-size: 11px;
  color: var(--fp-faint);
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.empty {
  color: var(--fp-faint);
  font-size: 13px;
}
@media (max-width: 960px) {
  .hero {
    grid-template-columns: 1fr;
  }
  .hero-metrics {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid var(--fp-line);
    padding-top: 18px;
  }
}
</style>
