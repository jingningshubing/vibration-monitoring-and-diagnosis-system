<template>
  <section class="alarm-layout">
    <section class="alarm-table-panel">
      <div class="table-scroll">
        <table class="alarm-table">
          <thead>
            <tr><th>序号</th><th>时间</th><th>设备</th><th>位置</th><th>报警事件描述</th><th>详情</th></tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="6" class="empty-row">正在加载…</td></tr>
            <tr v-else-if="tableError"><td colspan="6" class="empty-row error-row"><span>!</span>{{ tableError }}<button @click="loadRecords">重新加载</button></td></tr>
            <tr v-else-if="!pageData.records.length"><td colspan="6" class="empty-row">当前条件下暂无报警记录</td></tr>
            <tr v-for="(row, index) in pageData.records" :key="row.id">
              <td>{{ (pageData.page - 1) * pageData.size + index + 1 }}</td>
              <td class="time-cell"><span>{{ formatDate(row.alarmTime) }}</span><small>{{ formatClock(row.alarmTime) }}</small></td>
              <td><span class="equipment-tag">{{ row.equipmentCode || '--' }}</span></td>
              <td class="position-cell"><router-link class="position-link" :to="{ path: '/device-vibration', query: { equipmentId: row.equipmentId, sensorId: row.sensorId } }">{{ row.mountPosition || row.sensorCode || '--' }}</router-link></td>
              <td class="alarm-message"><span class="alarm-indicator" :style="{ backgroundColor: statusColor(row.alarmLevel) }"></span><span>{{ row.message }}</span></td>
              <td><button class="detail-button" aria-label="查看波形详情" @click="openWaveform(row)"><svg viewBox="0 0 24 16" aria-hidden="true"><polyline points="1,9 4,9 6,3 9,14 12,6 15,10 18,2 21,9 23,9" /></svg></button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination-bar">
        <span>共 {{ pageData.total }} 条</span>
        <select aria-label="每页条数"><option>20条/页</option></select>
        <button class="page-arrow" :disabled="pageData.page <= 1" @click="goPage(pageData.page - 1)">‹</button>
        <button v-for="item in visiblePages" :key="item" class="page-number" :class="{ active: item === pageData.page }" @click="goPage(item)">{{ item }}</button>
        <button class="page-arrow" :disabled="pageData.page >= totalPages" @click="goPage(pageData.page + 1)">›</button><span>前往</span><input v-model.number="targetPage" @keyup.enter="goPage(targetPage)" aria-label="跳转页码" /><span>页</span>
      </footer>
    </section>

    <aside class="statistics-sidebar">
      <article class="statistics-card"><h2>报警台数/次数</h2><div class="card-content alarm-count-content"><div v-for="item in alarmDonuts" :key="item.name" class="count-donut"><svg viewBox="0 0 120 120"><circle class="donut-base" cx="60" cy="60" r="42"/><circle v-for="segment in item.segments" :key="segment.code" cx="60" cy="60" r="42" class="donut-segment" :stroke="segment.color" :stroke-dasharray="segment.dasharray" :stroke-dashoffset="segment.dashoffset" transform="rotate(-90 60 60)"/><text v-for="segment in item.segments.filter((segment) => segment.count > 0)" :key="`${segment.code}-label`" class="donut-segment-label" :x="segment.labelX" :y="segment.labelY">{{ segment.count }}</text><text x="60" y="64">{{ item.total }}</text></svg><span>{{ item.name }}</span><div class="donut-tooltip"><p class="tooltip-total">共 {{ item.total }} {{ item.unit }}</p><p v-for="segment in item.visibleSegments" :key="segment.code"><i :style="{ backgroundColor: segment.color }"></i>{{ segment.name }}：{{ segment.count }} {{ item.unit }}（{{ segment.percent }}%）</p></div></div></div></article>
      <article class="statistics-card"><h2>振动预警-时间（按天）</h2><div class="card-content"><AlarmTrendChart :data="alarmStatistics.dailyTrend" :show-normal="false" /></div></article>
      <article class="statistics-card"><h2>振动预警-设备</h2><div class="card-content equipment-scroll"><AlarmEquipmentChart :data="alarmStatistics.equipmentRanking" :show-normal="false" /></div></article>
    </aside>

    <div v-if="selectedRecord" class="waveform-overlay" @click.self="closeWaveform">
      <section class="waveform-dialog" role="dialog" aria-modal="true" aria-label="三轴波形详情">
        <header class="waveform-header">
          <h2>{{ selectedRecord.equipmentCode || '--' }} / {{ selectedRecord.mountPosition || selectedRecord.sensorCode || '--' }} @ {{ formatFullTime(selectedRecord.alarmTime) }}</h2>
          <button class="dialog-close" aria-label="关闭" @click="closeWaveform">×</button>
        </header>
        <nav class="waveform-tabs" aria-label="振动数据类型">
          <button :class="{ active: waveformType === 'diagnosis' }" @click="loadDiagnosis">诊断结论</button>
          <button :class="{ active: waveformType === 'velocity' }" @click="waveformType = 'velocity'">速度</button>
          <button :class="{ active: waveformType === 'acceleration' }" @click="waveformType = 'acceleration'">加速度</button>
        </nav>
        <div v-if="waveformLoading" class="waveform-content"><p>正在加载三轴波形与频谱…</p></div>
        <div v-else-if="waveformType === 'diagnosis' && diagnosisLoading" class="waveform-content"><p>正在进行辅助诊断…</p></div>
        <div v-else-if="waveformType === 'diagnosis' && diagnosisError" class="waveform-content empty-waveform"><p><strong>诊断暂不可用</strong>{{ diagnosisError }}</p><button @click="loadDiagnosis">重新诊断</button></div>
        <div v-else-if="waveformType === 'diagnosis' && diagnosisDetail" class="diagnosis-content">
          <h3>{{ diagnosisDetail.diagnosis }}</h3><p><b>维护建议：</b>{{ diagnosisDetail.description }}</p>
          <div class="diagnosis-metrics"><span>置信度 <b>{{ percent(diagnosisDetail.confidence) }}</b></span><span>结果稳定性 <b>{{ percent(diagnosisDetail.stability) }}</b></span><span>诊断窗口 <b>{{ diagnosisDetail.windowCount }} 个</b></span></div>
          <div class="probability-list"><p><i class="healthy"></i>健康 <b>{{ percent(diagnosisDetail.probabilities.healthy) }}</b></p><p><i class="inner"></i>内圈故障 <b>{{ percent(diagnosisDetail.probabilities.innerRace) }}</b></p><p><i class="outer"></i>外圈故障 <b>{{ percent(diagnosisDetail.probabilities.outerRace) }}</b></p></div>
        </div>
        <div v-else-if="waveformError" class="waveform-content empty-waveform"><p><strong>暂无可展示的波形</strong>{{ waveformError }}</p><button @click="openWaveform(selectedRecord)">重新加载</button></div>
        <div v-else-if="waveformDetail" class="waveform-charts">
          <WaveformAxisChart v-for="axis in axes" :key="axis.key" :axis="axis" :mode="waveformType" :detail="waveformDetail" />
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchVibrationAlarms, fetchVibrationAlarmStatistics, fetchWaveformDetail, fetchBearingDiagnosis } from '@/api/equipment'
import { statusLevels } from '@/config/statusLevels'
import WaveformAxisChart from '@/components/alarm/WaveformAxisChart.vue'
import AlarmTrendChart from '@/components/alarm/AlarmTrendChart.vue'
import AlarmEquipmentChart from '@/components/alarm/AlarmEquipmentChart.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tableError = ref('')
const targetPage = ref(1)
const selectedRecord = ref(null)
const waveformType = ref('velocity')
const waveformDetail = ref(null)
const waveformLoading = ref(false)
const waveformError = ref('')
const diagnosisDetail = ref(null)
const diagnosisLoading = ref(false)
const diagnosisError = ref('')
const axes = [{ key: 'x', name: '水平' }, { key: 'y', name: '垂直' }, { key: 'z', name: '轴向' }]
const pageData = ref({ records: [], total: 0, page: 1, size: 20 })
const alarmStatistics = ref({ equipment: { normal: 0, warning: 0, alarm: 0, danger: 0 }, records: { normal: 0, warning: 0, alarm: 0, danger: 0 }, dailyTrend: [], equipmentRanking: [] })
const alarmDonuts = computed(() => [{ name: '报警设备', unit: '台', levels: alarmStatistics.value.equipment, showNormal: true }, { name: '报警次数', unit: '次', levels: alarmStatistics.value.records, showNormal: false }].map((item) => { const total = Object.values(item.levels).reduce((sum, value) => sum + Number(value || 0), 0); const circumference = 2 * Math.PI * 42; let offset = 0; const segments = statusLevels.map((level) => { const count = Number(item.levels[level.code.toLowerCase()] || 0); const length = total ? count / total * circumference : 0; const angle = -Math.PI / 2 + (offset + length / 2) / circumference * 2 * Math.PI; const result = { code: level.code, name: level.name, count, percent: total ? Math.round(count / total * 100) : 0, color: level.color, dasharray: `${length} ${circumference}`, dashoffset: -offset, labelX: 60 + 42 * Math.cos(angle), labelY: 64 + 42 * Math.sin(angle) }; offset += length; return result }); return { ...item, total, segments, visibleSegments: item.showNormal ? segments : segments.filter((segment) => segment.code !== 'NORMAL') } }))
const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / pageData.value.size)))
const visiblePages = computed(() => Array.from({ length: Math.min(7, totalPages.value) }, (_, index) => index + 1))

