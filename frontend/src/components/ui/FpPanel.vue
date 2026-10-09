<script setup lang="ts">
defineProps<{
  title?: string
  desc?: string
  flush?: boolean
}>()
</script>

<template>
  <section class="fp-panel" :class="{ 'is-flush': flush }">
    <header v-if="title || $slots.extra" class="fp-panel-hd">
      <div class="titles">
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="desc" class="desc">{{ desc }}</p>
      </div>
      <div class="extra">
        <slot name="extra" />
      </div>
    </header>
    <div class="fp-panel-bd">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.fp-panel {
  background: var(--fp-bg-elev);
  border: 1px solid var(--fp-line);
  border-radius: var(--fp-radius-lg);
  box-shadow: var(--fp-shadow-sm);
  overflow: hidden;
}
.fp-panel-hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  padding: 0 18px;
  border-bottom: 1px solid var(--fp-line);
  background: var(--fp-bg-soft);
}
.titles {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 0;
}
.fp-panel-hd h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.015em;
  color: var(--fp-ink);
}
.desc {
  margin: 0;
  font-size: 12px;
  font-weight: 400;
  color: var(--fp-faint);
  line-height: 1.35;
}
.extra {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
.fp-panel-bd {
  padding: 16px 18px 18px;
}
.is-flush .fp-panel-bd {
  padding: 0;
}
</style>
