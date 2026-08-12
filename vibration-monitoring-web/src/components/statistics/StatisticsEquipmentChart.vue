<template><div ref="chartRef" class="statistics-equipment-chart"></div></template>
<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
const props = defineProps({ data: { type: Array, default: () => [] }, type: { type: String, default: 'diagnosis' } })
const chartRef = ref(null); let chart
const diagnosis = [{ key: 'innerRace', name: '轴承内圈故障', color: '#4599ed' }, { key: 'outerRace', name: '轴承外圈故障', color: '#8b6edc' }]
const vibration = [{ key: 'warning', name: '预警', color: '#f3d400' }, { key: 'alarm', name: '报警', color: '#ff9800' }, { key: 'danger', name: '危险', color: '#d53535' }]
function render() { if (!chart) return; const categories = props.type === 'vibration' ? vibration : diagnosis; chart.setOption({ color: categories.map((x) => x.color), grid: { left: 88, right: 10, top: 8, bottom: 8 }, tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (params) => { const row = props.data[params[0]?.dataIndex] || {}; const total = params.reduce((sum, item) => sum + Number(item.value || 0), 0); return `${row.equipmentCode || ''}<br/><b>共 ${total} 次</b><br/>${params.map((item) => `${item.marker}${item.seriesName}：${item.value} 次`).join('<br/>')}` } }, xAxis: { type: 'value', show: false }, yAxis: { type: 'category', inverse: true, data: props.data.map((x) => x.equipmentCode), axisLabel: { fontSize: 11, width: 82, overflow: 'truncate' }, axisTick: { show: false }, axisLine: { show: false } }, series: categories.map((category) => ({ name: category.name, type: 'bar', stack: 'total', barMaxWidth: 15, data: props.data.map((x) => x[category.key] || 0) })) }, true); chart.resize() }
onMounted(async () => { await nextTick(); chart = echarts.init(chartRef.value); render(); window.addEventListener('resize', render) })
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', render); chart?.dispose() })
</script>
<style scoped>.statistics-equipment-chart { width: 100%; height: 100%; min-height: 0; }</style>