/** 根据地址栏筛选和页码读取报警表格。 */
async function loadRecords() {
  loading.value = true
  tableError.value = ''
  try {
    const params = { ...route.query, page: Number(route.query.page) || 1, size: 20 }
    const [page, statistics] = await Promise.all([fetchVibrationAlarms(params), fetchVibrationAlarmStatistics(params)])
    pageData.value = page; alarmStatistics.value = statistics
    targetPage.value = pageData.value.page
  } catch (error) {
    console.error('读取振动预警记录失败：', error)
    tableError.value = error?.response?.data?.message || '报警记录加载失败，请检查后端服务后重试'
    pageData.value = { records: [], total: 0, page: 1, size: 20 }; alarmStatistics.value = { equipment: { normal: 0, warning: 0, alarm: 0, danger: 0 }, records: { normal: 0, warning: 0, alarm: 0, danger: 0 }, dailyTrend: [], equipmentRanking: [] }
  } finally { loading.value = false }
}

/** 切换表格页码，保留当前全部筛选条件。 */
function goPage(page) {
  const safePage = Math.max(1, Math.min(Number(page) || 1, totalPages.value))
  router.push({ query: { ...route.query, page: String(safePage) } })
}
function formatDate(value) { return value ? value.slice(0, 10) : '--' }
function formatClock(value) { return value ? value.replace('T', ' ').slice(11, 16) : '' }
function statusColor(level) { return statusLevels.find((item) => item.code === level)?.color ?? '#8a8a8a' }
/** 打开所选报警记录的波形详情弹窗，后续使用 batchId 加载真实三轴数据。 */
async function openWaveform(record) {
  selectedRecord.value = record; waveformType.value = 'velocity'; waveformDetail.value = null; waveformError.value = ''; diagnosisDetail.value = null; diagnosisError.value = ''; waveformLoading.value = true
  try {
    if (!record?.batchId) throw new Error('该报警记录未关联波形批次')
    const detail = await fetchWaveformDetail(record.batchId)
    const hasWaveform = detail?.velocity?.timeSeconds?.length && detail?.acceleration?.timeSeconds?.length
    if (!hasWaveform) throw new Error('该批次暂无波形文件或波形数据为空')
    waveformDetail.value = detail
  } catch (error) {
    console.error('读取波形详情失败：', error)
    waveformError.value = error?.response?.status === 404
      ? '该批次暂无波形文件，暂不能查看三轴波形。'
      : (error?.message || '波形数据读取失败，请稍后重试。')
  } finally { waveformLoading.value = false }
}
/** 用户主动打开诊断结论页签时才调用 ONNX 辅助诊断，不改变原有报警等级。 */
async function loadDiagnosis() {
  waveformType.value = 'diagnosis'
  if (diagnosisDetail.value || diagnosisLoading.value) return
  diagnosisError.value = ''; diagnosisLoading.value = true
  try { diagnosisDetail.value = await fetchBearingDiagnosis(selectedRecord.value?.batchId) } catch (error) { diagnosisError.value = error?.response?.data?.message || '当前批次无法完成辅助诊断，请稍后重试。' } finally { diagnosisLoading.value = false }
}
/** 关闭波形详情弹窗。 */
function closeWaveform() { selectedRecord.value = null; waveformDetail.value = null; diagnosisDetail.value = null }
function formatFullTime(value) { return value ? value.replace('T', ' ').slice(0, 19) : '--' }
function percent(value) { return `${Math.round(Number(value || 0) * 100)}%` }
watch(() => route.query, loadRecords, { immediate: true })
</script>

