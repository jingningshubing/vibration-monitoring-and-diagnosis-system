<template><div ref="chartRef" class="home-alarm-trend-chart"></div></template>

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
    color: statusLevels.map((item) => item.color), grid: { left: 40, right: 8, top: 12, bottom: 75 },
    tooltip: { trigger: 'axis', backgroundColor: '#fff', borderColor: '#75be4f', borderWidth: 1, textStyle: { color: '#526273' }, formatter: (params) => {
      const total = params.reduce((sum, item) => sum + Number(item.value || 0), 0)
      const date = props.data[params[0]?.dataIndex]?.date || ''
      return `${date}（共 ${total} 次）<br/>${params.map((item) => `${item.marker}${item.seriesName}：${item.value} 次（${total ? Math.round(item.value / total * 100) : 0}%）`).join('<br/>')}`
    } },
    xAxis: { type: 'category', data: props.data.map((item) => item.date?.slice(5)), axisLabel: { fontSize: 10, color: '#fff', rotate: -45, margin: 8 }, axisLine: { lineStyle: { color: '#718397' } } },
    yAxis: { type: 'value', name: '次数', minInterval: 1, nameTextStyle: { color: '#fff' }, axisLabel: { fontSize: 10, color: '#fff' }, axisLine: { lineStyle: { color: '#718397' } }, splitLine: { show: false } },
    series: statusLevels.map((level) => ({ name: level.name, type: 'bar', stack: 'total', barMaxWidth: 9, data: props.data.map((item) => Number(item[level.code.toLowerCase()] || 0)) })),
  }, true)
  chart.resize()
}
onMounted(async () => { await nextTick(); chart = init(chartRef.value); render(); window.addEventListener('resize', render) })
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', render); chart?.dispose() })
</script>

<style scoped>.home-alarm-trend-chart { width: 100%; height: 100%; min-height: 170px; padding-bottom: 6px; }</style>
