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
      <div class="orb o1" />
      <div class="orb o2" />
      <div class="grid" />
      <div class="leaf leaf-a" />
      <div class="leaf leaf-b" />
    </div>

    <div class="card">
      <div class="brand-row">
        <div class="logo" />
        <div>
          <div class="name">Future Plant</div>
          <div class="tag">数字化植物工厂控制台</div>
        </div>
      </div>

      <h1>欢迎回来</h1>
      <p class="lead">以商业级体验管理光谱、环境与作物表型。</p>

      <form class="form" @submit.prevent="onSubmit">
        <label>
          <span>账号</span>
          <input v-model="username" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>
        <p v-if="error" class="err">{{ error }}</p>
        <FpButton type="submit" block :disabled="loading">
          {{ loading ? '登录中…' : '进入控制台' }}
        </FpButton>
      </form>

      <div class="foot">Secure session · Plant digital twin</div>
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
  background: #071c19;
}
.stage {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
}
.o1 {
  width: 420px;
  height: 420px;
  left: -80px;
  top: -60px;
  background: rgba(20, 184, 166, 0.35);
}
.o2 {
  width: 480px;
  height: 480px;
  right: -120px;
  bottom: -100px;
  background: rgba(52, 211, 153, 0.22);
}
.grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at center, #000 20%, transparent 72%);
}
.leaf {
  position: absolute;
  width: 180px;
  height: 280px;
  border-radius: 60% 40% 55% 45%;
  background: linear-gradient(160deg, rgba(52, 211, 153, 0.35), rgba(13, 148, 136, 0.08));
  filter: blur(1px);
  animation: float 9s ease-in-out infinite;
}
.leaf-a {
  left: 8%;
  top: 22%;
  transform: rotate(-18deg);
}
.leaf-b {
  right: 10%;
  bottom: 12%;
  transform: rotate(24deg);
  animation-delay: -3s;
}
@keyframes float {
  0%,
  100% {
    transform: translateY(0) rotate(var(--r, -18deg));
  }
  50% {
    transform: translateY(-14px) rotate(var(--r, -18deg));
  }
}
.card {
  position: relative;
  width: min(420px, 100%);
  padding: 32px 30px 26px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.35);
  animation: rise 0.55s ease both;
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.brand-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 22px;
}
.logo {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, #2dd4bf, #0f766e);
  box-shadow: 0 10px 24px rgba(13, 148, 136, 0.35);
}
.name {
  font-weight: 800;
  font-size: 15px;
  letter-spacing: -0.02em;
}
.tag {
  font-size: 12px;
  color: var(--fp-muted);
  margin-top: 2px;
}
h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.03em;
}
.lead {
  margin: 8px 0 0;
  color: var(--fp-muted);
  font-size: 14px;
  line-height: 1.5;
}
.form {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--fp-muted);
}
input {
  height: 44px;
  border-radius: 12px;
  border: 1px solid var(--fp-line);
  padding: 0 14px;
  background: #f8fbfa;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
input:focus {
  border-color: var(--fp-brand);
  box-shadow: 0 0 0 4px var(--fp-brand-soft);
  background: #fff;
}
.err {
  margin: 0;
  color: var(--fp-danger);
  font-size: 12px;
  font-weight: 600;
}
.foot {
  margin-top: 20px;
  text-align: center;
  font-size: 11px;
  color: var(--fp-faint);
  letter-spacing: 0.04em;
}
</style>
