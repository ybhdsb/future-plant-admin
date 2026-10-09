/** Light content chart theme */
export const FP_CHART_COLORS = ['#0a7a6e', '#0369a1', '#b45309', '#64748b']

export function fpChartBase(overrides: Record<string, unknown> = {}) {
  return {
    color: FP_CHART_COLORS,
    textStyle: {
      fontFamily: 'Space Grotesk, PingFang SC, sans-serif',
      color: '#5a6f68',
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(18, 35, 31, 0.92)',
      borderWidth: 0,
      padding: [10, 12],
      textStyle: { color: '#f7faf8', fontSize: 12 },
      axisPointer: {
        type: 'line',
        lineStyle: { color: 'rgba(10, 122, 110, 0.35)', width: 1 },
      },
    },
    legend: {
      top: 0,
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 3,
      textStyle: { color: '#5a6f68', fontSize: 11 },
    },
    grid: { left: 42, right: 12, top: 36, bottom: 26 },
    xAxis: {
      type: 'time',
      axisLabel: { color: '#8b9e96', fontSize: 10, fontFamily: 'JetBrains Mono, monospace' },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#8b9e96', fontSize: 10, fontFamily: 'JetBrains Mono, monospace' },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(18, 45, 38, 0.06)', type: 'solid', width: 1 } },
    },
    ...overrides,
  }
}
