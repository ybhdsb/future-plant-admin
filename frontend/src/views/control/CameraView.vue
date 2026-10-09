<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import {
  DEVICE_KEY,
  cameraCapture,
  cameraPhotosStatus,
  cameraPreviewFrameUrl,
  cameraPtzMove,
  cameraPtzStop,
  cameraReconnect,
  cameraRecordingStart,
  cameraRecordingStop,
  cameraScheduleStart,
  cameraScheduleStop,
  cameraStatus,
  getMedia,
} from '@/api/plant'

const media = ref<any[]>([])
const cam = ref<any>(null)
const photoStat = ref<any>(null)
const msg = ref('')
const err = ref('')
const busy = ref(false)
const previewSrc = ref('')
const recording = ref(false)
const scheduleOn = ref(false)
const speed = ref(30)
const durationMs = ref(500)
const intervalSec = ref(10)
let previewTimer: number | undefined
let mediaTimer: number | undefined

function mediaUrl(m: any) {
  return m?.url || (m?.id != null ? `/plant/api/media/${m.id}/file` : '')
}

const statusText = computed(() => {
  if (!cam.value?.gatewayEnabled) return '网关未启用'
  if (!cam.value?.connected) return cam.value?.message || '网关离线'
  if (cam.value?.previewConnected === false) return cam.value?.previewMessage || '预览断开'
  return `在线 · 相机 ${cam.value?.cameraIp || '-'}`
})

async function load() {
  err.value = ''
  try {
    ;[media.value, cam.value, photoStat.value] = await Promise.all([
      getMedia(DEVICE_KEY, 24),
      cameraStatus(),
      cameraPhotosStatus().catch(() => null),
    ])
    const phase = cam.value?.recordingPhase
    recording.value = phase === 'recording' || phase === 'starting'
    scheduleOn.value = !!photoStat.value?.active || !!cam.value?.photosActive
    refreshPreview()
  } catch (e: any) {
    err.value = e?.message || String(e)
  }
}

function refreshPreview() {
  if (cam.value?.gatewayEnabled && cam.value?.connected && cam.value?.previewConnected !== false) {
    previewSrc.value = cameraPreviewFrameUrl()
  } else {
    const img = media.value.find((m) => m.mediaType !== 'VIDEO' && mediaUrl(m))
    previewSrc.value = img ? mediaUrl(img) : ''
  }
}

async function reconnectCam() {
  return run('已请求重连摄像头', async () => {
    const r = await cameraReconnect()
    if (r && r.previewConnected === false) {
      throw new Error(r.message || r.previewMessage || '重连后预览仍未恢复，请检查相机网络或让同事重启网关')
    }
  })
}

async function run(label: string, fn: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  msg.value = ''
  err.value = ''
  try {
    await fn()
    msg.value = label
    await load()
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || String(e)
  } finally {
    busy.value = false
  }
}

function capture() {
  return run('拍照成功', () => cameraCapture())
}

function gimbal(direction: string) {
  if (direction === 'STOP') {
    return run('云台已停止', () => cameraPtzStop())
  }
  return run(`云台 ${direction}`, () =>
    cameraPtzMove({
      direction: direction.toLowerCase(),
      speed: Number(speed.value) || 30,
      durationMs: Number(durationMs.value) || 500,
    }),
  )
}

async function toggleRecord() {
  if (!recording.value) {
    await run('开始录像', () => cameraRecordingStart())
  } else {
    await run('停止录像（等待封装）', () => cameraRecordingStop())
  }
}

async function toggleSchedule() {
  if (!scheduleOn.value) {
    await run(`定时拍照每 ${intervalSec.value}s`, () =>
      cameraScheduleStart(Number(intervalSec.value) || 10),
    )
  } else {
    await run('已停止定时拍照', () => cameraScheduleStop())
  }
}

