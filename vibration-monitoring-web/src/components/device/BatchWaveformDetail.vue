<template>
  <section class="batch-detail">
    <div class="detail-heading">
      <button v-if="mode === 'acceleration'" class="listen-button" :class="{ playing: isListening }" :disabled="!detail || listeningLoading" @click="toggleListening">
        {{ listeningLoading ? '加载中…' : (isListening ? '停止听诊' : '模拟听诊') }}
      </button>
      <span>{{ title }}</span>
      <div class="detail-tabs">
        <div class="signal-tabs">
          <button :class="{ active: mode === 'velocity' }" @click="emit('select-measure', 'velocity')">速度</button>
          <button :class="{ active: mode === 'acceleration' }" @click="emit('select-measure', 'acceleration')">加速度</button>
        </div>
        <div class="axis-tabs">
          <button v-for="item in axes" :key="item.key" :class="{ active: axis === item.key }" @click="axis = item.key">{{ item.name }}</button>
        </div>
      </div>
    </div>
    <div ref="waveformEl" v-show="detail" class="detail-chart" aria-label="振动波形"></div>
    <div ref="spectrumEl" v-show="detail" class="detail-chart" aria-label="振动频谱"></div>
    <div v-if="detail" class="diagnosis-result">
      <h3>智能诊断结果</h3>
      <div v-if="diagnosis" class="diagnosis-content">
        <div class="diagnosis-item"><span>诊断结论</span><b class="diagnosis-value"><i :style="{ backgroundColor: diagnosisColor }"></i>{{ diagnosis.text }}</b></div>
        <div class="diagnosis-item"><span>置信度</span><b>{{ percentage(diagnosis.confidence) }}</b></div>
        <div class="diagnosis-item"><span>结果稳定性</span><b>{{ percentage(diagnosis.stability) }}</b></div>
        <div class="diagnosis-item diagnosis-advice"><span>维护建议</span><p>{{ diagnosis.suggestion || '建议结合后续趋势和现场点检综合确认。' }}</p></div>
      </div>
      <div v-else class="diagnosis-content diagnosis-empty">该采集批次暂无智能诊断结果</div>
    </div>
    <div v-if="!detail" class="empty">请选择左侧一条采集记录</div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { fetchWaveformDetail } from '@/api/equipment'

