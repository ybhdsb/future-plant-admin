<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    size?: 'sm' | 'md'
    block?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', block: false, disabled: false, type: 'button' },
)
</script>

<template>
  <button
    class="fp-btn"
    :class="[`is-${variant}`, `is-${size}`, { 'is-block': block }]"
    :disabled="disabled"
    :type="type"
  >
    <slot />
  </button>
</template>

<style scoped>
.fp-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--fp-radius-sm);
  font-weight: 600;
  letter-spacing: -0.01em;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
  white-space: nowrap;
}
.fp-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.is-md {
  height: 38px;
  padding: 0 16px;
  font-size: 13px;
}
.is-sm {
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
}
.is-block {
  width: 100%;
}
.is-primary {
  background: var(--fp-ink);
  color: #fff;
}
.is-primary:not(:disabled):hover {
  background: var(--fp-brand-2);
}
.is-secondary {
  background: transparent;
  color: var(--fp-ink-2);
  border: 1px solid var(--fp-line-strong);
}
.is-secondary:not(:disabled):hover {
  border-color: var(--fp-ink);
  background: var(--fp-bg-soft);
}
.is-ghost {
  background: transparent;
  color: var(--fp-muted);
}
.is-ghost:not(:disabled):hover {
  color: var(--fp-ink);
  background: rgba(18, 45, 38, 0.04);
}
.is-danger {
  background: transparent;
  color: var(--fp-danger);
  border: 1px solid rgba(194, 65, 12, 0.28);
}
.is-danger:not(:disabled):hover {
  background: rgba(194, 65, 12, 0.06);
}
</style>
