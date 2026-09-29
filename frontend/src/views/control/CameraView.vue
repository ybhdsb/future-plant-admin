<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import { DEVICE_KEY, getDashboard, getMedia, postCommand } from '@/api/plant'

const media = ref<any[]>([])
const dash = ref<any>(null)
const msg = ref('')

const latest = computed(() => media.value[0] || null)
function mediaUrl(m: any) {
  return m?.url || (m?.id != null ? `/plant/api/media/${m.id}/file` : '')
}

async function load() {
  ;[media.value, dash.value] = await Promise.all([getMedia(DEVICE_KEY, 24), getDashboard()])
}

async function capture() {
  await postCommand({
    deviceKey: DEVICE_KEY,
    clientRequestId: `ui-${Date.now()}`,
    commandType: 'CAMERA_CAPTURE',
    payload: { cameraId: 'camera.main', reason: 'manual' },
  })
  msg.value = '已下发抓拍指令'
  setTimeout(load, 1200)
}

async function gimbal(action: string) {
  await postCommand({
    deviceKey: DEVICE_KEY,
    clientRequestId: `ui-${Date.now()}`,
    commandType: 'GIMBAL_MOVE',
    payload: { action },
  })
  msg.value = `云台：${action}`
}

onMounted(load)
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Vision"
      title="摄像头与云台"
      subtitle="手动抓拍与云台微调，影像同步到多模态媒体库。"
    >
      <template #actions>
        <span class="pill" :class="dash?.online ? 'ok' : 'bad'">{{ dash?.online ? '在线' : '离线' }}</span>
        <span class="pill info">{{ dash?.mockEnabled ? 'Mock' : '实机' }}</span>
        <RouterLink to="/history"><FpButton variant="ghost" size="sm">历史图片</FpButton></RouterLink>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
        <FpButton size="sm" @click="capture">立即抓拍</FpButton>
      </template>
    </FpPageHeader>
    <p v-if="msg" class="ok">{{ msg }}</p>

    <div class="fp-stack">
      <div class="fp-grid-2">
        <FpPanel title="实时预览" desc="最近一次抓拍">
          <div class="preview">
            <img v-if="latest && mediaUrl(latest)" :src="mediaUrl(latest)" alt="preview" />
            <div v-else class="ph">暂无预览，请先抓拍</div>
          </div>
        </FpPanel>
        <FpPanel title="云台" desc="点按方向微调">
          <div class="pad">
            <span />
            <FpButton variant="secondary" @click="gimbal('UP')">上</FpButton>
            <span />
            <FpButton variant="secondary" @click="gimbal('LEFT')">左</FpButton>
            <FpButton variant="secondary" @click="gimbal('HOME')">回中</FpButton>
            <FpButton variant="secondary" @click="gimbal('RIGHT')">右</FpButton>
            <span />
            <FpButton variant="secondary" @click="gimbal('DOWN')">下</FpButton>
            <span />
          </div>
          <div class="stop">
            <FpButton variant="danger" @click="gimbal('STOP')">STOP 急停</FpButton>
          </div>
        </FpPanel>
      </div>

      <FpPanel title="最近影像" desc="点击可新窗口打开">
        <div class="grid">
          <a
            v-for="m in media"
            :key="m.id"
            class="card"
            :href="mediaUrl(m)"
            target="_blank"
            rel="noopener"
          >
            <div class="thumb">
              <img :src="mediaUrl(m)" alt="" @error="($event.target as HTMLImageElement).style.display='none'" />
              <span>{{ m.mediaType || 'IMG' }}</span>
            </div>
            <div class="meta">
              <b>{{ m.label || `Media #${m.id}` }}</b>
              <span>{{ m.capturedAt || '-' }}</span>
            </div>
          </a>
          <div v-if="!media.length" class="empty">暂无影像</div>
        </div>
      </FpPanel>
    </div>
  </div>
</template>

<style scoped>
.ok { color: var(--fp-ok); font-size: 13px; font-weight: 600; }
.pill {
  display: inline-flex; align-items: center; height: 32px; padding: 0 12px; border-radius: 999px;
  border: 1px solid var(--fp-line); background: #fff; font-size: 12px; font-weight: 700; color: var(--fp-muted);
}
.pill.ok { color: var(--fp-ok); background: #ecfdf5; border-color: #a7f3d0; }
.pill.bad { color: var(--fp-danger); background: #fef2f2; border-color: #fecaca; }
.pill.info { color: var(--fp-info); background: #f0f9ff; border-color: #bae6fd; }
.preview {
  min-height: 260px; border-radius: 14px; overflow: hidden; background: #0b3d36; display: grid; place-items: center;
}
.preview img { width: 100%; height: 280px; object-fit: contain; display: block; }
.ph { color: rgba(255,255,255,.7); font-size: 13px; }
.pad {
  width: 240px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 0 auto;
}
.stop { margin-top: 14px; text-align: center; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.card {
  border: 1px solid var(--fp-line); border-radius: 14px; overflow: hidden; background: #fff; color: inherit;
}
.thumb {
  position: relative; height: 110px; background: linear-gradient(160deg, #d1fae5, #ecfdf5);
  display: grid; place-items: center;
}
.thumb img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.thumb span {
  position: relative; z-index: 1; padding: 2px 8px; border-radius: 999px;
  background: rgba(255,255,255,.9); font-size: 11px; font-weight: 800; color: var(--fp-brand-2);
}
.meta { padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.meta b { font-size: 13px; }
.meta span { font-size: 11px; color: var(--fp-faint); }
.empty { color: var(--fp-faint); font-size: 13px; }
</style>