onMounted(() => {
  load()
  previewTimer = window.setInterval(() => {
    if (cam.value?.gatewayEnabled && cam.value?.connected && cam.value?.previewConnected !== false) {
      previewSrc.value = cameraPreviewFrameUrl()
    }
  }, 1500)
  // 定时拍照开启时轮询状态+媒体库（后端会把网关 last_file 同步入库）
  mediaTimer = window.setInterval(async () => {
    if (!scheduleOn.value) return
    try {
      photoStat.value = await cameraPhotosStatus()
      scheduleOn.value = !!photoStat.value?.active
      media.value = await getMedia(DEVICE_KEY, 24)
      if (media.value[0] && !(cam.value?.gatewayEnabled && cam.value?.connected)) {
        previewSrc.value = mediaUrl(media.value[0])
      }
    } catch {
      /* ignore poll errors */
    }
  }, 4000)
})
onUnmounted(() => {
  if (previewTimer) window.clearInterval(previewTimer)
  if (mediaTimer) window.clearInterval(mediaTimer)
})
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Vision"
      title="摄像头与云台"
      subtitle="经本平台转发至同事海康网关（状态 / 云台 / 录像 / 拍照 / 定时拍）。"
    >
      <template #actions>
        <span class="pill" :class="cam?.connected ? 'ok' : 'bad'">{{ statusText }}</span>
        <RouterLink to="/history"><FpButton variant="ghost" size="sm">历史图片</FpButton></RouterLink>
        <FpButton variant="secondary" size="sm" :disabled="busy" @click="load">刷新</FpButton>
        <FpButton variant="secondary" size="sm" :disabled="busy" @click="reconnectCam">重连预览</FpButton>
        <FpButton size="sm" :disabled="busy" @click="capture">立即拍照</FpButton>
        <FpButton
          :variant="recording ? 'danger' : 'secondary'"
          size="sm"
          :disabled="busy"
          @click="toggleRecord"
        >
          {{ recording ? '停止录像' : '开始录像' }}
        </FpButton>
      </template>
    </FpPageHeader>

    <p v-if="msg" class="ok">{{ msg }}</p>
    <p v-if="err" class="bad">{{ err }}</p>

    <div class="fp-stack">
      <div class="fp-grid-2">
        <FpPanel title="实时预览" desc="平台代理网关 JPEG 帧；停录后若黑屏请点「重连预览」">
          <div class="preview">
            <img v-if="previewSrc" :src="previewSrc" alt="preview" />
            <div v-else class="ph">
              {{
                cam?.previewConnected === false
                  ? cam?.previewMessage || '预览已断开（常见于停止录像后），请点右上角「重连预览」'
                  : '暂无预览（检查网关或先拍照）'
              }}
            </div>
          </div>
          <p class="hint">
            预览 {{ cam?.previewConnected === false ? '断开' : '正常' }} ·
            PTZ {{ cam?.ptzReady ? '就绪' : '未知' }} · 照片约 {{ cam?.photoCount ?? photoStat?.count ?? 0 }} 张 ·
            录像阶段 {{ cam?.recordingPhase || 'idle' }}
          </p>
        </FpPanel>

        <FpPanel title="云台控制" desc="八向 + 转速/时长（后端 move → 等待 → stop）">
          <div class="params">
            <label>转速 <input v-model.number="speed" type="number" min="1" max="100" /></label>
            <label>时长ms <input v-model.number="durationMs" type="number" min="100" max="5000" step="100" /></label>
          </div>
          <div class="pad">
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('up_left')">左上</FpButton>
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('up')">上</FpButton>
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('up_right')">右上</FpButton>
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('left')">左</FpButton>
            <FpButton variant="danger" :disabled="busy" @click="gimbal('STOP')">STOP</FpButton>
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('right')">右</FpButton>
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('down_left')">左下</FpButton>
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('down')">下</FpButton>
            <FpButton variant="secondary" :disabled="busy" @click="gimbal('down_right')">右下</FpButton>
          </div>
          <div class="zoom">
            <FpButton variant="secondary" size="sm" :disabled="busy" @click="gimbal('zoom_in')">变焦+</FpButton>
            <FpButton variant="secondary" size="sm" :disabled="busy" @click="gimbal('zoom_out')">变焦-</FpButton>
          </div>
        </FpPanel>
      </div>

      <FpPanel title="定时拍照" desc="网关侧按间隔循环抓拍；平台自动把新照片同步到「最近影像」">
        <div class="params">
          <label>间隔秒 <input v-model.number="intervalSec" type="number" min="1" max="86400" /></label>
          <FpButton
            :variant="scheduleOn ? 'danger' : 'primary'"
            size="sm"
            :disabled="busy"
            @click="toggleSchedule"
          >
            {{ scheduleOn ? '停止定时' : '启动定时' }}
          </FpButton>
        </div>
        <p class="hint" v-if="photoStat">
          active={{ photoStat.active }} · interval={{ photoStat.interval_seconds }} ·
          session={{ photoStat.session_count }} · count={{ photoStat.count }} ·
          {{ photoStat.message }}
          <span v-if="photoStat.syncedToMedia"> · 刚同步 {{ photoStat.syncedToMedia }} 张</span>
        </p>
      </FpPanel>

      <FpPanel title="最近影像" desc="照片为本地预览副本；录像经 SSH 拉取后可在此直接播放">
        <div class="grid">
          <a
            v-for="m in media"
            :key="m.id"
            class="card"
            :href="mediaUrl(m) || undefined"
            :target="mediaUrl(m) ? '_blank' : undefined"
            rel="noopener"
            @click="!mediaUrl(m) && $event.preventDefault()"
          >
            <div class="thumb">
              <video
                v-if="m.mediaType === 'VIDEO' && mediaUrl(m)"
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
                @error="($event.target as HTMLElement).style.display = 'none'"
              />
              <div v-else class="ph">{{ m.mediaType === 'VIDEO' ? 'VIDEO' : 'NO FILE' }}</div>
            </div>
            <div class="meta">
              <b>{{ m.label || m.mediaType }}</b>
              <span>{{ m.mediaType }} · {{ m.capturedAt || m.createdAt }}</span>
              <span v-if="m.remotePath && !m.hasLocalFile" class="path">{{ m.remotePath }}</span>
            </div>
          </a>
        </div>
        <p v-if="!media.length" class="ph">暂无媒体记录</p>
      </FpPanel>
    </div>
  </div>
