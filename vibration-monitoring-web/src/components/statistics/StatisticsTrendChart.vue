<template>
  <div ref="chartRef" class="statistics-trend-chart"></div>
</template>

<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  type: { type: String, default: 'diagnosis' },
})

const chartRef = ref(null)
let chart

const diagnosisCategories = [
  { key: 'innerRace', name: '轴承内圈故障', color: '#4599ed' },
  { key: 'outerRace', name: '轴承外圈故障', color: '#8b6edc' },
]
const vibrationCategories = [
  { key: 'warning', name: '预警', color: '#f3d400' },
  { key: 'alarm', name: '报警', color: '#ff9800' },
  { key: 'danger', name: '危险', color: '#d53535' },
]

function render() {
  if (!chart) return
  const categories = props.type === 'vibration' ? vibrationCategories : diagnosisCategories
  chart.setOption({
    color: categories.map((item) => item.color),
    grid: { left: 38, right: 8, top: 16, bottom: 52 },
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const row = props.data[params[0]?.dataIndex] || {}
        const total = params.reduce((sum, item) => sum + Number(item.value || 0), 0)
        return `${row.date || ''}<br/><b>共 ${total} 次</b><br/>${params.map((item) => `${item.marker}${item.seriesName}：${item.value} 次`).join('<br/>')}`
      },
    },
    xAxis: {
      type: 'category',
      data: props.data.map((item) => item.date?.slice(5)),
      axisLabel: { fontSize: 10, rotate: -45 },
      axisLine: { lineStyle: { color: '#34495e' } },
    },
    yAxis: {
      type: 'value',
      name: '次数',
      minInterval: 1,
      axisLabel: { fontSize: 10 },
      axisLine: { lineStyle: { color: '#34495e' } },
    },
    series: categories.map((category) => ({
      name: category.name,
      type: 'bar',
      stack: 'total',
      barMaxWidth: 9,
      data: props.data.map((item) => item[category.key] || 0),
    })),
  }, true)
  chart.resize()
}

onMounted(async () => {
  await nextTick()
  chart = echarts.init(chartRef.value)
  render()
  window.addEventListener('resize', render)
})
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', render)
  chart?.dispose()
})
</script>

<style scoped>
.statistics-trend-chart { width: 100%; height: 100%; min-height: 0; }
</style>
