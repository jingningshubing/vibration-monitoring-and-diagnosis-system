<template>
  <main class="device-vibration-page">
    <header class="trend-toolbar">
      <span class="crumb">设备振动详情</span>
      <select v-model="selectedEquipmentId" aria-label="设备">
        <option v-for="item in equipmentOptions" :key="item.id" :value="String(item.id)">{{ item.equipCode }}</option>
      </select>
      <select v-model="selectedSensorId" aria-label="测点">
        <option v-for="item in sensorsForEquipment" :key="item.id" :value="String(item.id)">{{ item.code }}<template v-if="item.mountPosition"> / {{ item.mountPosition }}</template></option>
      </select>
      <span class="time-label">时间</span><input v-model="startDate" type="date" /><span class="separator">至</span><input v-model="endDate" type="date" />
    </header>

    <section class="trend-panel">
      <h2><span>{{ equipmentName }} / {{ sensorName }} · 振动预警趋势</span><div class="trend-measure-tabs"><button :class="{ active: measure === 'velocity' }" @click="measure = 'velocity'">速度</button><button :class="{ active: measure === 'acceleration' }" @click="measure = 'acceleration'">加速度</button></div></h2>
      <div class="trend-content"><AxisTrendChart :data="axisTrendData" :measure="measure" :thresholds="selectedSensorThresholds" @select-batch="selectBatchFromTrend" /></div>
    </section>

    <section class="batch-panel">
      <aside ref="batchListRef" class="batch-list">
        <div class="list-head"><span>振动预警</span><span>智能诊断</span><span>时间</span></div>
        <button v-for="item in batches" :key="item.batchId" :ref="(element) => setBatchRow(item.batchId, element)" class="batch-row" :class="{ selected: String(selectedBatchId) === String(item.batchId) }" @click="selectedBatchId = item.batchId">
          <i class="status-dot" :style="{ backgroundColor: vibrationColor(item.status) }"></i>
          <i v-if="diagnosisColor(item.diagnosisText)" class="status-dot diagnosis-dot" :style="{ backgroundColor: diagnosisColor(item.diagnosisText) }"></i><i v-else class="status-dot diagnosis-dot empty-dot"></i>
          <time>{{ formatTime(item.time) }}</time>
        </button>
        <div v-if="!batches.length" class="list-empty">该时间范围没有采集记录</div>
      </aside>
      <div class="detail-area">
        <div v-if="detailLoading" class="detail-loading">正在加载波形数据…</div>
        <div v-else-if="detailError" class="detail-error">{{ detailError }}</div>
        <BatchWaveformDetail v-else :detail="waveformDetail" :mode="detailMeasure" :diagnosis="selectedDiagnosis" @select-measure="detailMeasure = $event" />
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchEquipment, fetchVibrationAlarmOptions, fetchDeviceAxisTrend, fetchWaveformDetail } from '@/api/equipment'
import AxisTrendChart from '@/components/device/AxisTrendChart.vue'
import BatchWaveformDetail from '@/components/device/BatchWaveformDetail.vue'

