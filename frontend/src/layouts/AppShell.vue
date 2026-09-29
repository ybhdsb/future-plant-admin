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

function weatherIcon(code: number) {
  if (code === 0) return '☀️'
  if (code <= 3) return '⛅'
  if (code <= 67) return '🌧️'
  if (code <= 77) return '🌨️'
  return '☁️'
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
            <span class="w-ico">{{ weatherIcon(weatherCode) }}</span>
            <div>
              <div class="w-city">武汉市</div>
              <div class="w-temp">{{ weatherTemp }}</div>
            </div>
          </div>
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
  padding: 22px 16px 18px;
  background: linear-gradient(180deg, #0b3d36 0%, #0f4f46 48%, #0d5c52 100%);
  color: #e7f7f3;
  box-shadow: 8px 0 32px rgba(8, 40, 34, 0.18);
  z-index: 20;
  overflow: hidden;
}
.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px 8px 18px;
}
.mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background:
    radial-gradient(circle at 30% 30%, #5eead4, transparent 50%),
    linear-gradient(135deg, #14b8a6, #0f766e);
  box-shadow: 0 0 0 4px rgba(94, 234, 212, 0.15);
}
.brand-name { font-size: 15px; font-weight: 800; letter-spacing: -0.02em; }
.brand-sub { margin-top: 2px; font-size: 11px; opacity: 0.65; }
.groups { flex: 1; overflow: auto; padding: 0 4px; }
.group { margin-bottom: 14px; }
.group-title {
  padding: 0 10px 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  opacity: 0.45;
}
.link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  margin-bottom: 2px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(231, 247, 243, 0.78);
  transition: background 0.15s ease, color 0.15s ease;
}
.link:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.link.is-active {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
}
.ico { width: 18px; text-align: center; opacity: 0.85; font-size: 12px; }
.main { min-width: 0; display: flex; flex-direction: column; }
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  height: var(--fp-top-h);
  padding: 0 32px;
  background: rgba(238, 243, 240, 0.86);
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
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--fp-ink-2);
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--fp-brand);
}
.top-right { display: flex; align-items: center; gap: 10px; }
.weather {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.w-ico { font-size: 16px; }
.w-city { font-size: 11px; font-weight: 600; color: var(--fp-ink-2); }
.w-temp { font-size: 11px; color: var(--fp-muted); font-variant-numeric: tabular-nums; }
.user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 2px 2px 2px;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--fp-ink);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}
.uname { font-size: 12px; font-weight: 600; color: var(--fp-ink-2); }
.content {
  flex: 1;
  min-height: 0;
  background:
    radial-gradient(720px 280px at 12% 0%, rgba(10, 122, 110, 0.05), transparent 55%),
    var(--fp-bg);
}
@media (max-width: 960px) {
  .shell { grid-template-columns: 1fr; }
  .nav { position: relative; height: auto; max-height: none; }
  .weather { display: none; }
}
</style>
