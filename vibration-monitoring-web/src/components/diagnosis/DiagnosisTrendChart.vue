<template>
  <div ref="chartRef" class="diagnosis-trend-chart"></div>
</template>

<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  homeTheme: { type: Boolean, default: false },
})

const chartRef = ref(null)
let chart
const categories = [
  { key: 'innerRace', name: '轴承内圈故障', color: '#4599ed' },
  { key: 'outerRace', name: '轴承外圈故障', color: '#8b6edc' },
]

function render() {
  if (!chart) return
  const axisTextColor = props.homeTheme ? '#fff' : '#34495e'
  const axisLineColor = props.homeTheme ? '#718397' : '#34495e'
  chart.setOption({
    color: categories.map((item) => item.color),
    grid: { left: 40, right: 8, top: 12, bottom: 75 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#75be4f',
      borderWidth: 1,
      textStyle: { color: '#526273' },
      formatter: (params) => {
        const row = props.data[params[0]?.dataIndex] || {}
        const total = params.reduce((sum, item) => sum + Number(item.value || 0), 0)
        return `${row.date || ''}（共 ${total} 次）<br/>${params.map((item) => `${item.marker}${item.seriesName}：${item.value} 次（${total ? Math.round(item.value / total * 100) : 0}%）`).join('<br/>')}`
      },
    },
    xAxis: {
      type: 'category', data: props.data.map((item) => item.date?.slice(5)),
      axisTick: { show: true },
      axisLabel: { fontSize: 10, color: axisTextColor, interval: 'auto', rotate: -45, margin: 8 },
      axisLine: { show: true, lineStyle: { color: axisLineColor } },
    },
    yAxis: {
      type: 'value', name: '次数', minInterval: 1,
      axisTick: { show: true }, nameTextStyle: { color: axisTextColor },
      axisLabel: { fontSize: 10, color: axisTextColor },
      axisLine: { show: true, lineStyle: { color: axisLineColor } }, splitLine: { show: false },
    },
    series: categories.map((category) => ({ name: category.name, type: 'bar', stack: 'total', barMaxWidth: 9, data: props.data.map((item) => item[category.key] || 0) })),
  }, true)
  chart.resize()
}

onMounted(async () => { await nextTick(); chart = echarts.init(chartRef.value); render(); window.addEventListener('resize', render) })
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', render); chart?.dispose() })
</script>

<style scoped>
.diagnosis-trend-chart { width: 100%; height: 100%; min-height: 170px; padding-bottom: 6px; }
</style>
