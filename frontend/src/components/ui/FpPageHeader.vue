<script setup lang="ts">
defineProps<{
  kicker?: string
  title: string
  subtitle?: string
}>()
</script>

<template>
  <header class="hdr">
    <div class="main">
      <div v-if="kicker" class="fp-kicker">{{ kicker }}</div>
      <h1 class="fp-title">{{ title }}</h1>
      <p v-if="subtitle" class="fp-subtitle">{{ subtitle }}</p>
    </div>
    <div class="aside">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.hdr {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  padding: 4px 0 22px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--fp-line);
}
.main {
  min-width: 0;
}
.aside {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  padding-bottom: 2px;
}
@media (max-width: 720px) {
  .hdr {
    flex-direction: column;
    align-items: stretch;
  }
  .aside {
    justify-content: flex-start;
  }
}
</style>
