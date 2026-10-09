<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'
import FpPanel from '@/components/ui/FpPanel.vue'
import FpButton from '@/components/ui/FpButton.vue'
import {
  DEVICE_KEY,
  deleteAutomationRule,
  getActuatorCatalog,
  getAutomationRules,
  getDashboard,
  postCommand,
  saveAutomationRule,
  setAutomationEnabled,
} from '@/api/plant'

const catalog = ref<any[]>([])
const rules = ref<any[]>([])
const dash = ref<any>(null)
const msg = ref('')

const form = ref({
  name: '氧泵自动策略',
  actuatorId: 'pump.oxygen',
  ruleType: 'DAILY_WINDOW',
  action: 'ON',
  onTime: '08:00',
  offTime: '20:00',
  everyNDays: 1,
  everyOnTime: '09:00',
  durationMinutes: 15,
  onMinutes: 30,
  offMinutes: 30,
  metric: 'nutrient.do',
  operator: 'LT',
  thresholdValue: 5,
  cooldownMinutes: 10,
  remark: '',
  enabled: true,
  priority: 10,
})

const stateMap = computed(() => {
  const m: Record<string, any> = {}
  ;(dash.value?.actuators || []).forEach((a: any) => {
    m[a.actuatorId] = a.state || {}
  })
  return m
})

const groups = computed(() => {
  const g: Record<string, any[]> = {}
  catalog.value.forEach((c) => {
    const key = c.group || '其他'
    ;(g[key] ||= []).push(c)
  })
  return g
})

const typeLabel: Record<string, string> = {
  DAILY_WINDOW: '每天时段',
  EVERY_N_DAYS: '隔天定时',
  INTERVAL: '间隔循环',
  SENSOR_THRESHOLD: '传感触发',
}

function paramText(r: any) {
  if (r.ruleType === 'DAILY_WINDOW') return `${r.onTime || ''} ~ ${r.offTime || ''}`
  if (r.ruleType === 'EVERY_N_DAYS') {
    return `每${r.everyNDays || 1}天 ${r.onTime || ''} 开${r.durationMinutes || 0}分钟`
  }
  if (r.ruleType === 'INTERVAL') return `开 ${r.onMinutes || 0} 分 / 关 ${r.offMinutes || 0} 分`
  if (r.ruleType === 'SENSOR_THRESHOLD') {
    return `${r.metric || ''} ${r.operatorName || r.operator || ''} ${r.thresholdValue ?? ''}`
  }
  return '-'
}

async function load() {
  ;[catalog.value, rules.value, dash.value] = await Promise.all([
    getActuatorCatalog(true),
    getAutomationRules(),
    getDashboard(),
  ])
  if (!form.value.actuatorId && catalog.value[0]) form.value.actuatorId = catalog.value[0].actuatorId
}

async function toggle(item: any) {
  const on = !stateMap.value[item.actuatorId]?.on
  await postCommand({
    deviceKey: DEVICE_KEY,
    clientRequestId: `ui-${Date.now()}`,
    commandType: item.commandType,
    payload: { actuatorId: item.actuatorId, on },
  })
  msg.value = `${item.name} → ${on ? '开' : '关'}`
  await load()
}

async function saveRule() {
  const t = form.value.ruleType
  const body: Record<string, unknown> = {
    deviceKey: DEVICE_KEY,
    name: form.value.name,
    actuatorId: form.value.actuatorId,
    ruleType: t,
    action: form.value.action,
    remark: form.value.remark,
    enabled: form.value.enabled,
    priority: form.value.priority,
    onTime: t === 'EVERY_N_DAYS' ? form.value.everyOnTime : form.value.onTime,
    offTime: form.value.offTime,
    everyNDays: form.value.everyNDays,
    durationMinutes: form.value.durationMinutes,
    onMinutes: form.value.onMinutes,
    offMinutes: form.value.offMinutes,
    metric: form.value.metric,
    operator: form.value.operator,
    thresholdValue: form.value.thresholdValue,
    cooldownMinutes: form.value.cooldownMinutes,
  }
  await saveAutomationRule(body)
  msg.value = '自动化规则已保存'
  await load()
}

async function toggleRule(r: any) {
  await setAutomationEnabled(r.id, !r.enabled)
  await load()
}

