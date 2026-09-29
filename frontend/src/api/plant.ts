import http, { unwrapPlant } from './http'

export const DEVICE_KEY = 'plant-ctrl-01'

export async function getDashboard(deviceKey = DEVICE_KEY) {
  const { data } = await http.get('/plant/api/dashboard', { params: { deviceKey } })
  return unwrapPlant<Record<string, any>>(data)
}

export async function setMock(enabled: boolean, deviceKey = DEVICE_KEY) {
  const { data } = await http.post('/plant/api/mock', { deviceKey, enabled })
  return unwrapPlant(data)
}

export async function postCommand(body: Record<string, unknown>) {
  const { data } = await http.post('/plant/api/commands', body)
  return unwrapPlant<Record<string, any>>(data)
}

export async function getCommands(deviceKey = DEVICE_KEY, limit = 30) {
  const { data } = await http.get('/plant/api/commands', { params: { deviceKey, limit } })
  return unwrapPlant<any[]>(data)
}

export async function getLedSchedules(deviceKey = DEVICE_KEY) {
  const { data } = await http.get('/plant/api/led/schedules', { params: { deviceKey } })
  return unwrapPlant<any[]>(data)
}

export async function saveLedSchedule(body: Record<string, unknown>) {
  const { data } = await http.post('/plant/api/led/schedules', body)
  return unwrapPlant(data)
}

export async function setLedScheduleEnabled(id: number, enabled: boolean) {
  const { data } = await http.post(`/plant/api/led/schedules/${id}/enabled`, { enabled })
  return unwrapPlant(data)
}

export async function deleteLedSchedule(id: number) {
  const { data } = await http.delete(`/plant/api/led/schedules/${id}`)
  return unwrapPlant(data)
}

export async function getActuatorCatalog(includeV2 = true) {
  const { data } = await http.get('/plant/api/actuators/catalog', { params: { includeV2 } })
  return unwrapPlant<any[]>(data)
}

export async function getAutomationRules(deviceKey = DEVICE_KEY) {
  const { data } = await http.get('/plant/api/automation/rules', { params: { deviceKey } })
  return unwrapPlant<any[]>(data)
}

export async function saveAutomationRule(body: Record<string, unknown>) {
  const { data } = await http.post('/plant/api/automation/rules', body)
  return unwrapPlant(data)
}

export async function setAutomationEnabled(id: number, enabled: boolean) {
  const { data } = await http.post(`/plant/api/automation/rules/${id}/enabled`, { enabled })
  return unwrapPlant(data)
}

export async function deleteAutomationRule(id: number) {
  const { data } = await http.delete(`/plant/api/automation/rules/${id}`)
  return unwrapPlant(data)
}

export async function getMetricsMulti(params: {
  deviceKey?: string
  metrics: string
  from?: string
  to?: string
  limit?: number
}) {
  const { data } = await http.get('/plant/api/metrics/multi', {
    params: { deviceKey: DEVICE_KEY, limit: 500, ...params },
  })
  return unwrapPlant<Record<string, any[]>>(data)
}

export async function getMedia(deviceKey = DEVICE_KEY, limit = 24) {
  const { data } = await http.get('/plant/api/media', { params: { deviceKey, limit } })
  return unwrapPlant<any[]>(data)
}

export async function getReadings(params: {
  deviceKey?: string
  metric?: string
  from?: string
  to?: string
  page?: number
  size?: number
}) {
  const { data } = await http.get('/plant/api/readings', {
    params: { deviceKey: DEVICE_KEY, page: 0, size: 50, ...params },
  })
  return unwrapPlant<{ page: number; size: number; count: number; rows: any[] }>(data)
}

export async function getSpecimens(deviceKey = DEVICE_KEY) {
  const { data } = await http.get('/plant/api/phenotype/specimens', { params: { deviceKey } })
  return unwrapPlant<any[]>(data)
}

export async function getPhenotypeOverview(deviceKey = DEVICE_KEY) {
  const { data } = await http.get('/plant/api/phenotype/overview', { params: { deviceKey } })
  return unwrapPlant<Record<string, any>>(data)
}

export async function getPhenotypeMetrics(params: {
  deviceKey?: string
  plantCode?: string
  metric?: string
  limit?: number
}) {
  const { data } = await http.get('/plant/api/phenotype/metrics', {
    params: { deviceKey: DEVICE_KEY, limit: 200, ...params },
  })
  return unwrapPlant<any[]>(data)
}

export async function getPhenotypeMedia(deviceKey = DEVICE_KEY, plantCode?: string, limit = 24) {
  const { data } = await http.get('/plant/api/phenotype/media', {
    params: { deviceKey, plantCode, limit },
  })
  return unwrapPlant<any[]>(data)
}

export async function getPhenotypeJobs(deviceKey = DEVICE_KEY, limit = 30) {
  const { data } = await http.get('/plant/api/phenotype/jobs', { params: { deviceKey, limit } })
  return unwrapPlant<any[]>(data)
}

export async function createPhenotypeJob(body: Record<string, unknown>) {
  const { data } = await http.post('/plant/api/phenotype/jobs', body)
  return unwrapPlant(data)
}

export async function mockCompleteJob(id: number) {
  const { data } = await http.post(`/plant/api/phenotype/jobs/${id}/mock-complete`)
  return unwrapPlant(data)
}

export function exportTelemetryUrl(params: {
  deviceKey?: string
  metric?: string
  from?: string
  to?: string
  format?: string
}) {
  const q = new URLSearchParams({
    deviceKey: params.deviceKey || DEVICE_KEY,
    format: params.format || 'csv',
  })
  if (params.metric) q.set('metric', params.metric)
  if (params.from) q.set('from', params.from)
  if (params.to) q.set('to', params.to)
  return `/plant/api/export/telemetry?${q.toString()}`
}
