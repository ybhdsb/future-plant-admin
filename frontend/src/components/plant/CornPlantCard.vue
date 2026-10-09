<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { CornPlant3D } from './corn3d.js'
import { canUseWebGL, createCornPlantThree, type CornPlantView } from './CornPlantThree'

const props = defineProps<{
  specimen: Record<string, any>
  active?: boolean
}>()
const emit = defineEmits<{ select: [specimen: Record<string, any>] }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const mode = ref<'three' | 'canvas'>('canvas')
let view: CornPlantView | null = null

onMounted(async () => {
  if (!canvasRef.value) return
  // Wait one frame so layout has real width/height before first render
  await new Promise<void>((r) => requestAnimationFrame(() => r()))
  if (!canvasRef.value) return
  if (canUseWebGL()) {
    try {
      view = createCornPlantThree(canvasRef.value, props.specimen)
      mode.value = 'three'
      view.onSelect = (sp) => emit('select', sp)
      return
    } catch (e) {
      console.error('[CornPlantCard] Three.js init failed, fallback canvas', e)
    }
  }
  view = CornPlant3D.create(canvasRef.value, props.specimen) as CornPlantView
  mode.value = 'canvas'
  view.onSelect = (sp) => emit('select', sp)
})

watch(
  () => props.specimen,
  (sp) => {
    view?.setSpecimen(sp)
  },
  { deep: true },
)

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})
</script>

<template>
  <div class="slot" :class="{ active }">
    <div class="stage">
      <div class="glow" aria-hidden="true" />
      <canvas ref="canvasRef" />
      <div class="hint">拖转旋转 · 点击选中</div>
    </div>
    <button class="tag" type="button" @click="emit('select', specimen)">
      <span class="code">{{ specimen.plantCode }}</span>
      <span class="slot-id">{{ specimen.slotCode || '—' }}</span>
    </button>
    <div v-if="mode === 'canvas'" class="warn">3D 渲染不可用，当前为简化预览</div>
  </div>
</template>

<style scoped>
.slot {
  text-align: center;
}
.stage {
  position: relative;
  height: 280px;
  margin: 0 auto;
  max-width: 200px;
  border-radius: var(--fp-radius-lg);
  background: linear-gradient(180deg, #fafbfa 0%, #eef2ef 100%);
  overflow: hidden;
  border: 1px solid var(--fp-line);
}
.slot.active .stage {
  border-color: rgba(10, 122, 110, 0.35);
  box-shadow: 0 0 0 1px rgba(10, 122, 110, 0.12);
}
.glow {
  display: none;
}
canvas {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  touch-action: none;
  cursor: grab;
}
canvas:active {
  cursor: grabbing;
}
.hint {
  position: absolute;
  z-index: 2;
  left: 50%;
  bottom: 10px;
  transform: translateX(-50%);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--fp-muted);
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s;
  white-space: nowrap;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid var(--fp-line);
  padding: 3px 9px;
  border-radius: 6px;
  font-family: var(--fp-mono);
}
.slot:hover .hint {
  opacity: 1;
}
.tag {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 10px;
  padding: 5px 12px;
  border-radius: 8px;
  background: var(--fp-bg-elev);
  border: 1px solid var(--fp-line);
  font-size: 12px;
}
.code {
  font-weight: 600;
  color: var(--fp-ink);
  letter-spacing: -0.02em;
}
.slot-id {
  font-weight: 500;
  color: var(--fp-faint);
  font-size: 11px;
  font-family: var(--fp-mono);
}
.warn {
  margin-top: 6px;
  font-size: 10px;
  color: var(--fp-warn);
}
</style>
