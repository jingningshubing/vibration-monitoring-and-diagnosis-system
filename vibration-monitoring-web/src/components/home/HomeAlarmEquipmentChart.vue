<template><div ref="chartRef" class="home-alarm-equipment-chart" :style="{ height: `${Math.max(170, data.length * 28)}px` }"></div></template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { statusLevels } from '@/config/statusLevels'

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart
function render() {
  if (!chart) return
  chart.setOption({
    color: statusLevels.map((item) => item.color), grid: { left: 20, right: 80, top: 12, bottom: 8 },
    tooltip: { trigger: 'axis', appendToBody: true, confine: false, axisPointer: { type: 'shadow' }, backgroundColor: '#fff', borderColor: '#75be4f', borderWidth: 1, textStyle: { color: '#526273' }, formatter: (params) => {
      const row = props.data[params[0]?.dataIndex] || {}
      const total = params.reduce((sum, item) => sum + Number(item.value || 0), 0)
      return `${row.equipmentCode || '--'}（共 ${total} 次）<br/>${params.map((item) => `${item.marker}${item.seriesName}：${item.value} 次（${total ? Math.round(item.value / total * 100) : 0}%）`).join('<br/>')}`
    } },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', inverse: true, data: props.data.map((item) => item.equipmentCode), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { show: false } },
    series: [
      ...statusLevels.map((level) => ({ name: level.name, type: 'bar', stack: 'total', barMaxWidth: 15, data: props.data.map((item) => Number(item[level.code.toLowerCase()] || 0)) })),
      { name: 'label', type: 'bar', stack: 'total', barGap: '-100%', barMaxWidth: 15, data: props.data.map(() => 0), itemStyle: { color: 'transparent' }, label: { show: true, position: 'right', color: '#fff', fontWeight: 700, formatter: (params) => props.data[params.dataIndex]?.equipmentCode || '' }, emphasis: { disabled: true }, silent: true },
    ],
  }, true)
  chart.resize()
}
onMounted(async () => { await nextTick(); chart = init(chartRef.value); render(); window.addEventListener('resize', render) })
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', render); chart?.dispose() })
</script>

<style scoped>.home-alarm-equipment-chart { width: 100%; min-height: 170px; }</style>
