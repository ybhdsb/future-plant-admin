<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import FpButton from '@/components/ui/FpButton.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const weatherTemp = ref('天气加载中...')
const weatherCode = ref(0)

const nav = [
  {
    title: '系统总览',
    items: [{ to: '/dashboard', label: '运行看板', icon: '◉' }],
  },
  {
    title: '设备控制',
    items: [
      { to: '/control/led', label: 'LED 补光', icon: '◈' },
      { to: '/control/actuators', label: '通风供水与自动化', icon: '◎' },
      { to: '/control/camera', label: '云台相机', icon: '◌' },
      { to: '/control/logs', label: '指令日志', icon: '▤' },
    ],
  },
  {
    title: '作物表型',
    items: [
      { to: '/phenotype/digital', label: '数字化植株', icon: '❀' },
      { to: '/phenotype/metrics', label: '表型指标', icon: '▣' },
      { to: '/phenotype/media', label: '多模态影像', icon: '◫' },
      { to: '/phenotype/jobs', label: '分析任务', icon: '▹' },
    ],
  },
  {
    title: '历史与数据',
    items: [
      { to: '/history', label: '环境历史', icon: '⌒' },
      { to: '/phenotype/metrics', label: '表型历史', icon: '▥' },
      { to: '/data', label: '数据导出', icon: '⇩' },
    ],
  },
  {
    title: '资源与权限',
    items: [
      { to: '/devices', label: '设备管理', icon: '◫' },
      { to: '/model-library', label: '模型库', icon: '▦' },
      { to: '/datasets', label: '数据集', icon: '▧' },
      { to: '/auth-manage', label: '权限管理', icon: '▨' },
    ],
  },
]

const pageTitle = computed(() => (route.meta.title as string) || '未来植物')

async function loadWeather() {
  try {
    const url =
      'https://api.open-meteo.com/v1/forecast?latitude=30.59&longitude=114.31&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=Asia%2FShanghai&forecast_days=1'
    const res = await fetch(url)
    const data = await res.json()
    const max = data?.daily?.temperature_2m_max?.[0]
    const min = data?.daily?.temperature_2m_min?.[0]
    weatherCode.value = Number(data?.daily?.weathercode?.[0] || 0)
    if (max != null && min != null) weatherTemp.value = `${Math.round(min)}℃ ~ ${Math.round(max)}℃`
    else weatherTemp.value = '暂无数据'
  } catch {
    weatherTemp.value = '天气暂不可用'
  }
}

function weatherLabel(code: number) {
  if (code === 0) return '晴'
  if (code <= 3) return '多云'
  if (code <= 67) return '雨'
  if (code <= 77) return '雪'
  return '阴'
}

async function onLogout() {
  await auth.logout()
  router.push({ name: 'login' })
}

onMounted(loadWeather)
</script>

