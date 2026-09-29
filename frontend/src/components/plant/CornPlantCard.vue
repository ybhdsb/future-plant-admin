<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { CornPlant3D } from './corn3d.js'

const props = defineProps<{
  specimen: Record<string, any>
  active?: boolean
}>()
const emit = defineEmits<{ select: [specimen: Record<string, any>] }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
let view: any = null

onMounted(() => {
  if (!canvasRef.value) return
  view = CornPlant3D.create(canvasRef.value, props.specimen)
  view.onSelect = (sp: any) => emit('select', sp)
})

watch(
  () => props.specimen,
  (sp) => {
    if (view) view.setSpecimen(sp)
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
      <canvas ref="canvasRef" />
      <div class="hint">← 拖动旋转 →</div>
    </div>
    <button class="tag" type="button" @click="emit('select', specimen)">
      {{ specimen.plantCode }} · {{ specimen.slotCode || '' }}
    </button>
  </div>
</template>

<style scoped>
.slot {
  text-align: center;
  transition: transform 0.18s ease;
}
.slot:hover,
.slot.active {
  transform: translateY(-4px);
}
.stage {
  position: relative;
  height: 236px;
  margin: 0 auto;
  max-width: 158px;
  border-radius: 16px;
  background:
    radial-gradient(ellipse at 50% 100%, rgba(255, 255, 255, 0.4), transparent 55%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0));
  overflow: hidden;
}
.slot.active .stage {
  box-shadow: 0 0 0 2px rgba(13, 148, 136, 0.5), 0 14px 26px rgba(13, 148, 136, 0.2);
}
canvas {
  display: block;
  width: 100%;
  height: 100%;
  touch-action: none;
  cursor: grab;
}
.hint {
  position: absolute;
  left: 50%;
  bottom: 6px;
  transform: translateX(-50%);
  font-size: 10px;
  color: rgba(20, 53, 47, 0.42);
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s;
  white-space: nowrap;
  background: rgba(255, 255, 255, 0.55);
  padding: 1px 7px;
  border-radius: 999px;
}
.slot:hover .hint {
  opacity: 1;
}
.tag {
  display: inline-block;
  margin-top: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #cfe8e1;
  font-size: 11px;
  font-weight: 700;
  color: #0f766e;
}
</style>
