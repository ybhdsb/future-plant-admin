<script setup lang="ts">
import { onMounted, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import { DEVICE_KEY, exportTelemetryUrl, getReadings } from '@/api/plant'

const rows = ref<any[]>([])
const count = ref(0)
const deviceKey = ref(DEVICE_KEY)
const metric = ref('')
const from = ref('')
const to = ref('')

const METRICS = [
  { value: '', label: '全部指标' },
  { value: 'air.temperature', label: '空气温度' },
  { value: 'air.humidity', label: '空气湿度' },
  { value: 'air.co2', label: 'CO₂' },
  { value: 'nutrient.ph', label: '营养液 pH' },
  { value: 'nutrient.ec', label: '营养液 EC' },
  { value: 'nutrient.do', label: '溶氧' },
  { value: 'nutrient.level', label: '液位' },
]

function toApi(v: string) {
  if (!v) return undefined
  return v.replace('T', ' ') + ':00'
}

async function load() {
  const res = await getReadings({
    deviceKey: deviceKey.value || DEVICE_KEY,
    metric: metric.value || undefined,
    from: toApi(from.value),
    to: toApi(to.value),
    page: 0,
    size: 100,
  })
  rows.value = res.rows || []
  count.value = res.count || 0
}

function download(format: string) {
  window.open(
    exportTelemetryUrl({
      deviceKey: deviceKey.value || DEVICE_KEY,
      metric: metric.value || undefined,
      from: toApi(from.value),
      to: toApi(to.value),
      format,
    }),
    '_blank',
  )
}

onMounted(load)
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Data"
      title="数据管理"
      subtitle="浏览传感读数并导出 CSV / Excel，支持设备、指标与时间范围过滤。"
    >
      <template #actions>
        <FpButton variant="secondary" size="sm" @click="load">预览</FpButton>
        <FpButton size="sm" @click="download('csv')">导出 CSV</FpButton>
        <FpButton size="sm" variant="secondary" @click="download('excel')">导出 Excel</FpButton>
      </template>
    </FpPageHeader>

    <div class="fp-stack">
      <FpPanel title="查询条件">
        <div class="filters">
          <label>Device Key<input v-model="deviceKey" /></label>
          <label>
            指标
            <select v-model="metric">
              <option v-for="m in METRICS" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
          </label>
          <label>开始<input v-model="from" type="datetime-local" /></label>
          <label>结束<input v-model="to" type="datetime-local" /></label>
        </div>
      </FpPanel>

      <FpPanel :title="`读数预览（${count}）`" flush>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>设备</th>
                <th>指标</th>
                <th>数值</th>
                <th>单位</th>
                <th>质量</th>
                <th>采样时间</th>
                <th>入库时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in rows" :key="r.id">
                <td>{{ r.id }}</td>
                <td>{{ r.deviceKey }}</td>
                <td>{{ r.metric }}</td>
                <td>{{ r.value }}</td>
                <td>{{ r.unit || '-' }}</td>
                <td>{{ r.quality || '-' }}</td>
                <td>{{ r.sampledAt }}</td>
                <td>{{ r.receivedAt || '-' }}</td>
              </tr>
              <tr v-if="!rows.length"><td colspan="8" class="empty">暂无数据</td></tr>
            </tbody>
          </table>
        </div>
      </FpPanel>
    </div>
  </div>
</template>

<style scoped>
.filters { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.filters label { display: grid; gap: 4px; font-size: 12px; font-weight: 700; color: var(--fp-muted); }
.filters input, .filters select {
  height: 36px; border-radius: 10px; border: 1px solid var(--fp-line); padding: 0 10px; background: var(--fp-bg-elev);
}
.table-wrap { overflow: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--fp-line); }
th { font-size: 11px; font-weight: 500; color: var(--fp-faint); background: var(--fp-bg-soft); }
.empty { color: var(--fp-faint); text-align: center; }
@media (max-width: 900px) { .filters { grid-template-columns: 1fr 1fr; } }
</style>
