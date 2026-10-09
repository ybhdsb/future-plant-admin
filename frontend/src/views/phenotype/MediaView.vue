<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import { getPhenotypeMedia, getSpecimens } from '@/api/plant'

const rows = ref<any[]>([])
const specimens = ref<any[]>([])
const plantCode = ref('')
const modality = ref('')

const filtered = computed(() => {
  return rows.value.filter((m) => {
    if (plantCode.value && m.plantCode !== plantCode.value) return false
    if (modality.value) {
      const mod = String(m.modality || m.mediaType || '').toUpperCase()
      if (!mod.includes(modality.value.toUpperCase())) return false
    }
    return true
  })
})

function mediaUrl(m: any) {
  return m.url || (m.id != null ? `/plant/api/media/${m.id}/file` : '')
}

async function load() {
  ;[rows.value, specimens.value] = await Promise.all([getPhenotypeMedia(), getSpecimens()])
}

onMounted(load)
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Multimodal"
      title="多模态影像"
      subtitle="表型分析关联的影像样本，可按植株与模态筛选。"
    >
      <template #actions>
        <select v-model="plantCode" class="sel">
          <option value="">全部植株</option>
          <option v-for="p in specimens" :key="p.plantCode" :value="p.plantCode">{{ p.plantCode }}</option>
        </select>
        <select v-model="modality" class="sel">
          <option value="">全部模态</option>
          <option value="RGB">RGB</option>
          <option value="DEPTH">DEPTH</option>
          <option value="IR">IR</option>
          <option value="IMAGE">IMAGE</option>
        </select>
        <RouterLink to="/control/camera"><FpButton size="sm">去云台抓拍</FpButton></RouterLink>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
      </template>
    </FpPageHeader>
    <FpPanel title="影像列表">
      <div class="grid">
        <a
          v-for="m in filtered"
          :key="m.id"
          class="card"
          :href="mediaUrl(m)"
          target="_blank"
          rel="noopener"
        >
          <div class="thumb">
            <video
              v-if="String(m.modality || m.mediaType || '').toUpperCase() === 'VIDEO' && mediaUrl(m)"
              :src="mediaUrl(m)"
              controls
              muted
              playsinline
              preload="metadata"
              @click.stop
            />
            <img
              v-else-if="mediaUrl(m)"
              :src="mediaUrl(m)"
              alt=""
              @error="($event.target as HTMLImageElement).style.display='none'"
            />
            <span class="mod">{{ m.modality || m.mediaType || 'IMG' }}</span>
          </div>
          <div class="bd">
            <b>{{ m.plantCode || '-' }} · {{ m.label || m.id }}</b>
            <span>{{ m.capturedAt || m.createdAt || '-' }}</span>
          </div>
        </a>
        <div v-if="!filtered.length" class="empty">暂无影像</div>
      </div>
    </FpPanel>
  </div>
</template>

<style scoped>
.sel {
  height: 32px; border-radius: 999px; border: 1px solid var(--fp-line);
  padding: 0 12px; background: var(--fp-bg-elev); font-size: 12px; font-weight: 600;
}
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
.card { border: 1px solid var(--fp-line); border-radius: 14px; overflow: hidden; background: var(--fp-bg-elev); color: inherit; }
.thumb {
  position: relative; height: 120px; background: #e8ece9;
  display: grid; place-items: center;
}
.thumb img,
.thumb video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; background: #000; }
.mod {
  position: relative; z-index: 1; padding: 2px 8px; border-radius: 999px;
  background: rgba(255,255,255,.9); font-size: 11px; font-weight: 800; color: var(--fp-brand-2);
}
.bd { padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.bd span { color: var(--fp-faint); }
.empty { color: var(--fp-faint); }
</style>