use([LineChart, GridComponent, TitleComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({ detail: { type: Object, default: null }, mode: { type: String, required: true }, diagnosis: { type: Object, default: null } })
const emit = defineEmits(['select-measure'])
const axes = [{ key: 'x', name: '垂直' }, { key: 'y', name: '水平' }, { key: 'z', name: '轴向' }]
const axis = ref('x')
const waveformEl = ref(null); const spectrumEl = ref(null)
const isListening = ref(false); const listeningLoading = ref(false)
let waveformChart; let spectrumChart
let audioContext; let audioSource
const title = computed(() => {
  if (!props.detail) return '波形与频谱'
  return [props.detail.equipmentCode, props.detail.sensorCode, props.detail.mountPosition, formatTime(props.detail.collectTime)].filter(Boolean).join(' / ')
})
const signalName = computed(() => props.mode === 'velocity' ? '振动速度' : '振动加速度')
const signal = computed(() => props.mode === 'velocity' ? props.detail?.velocity : props.detail?.acceleration)
const spectrum = computed(() => props.mode === 'velocity' ? props.detail?.velocitySpectrum : props.detail?.accelerationSpectrum)
const diagnosisColor = computed(() => ({ '健康': '#6cba4e', '轴承内圈故障': '#4599ed', '轴承外圈故障': '#8b6edc' })[props.diagnosis?.text] || '#9dacba')
function formatTime(value) { return String(value || '').replace('T', ' ') }
function percentage(value) { return value == null ? '--' : `${(Number(value) * 100).toFixed(1)}%` }
function stopListening() {
  if (audioSource) { audioSource.onended = null; audioSource.stop(); audioSource.disconnect(); audioSource = null }
  isListening.value = false
}
async function toggleListening() {
  if (isListening.value) { stopListening(); return }
  if (!props.detail?.batchId) return
  listeningLoading.value = true
  try {
    const audioDetail = await fetchWaveformDetail(props.detail.batchId, 10000)
    const samples = audioDetail?.acceleration?.[axis.value] || []
    if (!samples.length) throw new Error('当前批次没有可播放的原始波形')
    const peak = Math.max(...samples.map(value => Math.abs(Number(value) || 0))) || 1
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) throw new Error('当前浏览器不支持模拟听诊')
    audioContext ||= new AudioContextClass()
    await audioContext.resume()
    const buffer = audioContext.createBuffer(1, samples.length, Number(audioDetail.sampleRate) || 10000)
    const channel = buffer.getChannelData(0)
    samples.forEach((value, index) => { channel[index] = (Number(value) || 0) / peak * 0.7 })
    audioSource = audioContext.createBufferSource()
    audioSource.buffer = buffer
    audioSource.connect(audioContext.destination)
    audioSource.onended = () => { audioSource = null; isListening.value = false }
    audioSource.start()
    isListening.value = true
  } catch (error) {
    console.error('模拟听诊播放失败', error)
    window.alert(error?.message || '模拟听诊播放失败')
  } finally { listeningLoading.value = false }
}
function render() {
  if (!waveformChart || !spectrumChart || !props.detail) return
  const waveform = signal.value; const frequency = spectrum.value
  const currentAxis = axes.find(item => item.key === axis.value)?.name || ''
  waveformChart.setOption({
    title: { text: `${signalName.value}波形（${currentAxis}，采样频率：${props.detail.sampleRate} Hz）`, left: 'center', textStyle: { fontSize: 13, fontWeight: 'normal', color: '#25384c' } },
    tooltip: { trigger: 'axis', valueFormatter: value => `${Number(value).toFixed(4)} ${waveform?.unit || ''}` },
    grid: { left: 55, right: 20, top: 38, bottom: 38 },
    xAxis: { type: 'value', name: 's', nameLocation: 'middle', nameGap: 26, axisLabel: { fontSize: 10 }, splitLine: { lineStyle: { color: '#e5edf6' } } },
    yAxis: { type: 'value', name: waveform?.unit || '', nameLocation: 'middle', nameGap: 40, axisLabel: { fontSize: 10 }, splitLine: { lineStyle: { color: '#e5edf6' } } },
    series: [{ type: 'line', showSymbol: false, data: (waveform?.timeSeconds || []).map((time, index) => [time, waveform?.[axis.value]?.[index]]), lineStyle: { color: '#145cff', width: 1 }, emphasis: { disabled: true } }]
  }, true)
  spectrumChart.setOption({
    title: { text: `${signalName.value}频谱（${currentAxis}，采样频率：${props.detail.sampleRate} Hz）`, left: 'center', textStyle: { fontSize: 13, fontWeight: 'normal', color: '#25384c' } },
    tooltip: { trigger: 'axis', valueFormatter: value => `${Number(value).toFixed(4)} ${frequency?.unit || ''}` },
    grid: { left: 55, right: 20, top: 38, bottom: 38 },
    xAxis: { type: 'value', name: 'Hz', nameLocation: 'middle', nameGap: 26, axisLabel: { fontSize: 10 }, splitLine: { lineStyle: { color: '#e5edf6' } } },
    yAxis: { type: 'value', name: frequency?.unit || '', nameLocation: 'middle', nameGap: 40, axisLabel: { fontSize: 10 }, splitLine: { lineStyle: { color: '#e5edf6' } } },
    series: [{ type: 'line', showSymbol: false, data: (frequency?.frequencyHz || []).map((hz, index) => [hz, frequency?.[axis.value]?.[index]]), lineStyle: { color: '#145cff', width: 1 }, emphasis: { disabled: true } }]
  }, true)
}
function resize() { waveformChart?.resize(); spectrumChart?.resize() }
onMounted(async () => { await nextTick(); waveformChart = init(waveformEl.value); spectrumChart = init(spectrumEl.value); render(); window.addEventListener('resize', resize) })
watch(() => [props.detail, props.mode, axis.value], async () => { stopListening(); await nextTick(); render(); resize() }, { deep: true })
onBeforeUnmount(() => { stopListening(); audioContext?.close(); window.removeEventListener('resize', resize); waveformChart?.dispose(); spectrumChart?.dispose() })
</script>

<style scoped>
.batch-detail { min-width: 0; min-height: 100%; background: #fff; }.detail-heading { display: flex; align-items: center; justify-content: center; min-height: 44px; padding: 0 12px; color: #1f2f40; font-size: 14px; border-bottom: 1px solid #e5ebf2; position: relative; }.listen-button { position: absolute; left: 12px; height: 28px; padding: 0 12px; color: #3176b9; font-size: 12px; cursor: pointer; background: #fff; border: 1px solid #82b6e8; border-radius: 3px; }.listen-button:hover, .listen-button.playing { color: #fff; background: #4095e5; border-color: #4095e5; }.listen-button:disabled { color: #9aa8b7; cursor: not-allowed; background: #f4f6f8; border-color: #d9e0e7; }.detail-tabs { position: absolute; right: 12px; display: flex; }.signal-tabs, .axis-tabs { display: flex; }.signal-tabs { margin-right: 10px; }.signal-tabs button, .axis-tabs button { height: 28px; min-width: 66px; color: #40556b; cursor: pointer; background: #fff; border: 1px solid #ccd7e1; }.signal-tabs button + button, .axis-tabs button + button { margin-left: -1px; }.signal-tabs button.active, .axis-tabs button.active { color: #fff; background: #4095e5; border-color: #4095e5; position: relative; }.detail-chart { height: 250px; }.diagnosis-result { margin: 10px 16px 16px; background: #fff; border: 1px solid #e2e9f1; }.diagnosis-result h3 { height: 34px; margin: 0; padding-left: 10px; color: #3176b9; font-size: 14px; line-height: 34px; border-bottom: 1px solid #e7edf3; border-left: 4px solid #367fc4; }.diagnosis-content { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0; padding: 12px 14px; }.diagnosis-item { display: flex; align-items: center; gap: 12px; min-height: 28px; color: #53677b; font-size: 13px; }.diagnosis-item > span { color: #8492a0; }.diagnosis-item b { color: #314d69; font-weight: 500; }.diagnosis-value { display: inline-flex; align-items: center; gap: 7px; }.diagnosis-value i { width: 10px; height: 10px; }.diagnosis-advice { grid-column: 1 / -1; align-items: flex-start; margin-top: 8px; padding-top: 10px; border-top: 1px dashed #e3eaf2; }.diagnosis-advice p { flex: 1; min-width: 0; margin: 0; color: #65768a; line-height: 1.6; }.diagnosis-empty { display: block; color: #8998a7; font-size: 13px; }.empty { height: calc(100% - 44px); display: grid; color: #95a4b3; place-items: center; }
</style>