const route = useRoute(); const router = useRouter()
const equipmentOptions = ref([]); const sensorOptions = ref([]); const axisTrendData = ref([])
const selectedEquipmentId = ref(String(route.query.equipmentId || '')); const selectedSensorId = ref(''); const measure = ref('velocity')
const detailMeasure = ref('velocity')
const selectedBatchId = ref(null); const waveformDetail = ref(null); const detailLoading = ref(false); const detailError = ref('')
const batchListRef = ref(null)
const batchRowElements = new Map()
const today = new Date(); const thirtyDaysAgo = new Date(today); thirtyDaysAgo.setDate(today.getDate() - 29)
const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const startDate = ref(route.query.startDate || fmt(thirtyDaysAgo)); const endDate = ref(route.query.endDate || fmt(today))
const sensorsForEquipment = computed(() => sensorOptions.value.filter((item) => String(item.equipmentId) === selectedEquipmentId.value))
const equipmentName = computed(() => equipmentOptions.value.find((item) => String(item.id) === selectedEquipmentId.value)?.equipCode || '--')
const sensorName = computed(() => sensorsForEquipment.value.find((item) => String(item.id) === selectedSensorId.value)?.code || '--')
const batches = computed(() => [...axisTrendData.value].filter(item => item.batchId).reverse())
const selectedBatch = computed(() => batches.value.find(item => String(item.batchId) === String(selectedBatchId.value)) || null)
const selectedDiagnosis = computed(() => {
  const batch = selectedBatch.value
  if (!batch?.diagnosisText) return null
  return { text: batch.diagnosisText, confidence: batch.diagnosisConfidence, stability: batch.diagnosisStability, suggestion: batch.maintenanceSuggestion }
})
const selectedSensorThresholds = computed(() => {
  const sensor = sensorsForEquipment.value.find((item) => String(item.id) === selectedSensorId.value) || {}
  return { warning: Number(sensor.warningThreshold || 0), alarm: Number(sensor.alarmThreshold || 0), danger: Number(sensor.dangerThreshold || 0) }
})
function formatTime(value) { return String(value || '').replace('T', ' ').slice(0, 16) }
function vibrationColor(status) { return ({ WARNING: '#ffe000', ALARM: '#ff9d00', DANGER: '#cf3535' })[status] || '#75be4f' }
function diagnosisColor(text) { if (text === '健康') return '#6cba4e'; if (text === '轴承内圈故障') return '#4599ed'; if (text === '轴承外圈故障') return '#8b6edc'; return '' }
function setBatchRow(batchId, element) { if (element) batchRowElements.set(batchId, element); else batchRowElements.delete(batchId) }
async function selectBatchFromTrend(batchId) {
  selectedBatchId.value = batchId
  await nextTick()
  const row = batchRowElements.get(batchId)
  const list = batchListRef.value
  if (!row || !list) return
  const listRect = list.getBoundingClientRect()
  const rowRect = row.getBoundingClientRect()
  list.scrollTop = Math.max(0, list.scrollTop + rowRect.top - listRect.top - (list.clientHeight - rowRect.height) / 2)
}
async function loadDetail() {
  waveformDetail.value = null; detailError.value = ''
  if (!selectedBatchId.value) return
  detailLoading.value = true
  try { waveformDetail.value = await fetchWaveformDetail(selectedBatchId.value) } catch (error) { detailError.value = error?.response?.data?.message || '该批次波形数据加载失败' } finally { detailLoading.value = false }
}
async function load() {
  if (!selectedSensorId.value) { axisTrendData.value = []; selectedBatchId.value = null; return }
  try {
    axisTrendData.value = await fetchDeviceAxisTrend({ sensorId: selectedSensorId.value, startDate: startDate.value, endDate: endDate.value, measure: measure.value })
    selectedBatchId.value = batches.value[0]?.batchId || null
  } catch (error) { console.error('加载测点振动趋势失败', error); axisTrendData.value = []; selectedBatchId.value = null }
}
function syncQuery() { router.replace({ query: { equipmentId: selectedEquipmentId.value, sensorId: selectedSensorId.value, startDate: startDate.value, endDate: endDate.value } }) }
watch(selectedEquipmentId, () => { selectedSensorId.value = String(sensorsForEquipment.value[0]?.id || ''); syncQuery(); load() })
watch([selectedSensorId, startDate, endDate], () => { syncQuery(); load() })
watch(measure, () => { detailMeasure.value = measure.value; load() })
watch(selectedBatchId, loadDetail)
onMounted(async () => {
  const [equipment, options] = await Promise.all([fetchEquipment(), fetchVibrationAlarmOptions()])
  equipmentOptions.value = equipment; sensorOptions.value = options.sensors || []
  if (!selectedEquipmentId.value) selectedEquipmentId.value = String(equipment[0]?.id || '')
  selectedSensorId.value = String(route.query.sensorId || sensorsForEquipment.value[0]?.id || '')
  await load()
})
</script>