<style scoped>
.alarm-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; min-height: calc(100vh - 68px); background: #f4f6f8; }
.alarm-table-panel { display: grid; grid-template-rows: minmax(0, 1fr) 38px; min-width: 0; height: calc(100vh - 68px); padding: 8px 6px 0 8px; box-sizing: border-box; }
.table-scroll { min-height: 0; overflow: auto; background: #fff; border: 1px solid #e3e9f0; }
.alarm-table { width: 100%; min-width: 880px; border-collapse: collapse; table-layout: fixed; }
th, td { height: 58px; padding: 0 14px; color: #3f566c; font-size: 14px; text-align: center; border-right: 1px solid #e7edf3; border-bottom: 1px solid #e7edf3; box-sizing: border-box; }
th { height: 42px; color: #5d7590; font-size: 15px; font-weight: 700; letter-spacing: .5px; background: linear-gradient(180deg, #fbfdff, #f2f6fa); border-bottom: 2px solid #dce7f0; }
th:nth-child(1) { width: 60px; } th:nth-child(2) { width: 122px; } th:nth-child(3) { width: 160px; } th:nth-child(4) { width: 134px; } th:nth-child(6) { width: 78px; }
tbody tr { transition: background .16s ease, box-shadow .16s ease; } tbody tr:nth-child(even) td { background: #fbfcfe; } tbody tr:hover td { background: #f1f8ff; } tbody tr:hover { box-shadow: inset 3px 0 #4a9ced; }
.empty-row { color: #8b98a5; }.time-cell span, .time-cell small { display: block; line-height: 1.35; }.time-cell span { color: #405a73; font-variant-numeric: tabular-nums; }.time-cell small { margin-top: 3px; color: #8c9bad; font-size: 12px; }.equipment-tag { display: inline-flex; align-items: center; min-height: 27px; padding: 0 10px; color: #2869a8; font-weight: 600; letter-spacing: .2px; background: #eaf4ff; border: 1px solid #cce5fb; border-radius: 14px; white-space: nowrap; }.position-cell { color: #52708d; line-height: 1.45; }.position-link { color: #3176b9; text-decoration: none; }.position-link:hover { text-decoration: underline; }.alarm-message { display: grid; grid-template-columns: 10px minmax(0, 1fr); align-items: center; justify-content: start; column-gap: 10px; padding: 0 24px 0 30%; text-align: left; }.alarm-message > span:last-child { line-height: 1.55; }.alarm-indicator { width: 10px; height: 10px; border-radius: 2px; box-shadow: 0 0 0 3px #edf2f7; }
.detail-button { display: inline-grid; width: 45px; height: 32px; padding: 0; cursor: pointer; place-items: center; background: #4599ed; border: 0; border-radius: 4px; }.detail-button:hover { background: #2e86da; }.detail-button svg { width: 22px; height: 16px; overflow: visible; }.detail-button polyline { fill: none; stroke: #fff; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; }
.pagination-bar { display: flex; align-items: center; justify-content: center; gap: 12px; height: 38px; color: #435466; font-size: 13px; background: #fff; border: 1px solid #e3e9f0; border-top: 0; box-sizing: border-box; }
.pagination-bar select, .pagination-bar input { height: 27px; padding: 0 8px; color: #526273; background: #fff; border: 1px solid #d5dfe8; border-radius: 3px; } .pagination-bar input { width: 34px; text-align: center; }
.pagination-bar button { padding: 0; color: #31475f; cursor: pointer; background: transparent; border: 0; }.page-number { width: 17px; height: 25px; }.page-number.active { color: #3387dd; font-weight: 700; }.page-arrow { color: #a9b3bd !important; font-size: 21px; }.pagination-bar button:disabled { cursor: default; opacity: .4; }
.statistics-sidebar { display: grid; grid-template-rows: repeat(3, minmax(0, 1fr)); gap: 8px; height: calc(100vh - 68px); padding: 8px 8px 0; box-sizing: border-box; background: #fff; border-left: 1px solid #dce3eb; }
.statistics-card { display: grid; position: relative; z-index: 1; grid-template-rows: 48px minmax(0, 1fr); min-height: 0; overflow: visible; background: #f7f7f7; border-radius: 3px; }.statistics-card:has(.count-donut:hover) { z-index: 6; }
.statistics-card h2 { display: flex; align-items: center; margin: 0; padding: 0 16px; color: #3176b9; font-size: 17px; font-weight: 700; background: #fff; }.statistics-card h2::before { width: 5px; height: 20px; margin-right: 11px; content: ''; background: #367fc4; }
.card-content { min-height: 0; background: #f7f7f7; }
.equipment-scroll { overflow-y: auto; }
.alarm-count-content { display: flex; align-items: center; justify-content: space-evenly; padding: 8px 4px; }.count-donut { position: relative; display: grid; justify-items: center; gap: 3px; color: #617385; font-size: 13px; }.count-donut svg { width: 108px; height: 108px; }.count-donut circle { fill: none; stroke-width: 18px; }.donut-base { stroke: #e4eaf0; }.donut-segment { stroke-linecap: butt; }.count-donut text { fill: #34495e; font-size: 24px; font-weight: 700; text-anchor: middle; }.count-donut .donut-segment-label { fill: #fff; font-size: 12px; font-weight: 700; dominant-baseline: middle; }.donut-tooltip { position: absolute; z-index: 5; right: calc(100% + 10px); bottom: 0; display: none; min-width: 170px; padding: 10px 12px; color: #526273; background: #fff; border: 1px solid #75be4f; border-radius: 4px; box-shadow: 0 3px 10px #0002; }.count-donut:hover .donut-tooltip { display: block; }.donut-tooltip p { margin: 5px 0; font-size: 12px; white-space: nowrap; }.donut-tooltip .tooltip-total { margin-top: 0; color: #42576b; font-weight: 700; }.donut-tooltip p:last-child { margin-bottom: 0; }.donut-tooltip i { display: inline-block; width: 10px; height: 10px; margin-right: 7px; vertical-align: -1px; }
.waveform-overlay { position: fixed; z-index: 30; inset: 0; display: grid; padding: 24px; background: #1d2e3d73; place-items: center; box-sizing: border-box; }.waveform-dialog { display: grid; grid-template-rows: 56px 44px minmax(360px, 1fr); width: min(1220px, 100%); height: min(760px, 100%); overflow: hidden; background: #fff; border-radius: 4px; box-shadow: 0 12px 34px #13213055; }.waveform-header { display: flex; align-items: center; justify-content: space-between; padding: 0 18px; border-bottom: 1px solid #e2e8ef; }.waveform-header h2 { margin: 0; overflow: hidden; color: #34495e; font-size: 17px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }.dialog-close { width: 30px; height: 30px; padding: 0; color: #8997a5; font-size: 24px; line-height: 1; cursor: pointer; background: transparent; border: 0; }.dialog-close:hover { color: #367fc4; }.waveform-tabs { display: flex; align-items: end; gap: 2px; padding: 0 18px; border-bottom: 1px solid #dce4ec; }.waveform-tabs button { min-width: 68px; height: 32px; color: #526273; cursor: pointer; background: #fff; border: 1px solid #d4dfe9; border-bottom: 0; border-radius: 3px 3px 0 0; }.waveform-tabs button.active { color: #fff; background: #4599ed; border-color: #4599ed; }.waveform-content { display: grid; align-content: center; justify-items: center; gap: 10px; color: #8292a2; background: linear-gradient(#f9fbfd 1px, transparent 1px), linear-gradient(90deg, #f9fbfd 1px, transparent 1px); background-size: 32px 32px; }.waveform-content p { margin: 0; color: #526273; font-size: 17px; }.waveform-charts { min-height: 0; padding: 10px 16px; overflow: auto; background: #fff; }
.error-row { color: #c95555; }.error-row span { display: inline-grid; width: 18px; height: 18px; margin-right: 8px; color: #fff; font-size: 12px; font-weight: 700; background: #d9534f; border-radius: 50%; place-items: center; }.error-row button, .empty-waveform button { margin-left: 14px; padding: 5px 10px; color: #337dcc; cursor: pointer; background: #fff; border: 1px solid #bcd6ee; border-radius: 3px; }.error-row button:hover, .empty-waveform button:hover { color: #fff; background: #4599ed; border-color: #4599ed; }.empty-waveform { gap: 12px; }.empty-waveform p { display: grid; gap: 8px; text-align: center; }.empty-waveform strong { color: #536f87; font-size: 18px; }.empty-waveform button { margin-left: 0; }
.diagnosis-content { display: grid; align-content: center; justify-items: center; gap: 14px; min-height: 0; padding: 36px; color: #526273; text-align: center; background: #f8fbfe; }.diagnosis-content h3 { margin: 0; color: #3278be; font-size: 22px; }.diagnosis-content > p { margin: 0; color: #657789; }.diagnosis-metrics { display: flex; gap: 26px; }.diagnosis-metrics span { display: grid; gap: 6px; min-width: 105px; padding: 11px 15px; font-size: 13px; background: #fff; border: 1px solid #dce7f0; border-radius: 4px; }.diagnosis-metrics b { color: #34495e; font-size: 18px; }.probability-list { display: flex; gap: 10px; }.probability-list p { min-width: 112px; margin: 0; padding: 9px 12px; color: #526273; background: #fff; border: 1px solid #e0e8ef; border-radius: 4px; }.probability-list i { display: inline-block; width: 9px; height: 9px; margin-right: 6px; border-radius: 50%; }.probability-list b { margin-left: 8px; }.healthy { background: #6cba4e; }.inner { background: #ff9f0a; }.outer { background: #d83b3b; }
@media (max-width: 1000px) { .alarm-layout { grid-template-columns: 1fr; }.alarm-table-panel { height: auto; min-height: calc(100vh - 68px); }.statistics-sidebar { grid-template-columns: repeat(3, minmax(220px, 1fr)); grid-template-rows: 230px; height: 246px; overflow-x: auto; border-top: 1px solid #dce3eb; border-left: 0; } }
</style>
