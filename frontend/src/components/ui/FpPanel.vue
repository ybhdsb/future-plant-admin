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
      <div>
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
  overflow: hidden;
}
.fp-panel-hd {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 22px 0;
}
.fp-panel-hd h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--fp-ink);
}
.desc {
  margin: 4px 0 0;
  font-size: 12px;
  font-weight: 400;
  color: var(--fp-faint);
  line-height: 1.45;
}
.extra {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
.fp-panel-bd {
  padding: 16px 22px 22px;
}
.is-flush .fp-panel-bd {
  padding: 0;
}
.is-flush .fp-panel-hd {
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--fp-line);
}
</style>