<style scoped>
.device-vibration-page { display: flex; flex-direction: column; height: calc(100vh - 68px); min-height: 0; padding: 10px 12px 18px; overflow: hidden; color: #34495e; background: #f5f7fa; box-sizing: border-box; }
.trend-toolbar { position: fixed; z-index: 3; top: 0; right: 0; left: 64px; display: flex; align-items: center; gap: 16px; height: 68px; padding: 0 28px; background: #fff; border-bottom: 1px solid #dfe5ec; box-sizing: border-box; }
.crumb { margin-right: 12px; color: #253548; font-size: 18px; font-weight: 600; white-space: nowrap; }.trend-toolbar select, .trend-toolbar input { height: 34px; padding: 0 10px; color: #526273; font: inherit; font-size: 14px; background: #fff; border: 1px solid #ccd7e1; border-radius: 3px; box-sizing: border-box; }.trend-toolbar select { width: 230px; }.trend-toolbar input { width: 160px; }.time-label, .separator { color: #526273; font-size: 14px; white-space: nowrap; }.time-label { margin-left: 4px; }
.trend-panel { flex: none; margin-top: 8px; background: #fff; border-radius: 3px; }.trend-panel h2 { display: flex; align-items: center; height: 42px; margin: 0; padding: 0 16px; color: #3176b9; font-size: 16px; line-height: 42px; background: #fff; border-left: 5px solid #367fc4; }.trend-measure-tabs { display: flex; margin-left: auto; }.trend-measure-tabs button { height: 28px; min-width: 66px; color: #526273; cursor: pointer; background: #fff; border: 1px solid #ccd7e1; }.trend-measure-tabs button + button { margin-left: -1px; }.trend-measure-tabs button.active { color: #fff; background: #4095e5; border-color: #4095e5; position: relative; }.trend-content { height: 330px; padding: 6px 12px; }
.batch-panel { display: grid; grid-template-columns: 340px minmax(0, 1fr); flex: 1; min-height: 0; margin-top: 10px; overflow: hidden; background: #fff; border: 1px solid #e0e7ef; }.batch-list { min-height: 0; overflow-x: hidden; overflow-y: auto; background: #f9fbfd; border-right: 1px solid #e0e7ef; }.list-head, .batch-row { display: grid; grid-template-columns: 90px 90px minmax(0, 1fr); align-items: center; box-sizing: border-box; }.list-head { position: sticky; z-index: 1; top: 0; height: 38px; padding: 0 12px; color: #7a8795; font-size: 13px; text-align: center; background: #fff; border-bottom: 1px solid #e0e7ef; }.list-head span { white-space: nowrap; }.batch-row { width: 100%; min-height: 42px; padding: 0 12px; color: #596a7b; font-size: 13px; text-align: left; cursor: pointer; background: transparent; border: 0; border-bottom: 1px solid #edf1f5; }.batch-row:hover { background: #eaf3fc; }.batch-row.selected { color: #315a7b; background: #d8ebfb !important; box-shadow: inset 4px 0 0 #4095e5; }.status-dot { width: 10px; height: 10px; justify-self: center; }.diagnosis-dot { width: 9px; height: 9px; }.empty-dot { background: transparent !important; }.batch-row time { min-width: 0; white-space: nowrap; }.list-empty { padding: 24px 10px; color: #9ba8b5; font-size: 13px; text-align: center; }.detail-area { min-width: 0; min-height: 0; overflow-x: hidden; overflow-y: auto; }.detail-loading, .detail-error { height: 100%; display: grid; place-items: center; color: #8493a1; }.detail-error { color: #d05c5c; }
@media (max-width: 800px) { .trend-toolbar { left: 56px; flex-wrap: wrap; height: 68px; padding: 6px 8px; }.trend-toolbar select { width: 145px; }.trend-toolbar input { flex: 1; min-width: 120px; }.measure-tabs { margin-left: 0; }.batch-panel { grid-template-columns: 190px minmax(0, 1fr); } }
</style>
