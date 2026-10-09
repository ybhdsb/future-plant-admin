<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  unit?: string
  hint?: string
  tone?: 'default' | 'ok' | 'warn' | 'brand'
}>()
</script>

<template>
  <div class="stat" :class="`tone-${tone || 'default'}`">
    <div class="label">{{ label }}</div>
    <div class="value">
      {{ value }}
      <small v-if="unit">{{ unit }}</small>
    </div>
    <div v-if="hint" class="hint">{{ hint }}</div>
  </div>
</template>

<style scoped>
.stat {
  padding: 14px 16px;
  background: var(--fp-bg-elev);
  border: 1px solid var(--fp-line);
  border-radius: var(--fp-radius);
}
.tone-brand .value {
  color: var(--fp-brand-2);
}
.tone-ok .value {
  color: var(--fp-ok);
}
.tone-warn .value {
  color: var(--fp-warn);
}
.label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--fp-faint);
  font-family: var(--fp-mono);
}
.value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.04em;
  color: var(--fp-ink);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  font-family: var(--fp-mono);
}
.value small {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0;
  color: var(--fp-faint);
  font-family: var(--fp-font);
}
.hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--fp-faint);
  line-height: 1.35;
}
</style>
