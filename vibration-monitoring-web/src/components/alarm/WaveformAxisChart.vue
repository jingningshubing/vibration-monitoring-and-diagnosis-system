<template>
  <article class="axis-chart">
    <h3>{{ axis.name }}</h3>
    <div ref="timeChart" class="chart" :aria-label="`${axis.name}轴时域波形`"></div>
    <div ref="spectrumChart" class="chart" :aria-label="`${axis.name}轴频谱`"></div>
  </article>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, GridComponent, TitleComponent, CanvasRenderer])

const props = defineProps({ axis: { type: Object, required: true }, mode: { type: String, required: true }, detail: { type: Object, required: true } })
const timeChart = ref(null); const spectrumChart = ref(null)
let timeInstance; let spectrumInstance
function values(group) { return group?.[props.axis.key] ?? [] }
function render() {
  if (!timeInstance || !spectrumInstance) return
  const waveform = props.mode === 'velocity' ? props.detail.velocity : props.detail.acceleration
  const unit = waveform?.unit ?? ''
  const signalName = props.mode === 'velocity' ? '速度' : '加速度'
  const samplingText = `采样频率：${props.detail.sampleRate} Hz`
  timeInstance.setOption({ title: { text: `${signalName}波形（${samplingText}）`, left: 'center', textStyle: { fontSize: 12, fontWeight: 'normal' } }, grid: { left: 48, right: 16, top: 31, bottom: 38 }, xAxis: { type: 'value', name: 's', nameLocation: 'middle', nameGap: 25, axisLabel: { fontSize: 10 } }, yAxis: { type: 'value', name: unit, nameLocation: 'middle', nameGap: 34, axisLabel: { fontSize: 10 }, splitLine: { lineStyle: { color: '#dce6f1' } } }, series: [{ type: 'line', showSymbol: false, data: (waveform?.timeSeconds ?? []).map((time, index) => [time, values(waveform)[index]]), lineStyle: { color: '#175cff', width: 1 } }] }, true)
  const spectrum = props.mode === 'velocity' ? props.detail.velocitySpectrum : props.detail.accelerationSpectrum
  spectrumInstance.setOption({ title: { text: `${signalName}频谱（${samplingText}）`, left: 'center', textStyle: { fontSize: 12, fontWeight: 'normal' } }, grid: { left: 48, right: 16, top: 31, bottom: 38 }, xAxis: { type: 'value', name: 'Hz', nameLocation: 'middle', nameGap: 25, axisLabel: { fontSize: 10 } }, yAxis: { type: 'value', name: spectrum?.unit ?? '', nameLocation: 'middle', nameGap: 36, axisLabel: { fontSize: 10 }, splitLine: { lineStyle: { color: '#dce6f1' } } }, series: [{ type: 'line', showSymbol: false, data: (spectrum?.frequencyHz ?? []).map((frequency, index) => [frequency, values(spectrum)[index]]), lineStyle: { color: '#175cff', width: 1 } }] }, true)
}
function resize() { timeInstance?.resize(); spectrumInstance?.resize() }
onMounted(async () => { await nextTick(); timeInstance = init(timeChart.value); spectrumInstance = init(spectrumChart.value); render(); window.addEventListener('resize', resize) })
watch(() => [props.mode, props.detail], render)
onBeforeUnmount(() => { window.removeEventListener('resize', resize); timeInstance?.dispose(); spectrumInstance?.dispose() })
</script>

<style scoped>
.axis-chart { display: grid; grid-template-columns: 54px 1fr 1fr; gap: 10px; min-height: 190px; }.axis-chart h3 { display: grid; margin: 0; color: #526273; font-size: 13px; font-weight: 500; line-height: 1.2; text-align: center; place-items: center; writing-mode: vertical-rl; }.chart { min-width: 0; height: 190px; }
</style>
