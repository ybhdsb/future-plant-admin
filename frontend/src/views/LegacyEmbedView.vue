<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const frameSrc = computed(() => {
  const name = String(route.name || '')
  if (name === 'devices') return '/devices'
  if (name === 'model-library') return '/model_library'
  if (name === 'model-library-detail') return `/model_library/${route.params.id}`
  if (name === 'datasets') return '/datasets'
  if (name === 'dataset-detail') return `/datasets/${route.params.id}`
  if (name === 'auth-manage') return '/auth'
  return (route.meta.embedSrc as string) || '/devices'
})
</script>

<template>
  <div class="embed-page">
    <iframe
      :key="frameSrc"
      class="embed-frame"
      :src="frameSrc"
      title="workspace-content"
    />
  </div>
</template>

<style scoped>
.embed-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 61px);
  min-height: 520px;
  background: var(--fp-bg);
}
.embed-frame {
  flex: 1;
  width: 100%;
  border: 0;
  background: transparent;
}
</style>