async function removeRule(r: any) {
  await deleteAutomationRule(r.id)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="fp-page">
    <FpPageHeader
      kicker="Control · Actuators & Automation"
      title="通风 / 供水 / 曝气与自动化"
      subtitle="对照建设方案补齐循环风机、水泵、氧泵与继电器；并为每个执行器配置「每天定时 / 隔天定时 / 间隔循环 / 传感器阈值触发」。"
    >
      <template #actions>
        <span class="pill" :class="dash?.online ? 'ok' : 'bad'">{{ dash?.online ? '在线' : '离线' }}</span>
        <span class="pill info">{{ dash?.mockEnabled ? 'Mock' : '实机' }}</span>
        <FpButton variant="secondary" size="sm" @click="load">刷新</FpButton>
      </template>
    </FpPageHeader>
    <p v-if="msg" class="ok">{{ msg }}</p>

    <div class="fp-stack">
      <FpPanel title="执行器手动控制" desc="建设方案 V1：循环风机×2、水泵；任务表补充氧泵；遮光电机为 V2 预留">
        <div v-for="(items, group) in groups" :key="group" class="grp">
          <div class="gt">{{ group }}</div>
          <div class="acts">
            <div
              v-for="item in items"
              :key="item.actuatorId"
              class="act"
              :class="{ on: !!stateMap[item.actuatorId]?.on, v2: item.v1 === false }"
            >
              <div>
                <div class="nm">{{ item.name }}{{ item.v1 === false ? ' · V2预留' : '' }}</div>
                <div class="tip">{{ item.tip }} · {{ item.actuatorId }}</div>
              </div>
              <button
                class="switch"
                :class="{ on: !!stateMap[item.actuatorId]?.on }"
                type="button"
                @click="toggle(item)"
              >
                <i />
              </button>
            </div>
          </div>
        </div>
      </FpPanel>

      <FpPanel title="自动化规则" desc="以氧泵为例：每天固定时段开、每隔几天开一次、溶氧偏低立即开">
        <template #extra>
          <FpButton size="sm" @click="saveRule">保存规则</FpButton>
        </template>
        <div class="example">
          <strong>示例建议：</strong><br />
          1）氧泵 · 每隔 1 天 · 09:00 开启 15 分钟<br />
          2）氧泵 · 传感器触发 · <code>nutrient.do &lt; 5.0</code> 立即开启（冷却 10 分钟防抖）<br />
          3）循环风机 · 每天 08:00–20:00 开启
        </div>
        <div class="form">
          <label>规则名称<input v-model="form.name" /></label>
          <label>
            目标执行器
            <select v-model="form.actuatorId">
              <option v-for="c in catalog" :key="c.actuatorId" :value="c.actuatorId">{{ c.name }}</option>
            </select>
          </label>
          <label>
            规则类型
            <select v-model="form.ruleType">
              <option value="DAILY_WINDOW">每天固定时段开启</option>
              <option value="EVERY_N_DAYS">每隔 N 天固定时刻开启</option>
              <option value="INTERVAL">开/关间隔循环</option>
              <option value="SENSOR_THRESHOLD">传感器阈值触发</option>
            </select>
          </label>
          <label>
            动作
            <select v-model="form.action">
              <option value="ON">开启</option>
              <option value="OFF">关闭</option>
            </select>
          </label>

          <template v-if="form.ruleType === 'DAILY_WINDOW'">
            <label>每天开<input v-model="form.onTime" /></label>
            <label>每天关<input v-model="form.offTime" /></label>
          </template>
          <template v-else-if="form.ruleType === 'EVERY_N_DAYS'">
            <label>每隔天数<input v-model.number="form.everyNDays" type="number" min="1" /></label>
            <label>开启时刻<input v-model="form.everyOnTime" /></label>
            <label>持续分钟<input v-model.number="form.durationMinutes" type="number" min="1" /></label>
          </template>
          <template v-else-if="form.ruleType === 'INTERVAL'">
            <label>开启时长(分)<input v-model.number="form.onMinutes" type="number" min="1" /></label>
            <label>关闭时长(分)<input v-model.number="form.offMinutes" type="number" min="1" /></label>
          </template>
          <template v-else>
            <label>
              测点 metric
              <select v-model="form.metric">
                <option value="nutrient.do">nutrient.do（溶氧）</option>
                <option value="air.temperature">air.temperature（气温）</option>
                <option value="air.humidity">air.humidity（湿度）</option>
                <option value="air.co2">air.co2（CO₂）</option>
                <option value="nutrient.ph">nutrient.ph（pH）</option>
                <option value="nutrient.ec">nutrient.ec（EC）</option>
                <option value="nutrient.level">nutrient.level（液位）</option>
              </select>
            </label>
            <label>
              条件
              <div class="row">
                <select v-model="form.operator">
                  <option value="LT">&lt;</option>
                  <option value="LTE">&lt;=</option>
                  <option value="GT">&gt;</option>
                  <option value="GTE">&gt;=</option>
                  <option value="EQ">=</option>
                </select>
                <input v-model.number="form.thresholdValue" type="number" step="0.01" />
              </div>
            </label>
            <label>冷却分钟<input v-model.number="form.cooldownMinutes" type="number" min="0" /></label>
          </template>
          <label>备注<input v-model="form.remark" placeholder="可选" /></label>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>执行器</th>
                <th>类型</th>
                <th>参数</th>
                <th>状态</th>
                <th>最近执行</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in rules" :key="r.id">
                <td>{{ r.name }}</td>
                <td>{{ r.actuatorId }}</td>
                <td>{{ typeLabel[r.ruleType] || r.ruleType }}</td>
                <td>{{ paramText(r) }}</td>
                <td>{{ r.enabled ? '启用' : '停用' }}</td>
                <td>{{ r.lastAppliedAt || r.lastTriggeredAt || '--' }}</td>
                <td class="ops">
                  <FpButton size="sm" variant="secondary" @click="toggleRule(r)">
                    {{ r.enabled ? '停用' : '启用' }}
                  </FpButton>
                  <FpButton size="sm" variant="danger" @click="removeRule(r)">删除</FpButton>
                </td>
              </tr>
              <tr v-if="!rules.length"><td colspan="7" class="empty">暂无规则</td></tr>
            </tbody>
          </table>
        </div>
      </FpPanel>
    </div>
  </div>
</template>

<style scoped>
.ok { color: var(--fp-ok); font-size: 13px; font-weight: 600; }
.pill {
  display: inline-flex; align-items: center; height: 28px; padding: 0 10px; border-radius: var(--fp-radius-sm);
  border: 1px solid var(--fp-line-strong); background: var(--fp-bg-elev); font-size: 11.5px; font-weight: 500; color: var(--fp-muted);
}
.pill.ok { color: var(--fp-ok); background: rgba(13, 122, 79, 0.05); border-color: rgba(13, 122, 79, 0.2); }
.pill.bad { color: var(--fp-danger); background: rgba(185, 28, 28, 0.04); border-color: rgba(185, 28, 28, 0.2); }
.pill.info { color: var(--fp-info); background: rgba(29, 95, 138, 0.05); border-color: rgba(29, 95, 138, 0.2); }
.gt { margin: 0 0 10px; font-size: 13px; font-weight: 600; color: var(--fp-ink); }
.grp + .grp { margin-top: 16px; }
.acts { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.act {
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
  padding: 14px; border-radius: 12px; border: 1px solid var(--fp-line); background: var(--fp-bg-elev);
}
.act.v2 { opacity: 0.7; border-style: dashed; }
.nm { font-weight: 800; font-size: 15px; }
.tip { margin-top: 4px; font-size: 12px; color: #94a3b8; }
.switch {
  position: relative; width: 52px; height: 30px; border-radius: 999px; background: #334155; flex-shrink: 0;
}
.switch.on { background: #009688; }
.switch i {
  position: absolute; top: 3px; left: 3px; width: 24px; height: 24px; border-radius: 50%; background: var(--fp-bg-elev);
  transition: left 0.18s ease; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}
.switch.on i { left: 25px; }
.example {
  margin: 0 0 14px; padding: 10px 12px; border-radius: 10px; background: rgba(46, 196, 167, 0.08); border: 1px solid rgba(46, 196, 167, 0.25);
  color: #065f46; font-size: 12px; line-height: 1.6;
}
.form {
  display: grid; grid-template-columns: 120px 1fr; gap: 10px 12px; align-items: center; max-width: 760px; margin-bottom: 16px;
}
.form label { display: contents; font-size: 12px; font-weight: 700; color: var(--fp-muted); }
.form input, .form select {
  height: 38px; border-radius: 10px; border: 1px solid var(--fp-line); padding: 0 10px; background: var(--fp-bg-elev);
}
.row { display: flex; gap: 8px; }
.table-wrap { overflow: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--fp-line); }
th { font-size: 11px; font-weight: 500; color: var(--fp-faint); background: var(--fp-bg-soft); }
.ops { display: flex; gap: 6px; }
.empty { color: var(--fp-faint); text-align: center; }
@media (max-width: 900px) {
  .acts { grid-template-columns: 1fr; }
  .form { grid-template-columns: 1fr; }
  .form label { display: grid; gap: 4px; }
}
</style>