</template>

<style scoped>
.pill {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--fp-border);
}
.pill.ok {
  color: #15803d;
  background: #dcfce7;
}
.pill.bad {
  color: #b91c1c;
  background: #fee2e2;
}
.ok {
  color: #15803d;
  margin: 0 0 12px;
}
.bad {
  color: #b91c1c;
  margin: 0 0 12px;
}
.preview {
  min-height: 240px;
  background: #0f172a;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.preview img {
  max-width: 100%;
  max-height: 360px;
  object-fit: contain;
}
.ph {
  color: #94a3b8;
  padding: 16px;
}
.hint {
  font-size: 12px;
  color: var(--fp-muted, #64748b);
  margin: 8px 0 0;
}
.params {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.params label {
  font-size: 13px;
  display: flex;
  gap: 6px;
  align-items: center;
}
.params input {
  width: 88px;
  padding: 4px 8px;
  border: 1px solid var(--fp-border);
  border-radius: 6px;
}
.pad {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.zoom {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.card {
  text-decoration: none;
  color: inherit;
  border: 1px solid var(--fp-border);
  border-radius: 8px;
  overflow: hidden;
}
.thumb {
  height: 120px;
  background: #0f172a;
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb img,
.thumb video {
  width: 100%;
  max-height: 100%;
  object-fit: cover;
  display: block;
}
.thumb video {
  height: 100%;
  background: #000;
}
.meta {
  font-size: 11px;
  padding: 6px 8px;
  color: var(--fp-muted);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.meta b {
  color: var(--fp-ink, #0f172a);
  font-weight: 600;
}
.meta .path {
  word-break: break-all;
  font-size: 10px;
  opacity: 0.85;
}
</style>
