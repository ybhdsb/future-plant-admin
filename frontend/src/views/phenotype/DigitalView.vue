<script setup lang="ts">
import { onMounted, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import CornPlantCard from '@/components/plant/CornPlantCard.vue'
import { getSpecimens } from '@/api/plant'

const specimens = ref<any[]>([])
const selected = ref<any | null>(null)

async function load() {
  specimens.value = await getSpecimens()
  if (!selected.value && specimens.value.length) selected.value = specimens.value[0]
  else if (selected.value) {
    selected.value =
      specimens.value.find((x) => x.plantCode === selected.value.plantCode) || specimens.value[0] || null
  }
}

function onSelect(p: any) {
  selected.value = p
}

onMounted(load)
</script>

<template>
  <div class="fp-page fp-page-wide">
    <FpPageHeader
      kicker="Digital Specimen"
      title="数字化植株"
      subtitle="标本级 3D 玉米模型 · 拖转观察 · 表型参数驱动形态比例。"
    >
      <template #actions>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
      </template>
    </FpPageHeader>

    <div class="layout">
      <FpPanel title="植株标本墙" desc="表型驱动的展示级 3D 玉米 · 拖转观察" flush>
        <div class="field">
          <div class="grid">
            <CornPlantCard
              v-for="p in specimens"
              :key="p.plantCode"
              :specimen="p"
              :active="selected?.plantCode === p.plantCode"
              @select="onSelect"
            />
          </div>
        </div>
      </FpPanel>

      <FpPanel
        :title="selected ? `${selected.plantCode}` : '选择植株'"
        :desc="selected ? `${selected.cropType || '玉米'} / ${selected.variety || '—'} / ${selected.growthStage || '—'}` : '点击卡片查看表型详情'"
      >
        <template v-if="selected">
          <div class="kv">
            <div><span>穴位</span><b>{{ selected.slotCode || '--' }}</b></div>
            <div><span>生育期</span><b>{{ selected.growthStage || '--' }}</b></div>
            <div><span>状态</span><b>{{ selected.status || '--' }}</b></div>
            <div><span>位置</span><b>行 {{ selected.posRow || '-' }} / 列 {{ selected.posCol || '-' }}</b></div>
          </div>
          <div class="chips">
            <div class="chip"><div class="n">株高</div><div class="v">{{ selected.latest?.height_cm ?? '--' }} <small>cm</small></div></div>
            <div class="chip"><div class="n">叶片数</div><div class="v">{{ selected.latest?.leaf_count ?? '--' }} <small>片</small></div></div>
            <div class="chip"><div class="n">茎粗</div><div class="v">{{ selected.latest?.stem_diameter_mm ?? '--' }} <small>mm</small></div></div>
            <div class="chip"><div class="n">叶面积</div><div class="v">{{ selected.latest?.leaf_area_cm2 ?? '--' }} <small>cm²</small></div></div>
            <div class="chip"><div class="n">SPAD</div><div class="v">{{ selected.latest?.spad ?? '--' }}</div></div>
          </div>
        </template>
        <p v-else class="empty">先选择一株玉米</p>
      </FpPanel>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
}
.field {
  min-height: 520px;
  background: var(--fp-bg-soft);
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px 14px;
  padding: 22px 18px 28px;
  align-items: end;
}
.kv { display: grid; gap: 0; font-size: 12.5px; }
.kv > div {
  display: flex; justify-content: space-between; gap: 10px;
  padding: 9px 0; border-bottom: 1px solid var(--fp-line);
}
.kv > div:last-child { border-bottom: 0; }
.kv span { color: var(--fp-faint); font-weight: 500; font-size: 11.5px; }
.kv b { font-weight: 600; color: var(--fp-ink); font-family: var(--fp-mono); font-size: 12.5px; }
.chips {
  display: grid; grid-template-columns: 1fr 1fr; gap: 0; margin-top: 12px;
  border-top: 1px solid var(--fp-line);
}
.chip {
  padding: 12px 0 10px;
  border-bottom: 1px solid var(--fp-line);
}
.chip:nth-child(odd) { padding-right: 12px; }
.chip:nth-child(even) { padding-left: 12px; border-left: 1px solid var(--fp-line); }
.chip .n {
  font-size: 10.5px; font-weight: 500; color: var(--fp-faint); font-family: var(--fp-mono);
  letter-spacing: 0.06em; text-transform: uppercase;
}
.chip .v {
  font-size: 18px; font-weight: 600; letter-spacing: -0.03em;
  color: var(--fp-ink); margin-top: 5px; font-variant-numeric: tabular-nums;
  font-family: var(--fp-mono);
}
.chip small { font-size: 10.5px; font-weight: 500; color: var(--fp-faint); font-family: var(--fp-font); }
.empty { color: var(--fp-faint); font-size: 12.5px; }
@media (max-width: 980px) {
  .layout { grid-template-columns: 1fr; }
  .grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
