<template>
  <div ref="chartRef" class="axis-trend-chart"></div>
</template>

<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { statusLevels } from '@/config/statusLevels'

const props = defineProps({
  data: { type: Array, default: () => [] },
  measure: { type: String, default: 'velocity' },
  thresholds: { type: Object, default: () => ({ warning: 0, alarm: 0, danger: 0 }) },
})
const emit = defineEmits(['select-batch'])

const chartRef = ref(null)
let chart

const axes = [
  { key: 'x', name: '垂直', lineColor: '#e16eea', lineType: 'solid' },
  { key: 'y', name: '水平', lineColor: '#aa8df1', lineType: 'solid' },
  { key: 'z', name: '轴向', lineColor: '#6d55df', lineType: 'solid' },
]
const vibrationStatusNames = { NORMAL: '正常', WARNING: '预警', ALARM: '报警', DANGER: '危险', OFFLINE: '离线' }
const vibrationStatusColors = Object.fromEntries(statusLevels.map((item) => [item.code, item.color]))
const diagnosisStates = {
  '健康': { color: '#6cba4e' },
  '轴承内圈故障': { color: '#4599ed' },
  '轴承外圈故障': { color: '#8b6edc' },
}

function formatTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || ''
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}\n${String(date.getHours()).padStart(2, '0')}:00`
}

function thresholdLines() {
  return [
    ['warning', '预警', '#f3d400'], ['alarm', '报警', '#ff9800'], ['danger', '危险', '#d53535'],
  ].map(([key, name, color]) => ({ value: Number(props.thresholds?.[key] || 0), name, color }))
    .filter((item) => item.value > 0)
    .map((item) => ({
      yAxis: item.value, name: `${item.name} ${item.value.toFixed(2)}`,
      lineStyle: { color: item.color, type: 'dashed', width: 1 },
      label: { color: item.color, fontSize: 10, position: 'end', formatter: '{b}' },
    }))
}

function render() {
  if (!chart) return
  const unit = props.measure === 'acceleration' ? 'g RMS' : 'mm/s RMS'
  const values = props.data.flatMap((item) => axes.map((axis) => Number(item[axis.key] || 0)))
  const maxValue = Math.max(1, ...values)
  const diagnosisY = -Math.max(1, maxValue * 0.14)
  const diagnosisPoints = props.data
    .filter((item) => diagnosisStates[item.diagnosisText])
    .map((item) => ({
      value: [item.time, diagnosisY],
      batchId: item.batchId,
      diagnosisText: item.diagnosisText,
      itemStyle: { color: diagnosisStates[item.diagnosisText].color },
    }))

  chart.setOption({
    animation: false,
    color: axes.map((axis) => axis.lineColor),
    grid: { left: 60, right: 24, top: 36, bottom: 58 },
    legend: { top: 8, right: 16, itemWidth: 15, itemHeight: 8, textStyle: { fontSize: 11, color: '#526273' }, data: axes.map((axis) => axis.name) },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '#7a8ea2', type: 'dashed' } },
      formatter: (items) => {
        const axisItems = items.filter((item) => item.seriesName !== '智能诊断')
        const index = axisItems[0]?.dataIndex
        const row = props.data[index] || {}
        const diagnosis = items.find((item) => item.seriesName === '智能诊断')
        const lines = [
          row.time || '',
          ...axisItems.map((item) => `${item.marker}${item.seriesName}：${item.value?.[1] == null ? '--' : Number(item.value[1]).toFixed(2)} ${unit}`),
          `振动预警：${vibrationStatusNames[row.status] || '离线'}`,
        ]
        if (diagnosis) {
          const text = diagnosis.data?.diagnosisText || ''
          const color = diagnosis.color || diagnosisStates[text]?.color || '#9dacba'
          lines.push(`<span style="display:inline-block;width:10px;height:10px;margin-right:6px;background:${color};"></span>${text}`)
        }
        return lines.join('<br/>')
      },
    },
    xAxis: { type: 'time', axisLabel: { fontSize: 10, formatter: formatTime }, axisLine: { lineStyle: { color: '#7d8d9d' } }, splitLine: { show: false } },
    yAxis: {
      type: 'value', name: unit, nameLocation: 'middle', nameGap: 42,
      min: diagnosisY * 1.7, minInterval: 1,
      axisLabel: { fontSize: 10, formatter: (value) => value < 0 ? '' : value },
      axisLine: { lineStyle: { color: '#7d8d9d' } }, splitLine: { lineStyle: { color: '#e3eaf2' } },
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: [0], filterMode: 'none' },
      { type: 'slider', xAxisIndex: [0], height: 22, bottom: 5, borderColor: '#c7d7ed', fillerColor: '#dce9ff', handleStyle: { color: '#fff', borderColor: '#a9bfdf' }, backgroundColor: '#f2f6fc' },
    ],
    series: [
      ...axes.map((axis, index) => ({
        name: axis.name, type: 'line', showSymbol: true, symbol: 'circle', symbolSize: 6, connectNulls: false,
        lineStyle: { width: 1.5, color: axis.lineColor, type: axis.lineType },
        data: props.data.map((item) => ({
          value: [item.time, item[axis.key]],
          batchId: item.batchId,
          itemStyle: { color: vibrationStatusColors[item.status] || vibrationStatusColors.NORMAL },
        })),
        ...(index === 0 ? { markLine: { silent: true, symbol: 'none', data: thresholdLines() } } : {}),
      })),
      { name: '智能诊断', type: 'scatter', symbol: 'rect', symbolSize: [12, 12], data: diagnosisPoints, emphasis: { scale: 1.2 } },
    ],
  }, true)
  chart.resize()
  const diagnosisLabelY = chart.convertToPixel({ yAxisIndex: 0 }, diagnosisY)
  chart.setOption({
    graphic: [{
      id: 'diagnosis-label', type: 'text', left: 12, top: diagnosisLabelY - 7,
      style: { text: '诊断', fill: '#718295', font: '12px sans-serif' },
      silent: true,
    }],
  })
}

onMounted(async () => {
  await nextTick()
  chart = echarts.init(chartRef.value)
  chart.on('click', (params) => {
    const batchId = params?.data?.batchId
    if (batchId != null) emit('select-batch', batchId)
  })
  render()
  window.addEventListener('resize', render)
})
watch(() => [props.data, props.measure, props.thresholds], render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', render); chart?.dispose() })
</script>

<style scoped>
.axis-trend-chart { width: 100%; height: 100%; min-height: 330px; }
</style>