<template>
  <div class="shell">
    <aside class="nav">
      <div class="brand">
        <div class="mark" aria-hidden="true" />
        <div>
          <div class="brand-name">Future Plant</div>
          <div class="brand-sub">未来植物原型系统</div>
        </div>
      </div>

      <nav class="groups">
        <div v-for="g in nav" :key="g.title" class="group">
          <div class="group-title">{{ g.title }}</div>
          <RouterLink
            v-for="item in g.items"
            :key="item.to"
            :to="item.to"
            class="link"
            active-class="is-active"
          >
            <span class="ico">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </nav>
    </aside>

    <div class="main">
      <header class="top">
        <div class="crumb">
          <span class="dot" />
          <span>{{ pageTitle }}</span>
        </div>
        <div class="top-right">
          <div class="weather" title="武汉天气">
            <span class="w-city">武汉 · {{ weatherLabel(weatherCode) }}</span>
            <span class="w-temp">{{ weatherTemp }}</span>
          </div>
          <div class="sep" aria-hidden="true" />
          <div class="user">
            <span class="avatar">{{ (auth.user?.username || 'U').slice(0, 1).toUpperCase() }}</span>
            <span class="uname">{{ auth.user?.username || '用户' }}</span>
          </div>
          <FpButton variant="ghost" size="sm" @click="onLogout">退出</FpButton>
        </div>
      </header>
      <div class="content">
        <RouterView v-slot="{ Component }">
          <Transition name="fp-fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--fp-nav-w) 1fr;
  min-height: 100vh;
  background: var(--fp-bg);
}
.nav {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 22px 14px 18px;
  background:
    radial-gradient(480px 260px at 10% -5%, rgba(34, 211, 238, 0.22), transparent 55%),
    radial-gradient(360px 220px at 90% 90%, rgba(46, 196, 167, 0.12), transparent 50%),
    linear-gradient(180deg, #020807 0%, #061816 50%, #0a2420 100%);
  color: #e7f7f3;
  border-right: 1px solid rgba(34, 211, 238, 0.14);
  z-index: 20;
  overflow: hidden;
}
.nav::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, rgba(34, 211, 238, 0.55), transparent 40%, rgba(46, 196, 167, 0.25));
  pointer-events: none;
}
.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px 8px 20px;
}
.mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background:
    radial-gradient(circle at 30% 28%, #a5f3fc, transparent 52%),
    linear-gradient(145deg, #22d3ee, #0f766e);
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.45), 0 0 28px rgba(34, 211, 238, 0.35);
  animation: mark-glow 3.2s ease-in-out infinite;
}
@keyframes mark-glow {
  0%, 100% { box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.4), 0 0 20px rgba(34, 211, 238, 0.28); }
  50% { box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.6), 0 0 36px rgba(34, 211, 238, 0.5); }
}
.brand-name { font-size: 15px; font-weight: 700; letter-spacing: -0.03em; }
.brand-sub { margin-top: 2px; font-size: 10px; opacity: 0.55; font-family: var(--fp-mono); letter-spacing: 0.08em; text-transform: uppercase; }
.groups { flex: 1; overflow: auto; padding: 0 4px; }
.group { margin-bottom: 16px; }
.group-title {
  padding: 0 10px 8px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  opacity: 0.4;
  font-family: var(--fp-mono);
}
.link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  margin-bottom: 2px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  color: rgba(231, 247, 243, 0.72);
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}
.link:hover { background: rgba(255, 255, 255, 0.06); color: #fff; }
.link.is-active {
  background: linear-gradient(90deg, rgba(34, 211, 238, 0.18), rgba(46, 196, 167, 0.08));
  color: #fff;
  box-shadow: inset 0 0 0 1px rgba(34, 211, 238, 0.28), 0 0 20px rgba(34, 211, 238, 0.08);
}
.ico { width: 18px; text-align: center; opacity: 0.85; font-size: 12px; }
.main { min-width: 0; display: flex; flex-direction: column; background: var(--fp-bg); }
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  height: var(--fp-top-h);
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--fp-line);
  position: sticky;
  top: 0;
  z-index: 10;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: -0.015em;
  color: var(--fp-ink-2);
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: var(--fp-brand);
}
.top-right { display: flex; align-items: center; gap: 12px; }
.weather {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.w-city {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--fp-ink-2);
}
.w-temp {
  font-size: 11.5px;
  color: var(--fp-muted);
  font-variant-numeric: tabular-nums;
  font-family: var(--fp-mono);
}
.sep {
  width: 1px;
  height: 16px;
  background: var(--fp-line-strong);
}
.user {
  display: flex;
  align-items: center;
  gap: 8px;
}
.avatar {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--fp-ink);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}
.uname { font-size: 12px; font-weight: 500; color: var(--fp-ink-2); }
.content {
  flex: 1;
  min-height: 0;
  background: var(--fp-bg);
}
@media (max-width: 960px) {
  .shell { grid-template-columns: 1fr; }
  .nav { position: relative; height: auto; max-height: none; }
  .nav::after { display: none; }
  .weather { display: none; }
  .sep { display: none; }
}
</style>
