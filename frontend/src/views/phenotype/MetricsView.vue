<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import FpChart from '@/components/ui/FpChart.vue'
import { getPhenotypeMetrics, getSpecimens } from '@/api/plant'

const METRICS = [
  { value: 'height_cm', label: '株高 height_cm' },
  { value: 'leaf_count', label: '叶片数 leaf_count' },
  { value: 'stem_diameter_mm', label: '茎粗 stem_diameter_mm' },
  { value: 'leaf_area_cm2', label: '叶面积 leaf_area_cm2' },
  { value: 'spad', label: 'SPAD' },
]

const specimens = ref<any[]>([])
const plantCode = ref('')
const metric = ref('height_cm')
const points = ref<any[]>([])

const option = computed(() => {
  const byPlant: Record<string, any[]> = {}
  points.value.forEach((p) => {
    ;(byPlant[p.plantCode] ||= []).push([p.sampledAt, p.value])
  })
  return {
    color: ['#0d9488', '#0284c8', '#ca8a04', '#16a34a', '#7c3aed', '#db2777'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 44, right: 16, top: 40, bottom: 28 },
    xAxis: { type: 'time' },
    yAxis: { type: 'value' },
    series: Object.keys(byPlant).map((code) => ({
      name: code,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: byPlant[code],
    })),
  }
})

async function load() {
  specimens.value = await getSpecimens()
  points.value = await getPhenotypeMetrics({
    plantCode: plantCode.value || undefined,
    metric: metric.value,
    limit: 300,
  })
}

onMounted(load)
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Phenotype"
      title="表型指标 / 表型历史"
      subtitle="按植株与指标筛选，查看生长曲线与明细表。"
    >
      <template #actions>
        <select v-model="plantCode" class="sel" @change="load">
          <option value="">全部植株</option>
          <option v-for="p in specimens" :key="p.plantCode" :value="p.plantCode">
            {{ p.plantCode }} · {{ p.slotCode }}
          </option>
        </select>
        <select v-model="metric" class="sel" @change="load">
          <option v-for="m in METRICS" :key="m.value" :value="m.value">{{ m.label }}</option>
        </select>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
      </template>
    </FpPageHeader>

    <div class="fp-stack">
      <FpPanel :title="`趋势 · ${metric}`">
        <FpChart :option="option" height="340px" />
      </FpPanel>
      <FpPanel title="数据表" flush>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>时间</th><th>植株</th><th>指标</th><th>数值</th><th>单位</th><th>来源</th></tr>
            </thead>
            <tbody>
              <tr v-for="p in points" :key="p.id">
                <td>{{ p.sampledAt }}</td>
                <td>{{ p.plantCode }}</td>
                <td>{{ p.metric }}</td>
                <td>{{ p.value }}</td>
                <td>{{ p.unit || '-' }}</td>
                <td>{{ p.source || '-' }}</td>
              </tr>
              <tr v-if="!points.length"><td colspan="6" class="empty">暂无数据</td></tr>
            </tbody>
          </table>
        </div>
      </FpPanel>
    </div>
  </div>
</template>

<style scoped>
.sel {
  height: 32px; border-radius: 999px; border: 1px solid var(--fp-line);
  padding: 0 12px; background: var(--fp-bg-elev); font-size: 12px; font-weight: 600;
}
.table-wrap { overflow: auto; max-height: 420px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--fp-line); }
th { font-size: 11px; font-weight: 500; color: var(--fp-faint); background: var(--fp-bg-soft); }
.empty { color: var(--fp-faint); text-align: center; }
</style>
