<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import { getCommands, getDashboard } from '@/api/plant'

const rows = ref<any[]>([])
const dash = ref<any>(null)
let timer: number | undefined

async function load() {
  ;[rows.value, dash.value] = await Promise.all([getCommands(undefined, 50), getDashboard()])
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 5000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Audit"
      title="指令日志"
      subtitle="查看下发到边缘侧的指令与执行状态（每 5 秒自动刷新）。"
    >
      <template #actions>
        <span class="pill" :class="dash?.online ? 'ok' : 'bad'">{{ dash?.online ? '在线' : '离线' }}</span>
        <span class="pill info">{{ dash?.mockEnabled ? 'Mock' : '实机' }}</span>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
      </template>
    </FpPageHeader>

    <FpPanel title="最近指令" flush>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>类型</th>
              <th>状态</th>
              <th>操作者</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.commandId || r.id">
              <td>{{ r.commandId || r.id }}</td>
              <td>{{ r.commandType }}</td>
              <td><span class="badge">{{ r.status }}</span></td>
              <td>{{ r.operator || '-' }}</td>
              <td>{{ r.createdAt }}</td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="5" class="empty">暂无指令</td>
            </tr>
          </tbody>
        </table>
      </div>
    </FpPanel>
  </div>
</template>

<style scoped>
.table-wrap { overflow: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--fp-line); }
th { font-size: 11px; text-transform: uppercase; letter-spacing: 0.06em; color: var(--fp-muted); background: #f7fbf9; }
.pill {
  display: inline-flex; align-items: center; height: 32px; padding: 0 12px; border-radius: 999px;
  border: 1px solid var(--fp-line); background: #fff; font-size: 12px; font-weight: 700; color: var(--fp-muted);
}
.pill.ok { color: var(--fp-ok); background: #ecfdf5; border-color: #a7f3d0; }
.pill.bad { color: var(--fp-danger); background: #fef2f2; border-color: #fecaca; }
.pill.info { color: var(--fp-info); background: #f0f9ff; border-color: #bae6fd; }
.badge {
  display: inline-block; padding: 2px 8px; border-radius: 999px;
  background: var(--fp-brand-soft); color: var(--fp-brand-2); font-size: 11px; font-weight: 700;
}
.empty { color: var(--fp-faint); text-align: center; }
</style>
