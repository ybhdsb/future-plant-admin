<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import FpChart from '@/components/ui/FpChart.vue'
import { getMedia, getMetricsMulti } from '@/api/plant'

const ALL_METRICS = [
  { key: 'air.temperature', label: '空气温度' },
  { key: 'air.humidity', label: '空气湿度' },
  { key: 'air.co2', label: 'CO₂' },
  { key: 'nutrient.ph', label: '营养液 pH' },
  { key: 'nutrient.ec', label: '营养液 EC' },
]

const selected = ref<string[]>(ALL_METRICS.map((m) => m.key))
const from = ref('')
const to = ref('')
const series = ref<Record<string, any[]>>({})
const media = ref<any[]>([])
const pointCount = computed(() =>
  Object.values(series.value).reduce((n, arr) => n + (arr?.length || 0), 0),
)

function pad(n: number) {
  return n < 10 ? `0${n}` : String(n)
}
function fmtLocal(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function toApi(v: string) {
  if (!v) return undefined
  return v.replace('T', ' ') + ':00'
}

function setPreset(hours: number) {
  const end = new Date()
  const start = new Date(end.getTime() - hours * 3600 * 1000)
  from.value = fmtLocal(start)
  to.value = fmtLocal(end)
  load()
}

const option = computed(() => {
  const labels: Record<string, string> = Object.fromEntries(ALL_METRICS.map((m) => [m.key, m.label]))
  const keys = selected.value.filter((k) => series.value[k]?.length)
  return {
    color: ['#0d9488', '#0284c8', '#d97706', '#7c3aed', '#16a34a'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 44, right: 16, top: 40, bottom: 28 },
    xAxis: { type: 'time' },
    yAxis: { type: 'value' },
    series: keys.map((k) => ({
      name: labels[k] || k,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: (series.value[k] || []).map((p) => [p.sampledAt, p.value]),
    })),
  }
})

const detailRows = computed(() => {
  const rows: any[] = []
  selected.value.forEach((k) => {
    ;(series.value[k] || []).forEach((p) => {
      rows.push({ ...p, metric: k })
    })
  })
  rows.sort((a, b) => String(b.sampledAt).localeCompare(String(a.sampledAt)))
  return rows.slice(0, 100)
})

async function load() {
  if (!selected.value.length) {
    series.value = {}
    return
  }
  series.value = await getMetricsMulti({
    metrics: selected.value.join(','),
    from: toApi(from.value),
    to: toApi(to.value),
    limit: 500,
  })
  media.value = await getMedia(undefined, 12)
}

onMounted(() => {
  setPreset(6)
})
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="History"
      title="环境历史"
      subtitle="多指标时序曲线与关联影像快照。可按时间范围与测点筛选。"
    >
      <template #actions>
        <span class="pill">点数 {{ pointCount }}</span>
        <FpButton variant="secondary" size="sm" @click="setPreset(6)">近 6 小时</FpButton>
        <FpButton variant="secondary" size="sm" @click="setPreset(24)">近 24 小时</FpButton>
        <FpButton size="sm" @click="load">查询</FpButton>
      </template>
    </FpPageHeader>

    <div class="fp-stack">
      <FpPanel title="筛选条件">
        <div class="filters">
          <label>开始<input v-model="from" type="datetime-local" /></label>
          <label>结束<input v-model="to" type="datetime-local" /></label>
          <div class="checks">
            <label v-for="m in ALL_METRICS" :key="m.key" class="ck">
              <input v-model="selected" type="checkbox" :value="m.key" />
              {{ m.label }}
            </label>
          </div>
        </div>
      </FpPanel>

      <FpPanel title="环境曲线">
        <FpChart :option="option" height="360px" />
      </FpPanel>

      <div class="fp-grid-2">
        <FpPanel title="数据明细" desc="最多展示 100 条" flush>
          <div class="table-wrap">
            <table>
              <thead>
                <tr><th>时间</th><th>测点</th><th>数值</th><th>单位</th><th>质量</th></tr>
              </thead>
              <tbody>
                <tr v-for="(r, idx) in detailRows" :key="idx">
                  <td>{{ r.sampledAt }}</td>
                  <td>{{ r.metric }}</td>
                  <td>{{ r.value }}</td>
                  <td>{{ r.unit || '-' }}</td>
                  <td>{{ r.quality || '-' }}</td>
                </tr>
                <tr v-if="!detailRows.length"><td colspan="5" class="empty">暂无数据</td></tr>
              </tbody>
            </table>
          </div>
        </FpPanel>
        <FpPanel title="关联影像">
          <div class="grid">
            <a
              v-for="m in media"
              :key="m.id"
              class="card"
              :href="m.url || `/plant/api/media/${m.id}/file`"
              target="_blank"
              rel="noopener"
            >
              <img v-if="m.url || m.id" :src="m.url || `/plant/api/media/${m.id}/file`" alt="" @error="($event.target as HTMLImageElement).style.display='none'" />
              <div class="bd">
                <b>{{ m.label || m.id }}</b>
                <span>{{ m.capturedAt || '-' }}</span>
              </div>
            </a>
            <div v-if="!media.length" class="empty">暂无影像</div>
          </div>
        </FpPanel>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pill {
  height: 28px; padding: 0 10px; border-radius: var(--fp-radius-sm); display: inline-flex; align-items: center;
  border: 1px solid var(--fp-line-strong); background: var(--fp-bg-elev); font-size: 11.5px; font-weight: 500; color: var(--fp-muted);
}
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
.filters label { display: grid; gap: 4px; font-size: 12px; font-weight: 700; color: var(--fp-muted); }
.filters input[type='datetime-local'] {
  height: 36px; border-radius: 10px; border: 1px solid var(--fp-line); padding: 0 10px; background: var(--fp-bg-elev);
}
.checks { display: flex; flex-wrap: wrap; gap: 10px; }
.ck { display: inline-flex !important; align-items: center; gap: 6px; font-weight: 600 !important; }
.table-wrap { overflow: auto; max-height: 360px; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th, td { padding: 8px 12px; border-bottom: 1px solid var(--fp-line); text-align: left; }
th { background: var(--fp-bg-soft); color: var(--fp-faint); font-weight: 500; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
.card {
  border: 1px solid var(--fp-line); border-radius: 12px; overflow: hidden; background: var(--fp-bg-elev); color: inherit;
}
.card img { width: 100%; height: 90px; object-fit: cover; background: rgba(46, 196, 167, 0.08); display: block; }
.bd { padding: 8px 10px; display: flex; flex-direction: column; gap: 2px; font-size: 11px; }
.bd span { color: var(--fp-faint); }
.empty { color: var(--fp-faint); font-size: 13px; text-align: center; }
</style>
