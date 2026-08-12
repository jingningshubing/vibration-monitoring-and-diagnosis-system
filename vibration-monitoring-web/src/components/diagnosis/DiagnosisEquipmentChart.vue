<template>
  <div ref="chartRef" class="diagnosis-equipment-chart"></div>
</template>

<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart
const categories = [
  { key: 'innerRace', name: '轴承内圈故障', color: '#4599ed' },
  { key: 'outerRace', name: '轴承外圈故障', color: '#8b6edc' },
]

function render() {
  if (!chart) return
  chart.setOption({
    color: categories.map((item) => item.color),
    grid: { left: 96, right: 12, top: 12, bottom: 8 },
    tooltip: {
      trigger: 'axis', appendToBody: true, confine: false, axisPointer: { type: 'shadow' },
      backgroundColor: '#fff', borderColor: '#75be4f', borderWidth: 1, textStyle: { color: '#526273' },
      formatter: (params) => {
        const row = props.data[params[0]?.dataIndex] || {}
        const total = params.reduce((sum, item) => sum + Number(item.value || 0), 0)
        return `${row.equipmentCode || '--'}（共 ${total} 次）<br/>${params.map((item) => `${item.marker}${item.seriesName}：${item.value} 次（${total ? Math.round(item.value / total * 100) : 0}%）`).join('<br/>')}`
      },
    },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category', inverse: true, data: props.data.map((item) => item.equipmentCode),
      axisTick: { show: false }, axisLine: { show: false },
      axisLabel: { fontSize: 12, fontWeight: 500, color: '#34495e', width: 88, overflow: 'truncate' },
    },
    series: categories.map((category) => ({ name: category.name, type: 'bar', stack: 'total', barMaxWidth: 15, data: props.data.map((item) => item[category.key] || 0) })),
  }, true)
  chart.resize()
}

onMounted(async () => { await nextTick(); chart = echarts.init(chartRef.value); render(); window.addEventListener('resize', render) })
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', render); chart?.dispose() })
</script>

<style scoped>
.diagnosis-equipment-chart { width: 100%; min-height: 100%; }
</style>
