<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import { DEVICE_KEY, createPhenotypeJob, getPhenotypeJobs, getSpecimens, mockCompleteJob } from '@/api/plant'

const jobs = ref<any[]>([])
const specimens = ref<any[]>([])
const plantCode = ref('')
const msg = ref('')

async function load() {
  ;[jobs.value, specimens.value] = await Promise.all([getPhenotypeJobs(), getSpecimens()])
  if (!plantCode.value && specimens.value[0]) plantCode.value = specimens.value[0].plantCode
}

async function createJob() {
  if (!plantCode.value) return
  await createPhenotypeJob({ deviceKey: DEVICE_KEY, plantCode: plantCode.value, jobType: 'PHENOTYPE_EXTRACT' })
  msg.value = '任务已创建'
  await load()
}

async function mockDone(id: number) {
  await mockCompleteJob(id)
  msg.value = `任务 #${id} Mock 完成`
  await load()
}

onMounted(load)
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Jobs"
      title="分析任务"
      subtitle="影像 → 表型提取任务队列。可用 Mock 完成模拟推理并写回指标。"
    >
      <template #actions>
        <select v-model="plantCode" class="sel">
          <option v-for="p in specimens" :key="p.plantCode" :value="p.plantCode">
            {{ p.plantCode }}
          </option>
        </select>
        <FpButton size="sm" @click="createJob">新建任务</FpButton>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
      </template>
    </FpPageHeader>
    <p v-if="msg" class="ok">{{ msg }}</p>
    <FpPanel title="任务队列" flush>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>植株</th>
              <th>类型</th>
              <th>状态</th>
              <th>说明</th>
              <th>创建时间</th>
              <th>完成时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="j in jobs" :key="j.id">
              <td>{{ j.id }}</td>
              <td>{{ j.plantCode }}</td>
              <td>{{ j.jobType }}</td>
              <td><span class="badge" :data-s="j.status">{{ j.status }}</span></td>
              <td>{{ j.message || '-' }}</td>
              <td>{{ j.createdAt }}</td>
              <td>{{ j.finishedAt || '-' }}</td>
              <td class="ops">
                <FpButton
                  v-if="j.status !== 'DONE' && j.status !== 'SUCCESS'"
                  size="sm"
                  variant="secondary"
                  @click="mockDone(j.id)"
                >
                  Mock 完成
                </FpButton>
                <RouterLink v-else to="/phenotype/digital">
                  <FpButton size="sm" variant="ghost">查看植株</FpButton>
                </RouterLink>
              </td>
            </tr>
            <tr v-if="!jobs.length"><td colspan="8" class="empty">暂无任务。真实模型接入前，可用「Mock 完成」模拟推理并写回指标。</td></tr>
          </tbody>
        </table>
      </div>
    </FpPanel>
  </div>
</template>

<style scoped>
.ok { color: var(--fp-ok); font-size: 13px; font-weight: 600; }
.sel {
  height: 32px; border-radius: 999px; border: 1px solid var(--fp-line);
  padding: 0 12px; background: var(--fp-bg-elev); font-size: 12px; font-weight: 600;
}
.table-wrap { overflow: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--fp-line); }
th { font-size: 11px; font-weight: 500; color: var(--fp-faint); background: var(--fp-bg-soft); }
.badge {
  display: inline-block; padding: 2px 6px; border-radius: var(--fp-radius-sm);
  background: var(--fp-brand-soft); color: var(--fp-brand-2); font-size: 11px; font-weight: 700;
}
.badge[data-s='DONE'], .badge[data-s='SUCCESS'] { background: rgba(46, 196, 167, 0.08); color: var(--fp-ok); }
.badge[data-s='FAILED'], .badge[data-s='ERROR'] { background: rgba(240, 113, 103, 0.08); color: #b91c1c; }
.ops { display: flex; gap: 6px; }
.empty { color: var(--fp-faint); text-align: center; }
</style>
