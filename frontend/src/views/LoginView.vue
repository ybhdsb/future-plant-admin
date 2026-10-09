<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import FpButton from '@/components/ui/FpButton.vue'

const auth = useAuthStore()
const router = useRouter()
const username = ref('admin')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    await router.replace({ name: 'dashboard' })
  } catch (e: any) {
    error.value = e?.message || '登录失败，请检查账号密码'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="stage" aria-hidden="true">
      <div class="grid" />
      <div class="beam b1" />
      <div class="beam b2" />
      <div class="ring r1" />
      <div class="ring r2" />
    </div>

    <div class="card">
      <i class="c tl" /><i class="c tr" /><i class="c bl" /><i class="c br" />
      <div class="brand-row">
        <div class="logo" />
        <div>
          <div class="name">Future Plant</div>
          <div class="tag">NEURAL GREENHOUSE OS</div>
        </div>
      </div>

      <h1>接入控制台</h1>
      <p class="lead">光谱 · 环境 · 表型孪生 · 资源编目</p>

      <form class="form" @submit.prevent="onSubmit">
        <label>
          <span>账号</span>
          <input v-model="username" autocomplete="username" placeholder="USERNAME" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="PASSWORD"
          />
        </label>
        <p v-if="error" class="err">{{ error }}</p>
        <FpButton type="submit" block :disabled="loading">
          {{ loading ? '鉴权中…' : 'ENTER SYSTEM' }}
        </FpButton>
      </form>

      <div class="foot">SECURE SESSION · HZAU-AIOT</div>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 16px;
  position: relative;
  overflow: hidden;
  background: #020807;
}
.stage {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.06) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse at center, #000 20%, transparent 72%);
  animation: drift 24s linear infinite;
}
@keyframes drift {
  from { background-position: 0 0; }
  to { background-position: 56px 56px; }
}
.beam {
  position: absolute;
  width: 2px;
  height: 140%;
  top: -20%;
  background: linear-gradient(180deg, transparent, rgba(34, 211, 238, 0.35), transparent);
  filter: blur(1px);
  animation: sweep 9s ease-in-out infinite;
}
.b1 { left: 18%; animation-delay: 0s; }
.b2 { left: 72%; animation-delay: 2.5s; opacity: 0.7; }
@keyframes sweep {
  0%, 100% { transform: translateX(0) rotate(8deg); opacity: 0.2; }
  50% { transform: translateX(40px) rotate(8deg); opacity: 0.7; }
}
.ring {
  position: absolute;
  border: 1px solid rgba(34, 211, 238, 0.18);
  border-radius: 50%;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}
.r1 {
  width: min(70vw, 560px);
  height: min(70vw, 560px);
  animation: spin 28s linear infinite;
  box-shadow: inset 0 0 60px rgba(34, 211, 238, 0.05);
}
.r2 {
  width: min(90vw, 760px);
  height: min(90vw, 760px);
  border-style: dashed;
  opacity: 0.5;
  animation: spin 48s linear infinite reverse;
}
@keyframes spin {
  to { transform: translate(-50%, -50%) rotate(360deg); }
}
.card {
  position: relative;
  width: min(420px, 100%);
  padding: 32px 28px 26px;
  border-radius: 16px;
  background:
    linear-gradient(160deg, rgba(34, 211, 238, 0.08), transparent 45%),
    rgba(7, 22, 20, 0.92);
  border: 1px solid rgba(34, 211, 238, 0.2);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.55), 0 0 40px rgba(34, 211, 238, 0.08);
  backdrop-filter: blur(16px);
}
.c {
  position: absolute;
  width: 12px;
  height: 12px;
  border-color: #22d3ee;
  border-style: solid;
  opacity: 0.7;
}
.tl { top: 10px; left: 10px; border-width: 2px 0 0 2px; }
.tr { top: 10px; right: 10px; border-width: 2px 2px 0 0; }
.bl { bottom: 10px; left: 10px; border-width: 0 0 2px 2px; }
.br { bottom: 10px; right: 10px; border-width: 0 2px 2px 0; }
.brand-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 28px;
}
.logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background:
    radial-gradient(circle at 30% 28%, #a5f3fc, transparent 52%),
    linear-gradient(145deg, #22d3ee, #0f766e);
  box-shadow: 0 0 24px rgba(34, 211, 238, 0.45);
}
.name {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.03em;
}
.tag {
  margin-top: 3px;
  font-size: 10px;
  font-family: var(--fp-mono);
  letter-spacing: 0.14em;
  color: #22d3ee;
}
h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.04em;
}
.lead {
  margin: 8px 0 0;
  color: var(--fp-muted);
  font-size: 13px;
}
.form {
  margin-top: 24px;
  display: grid;
  gap: 14px;
}
label {
  display: grid;
  gap: 6px;
}
label span {
  font-size: 10px;
  font-family: var(--fp-mono);
  letter-spacing: 0.12em;
  color: var(--fp-faint);
}
input {
  height: 42px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid rgba(120, 240, 220, 0.16);
  background: rgba(0, 0, 0, 0.35);
  color: var(--fp-ink);
}
input:focus {
  outline: none;
  border-color: rgba(34, 211, 238, 0.55);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12);
}
.err {
  margin: 0;
  color: var(--fp-danger);
  font-size: 12px;
}
.foot {
  margin-top: 22px;
  text-align: center;
  font-size: 10px;
  letter-spacing: 0.16em;
  font-family: var(--fp-mono);
  color: var(--fp-faint);
}
</style>
