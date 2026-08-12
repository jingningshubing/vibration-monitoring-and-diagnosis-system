<template>
  <main class="statistics-page">
    <div class="content-grid">
    <div class="left-column">
    <section class="overview-section">
      <header class="section-heading">
        <h2>设备概况</h2>
        <div class="export-actions">
          <button type="button" @click="exportWord">导出报表（Word）</button>
          <button type="button" @click="exportCsv">导出报表（CSV）</button>
        </div>
      </header>

      <div class="overview-cards">
        <article class="overview-card">
          <span class="overview-icon"><Monitor /></span>
          <div><p>设备数量（台）</p><strong>{{ overview.equipmentCount }}</strong></div>
        </article>
        <article class="overview-card">
          <span class="overview-icon"><DataAnalysis /></span>
          <div><p>传感器数量（支）</p><strong>{{ overview.sensorCount }}</strong></div>
        </article>
      </div>
    </section>

    <section class="health-section">
      <h2 class="section-heading">健康监测-设备</h2>
      <div class="statistics-table-wrap">
        <table class="statistics-table">
          <thead>
            <tr><th rowspan="2">序号</th><th rowspan="2">设备名称</th><th colspan="3">智能诊断</th><th colspan="4">振动预警</th></tr>
            <tr>
              <th>轴承内圈故障(次)<i class="inner-fault"></i></th><th>轴承外圈故障(次)<i class="outer-fault"></i></th><th>总数(次)</th>
              <th>预警(次)<i class="yellow"></i></th><th>报警(次)<i class="orange"></i></th><th>危险(次)<i class="red"></i></th><th>总数(次)<i class="sort-mark"></i></th>
            </tr>
          </thead>
          <tbody v-if="statisticsRows.length">
            <tr v-for="row in statisticsRows" :key="row.index">
              <td>{{ row.index }}</td><td class="device-name">{{ row.name }}</td>
              <td>{{ row.innerRace }}</td><td>{{ row.outerRace }}</td><td>{{ row.diagnosisTotal }}</td>
              <td>{{ row.vibrationWarning }}</td><td>{{ row.vibrationAlarm }}</td><td>{{ row.vibrationDanger }}</td><td>{{ row.vibrationTotal }}</td>
            </tr>
          </tbody>
          <tbody v-else><tr><td colspan="9" class="empty-data">{{ loading ? '正在加载统计数据…' : '当前时间范围暂无统计数据' }}</td></tr></tbody>
          <tfoot v-if="statisticsRows.length">
            <tr><td colspan="2">合计</td><td>{{ totals.innerRace }}</td><td>{{ totals.outerRace }}</td><td>{{ totals.diagnosisTotal }}</td><td>{{ totals.vibrationWarning }}</td><td>{{ totals.vibrationAlarm }}</td><td>{{ totals.vibrationDanger }}</td><td>{{ totals.vibrationTotal }}</td></tr>
          </tfoot>
        </table>
      </div>
    </section>
    </div>
    <aside class="chart-sidebar">
      <article class="chart-card"><h2>智能诊断-时间（近 {{ periodDays }} 天）</h2><StatisticsTrendChart :data="diagnosisTrend" type="diagnosis" /></article>
      <article class="chart-card"><h2>智能诊断-设备</h2><StatisticsEquipmentChart :data="diagnosisRanking" type="diagnosis" /></article>
      <article class="chart-card"><h2>振动预警-时间（近 {{ periodDays }} 天）</h2><StatisticsTrendChart :data="vibrationTrend" type="vibration" /></article>
      <article class="chart-card"><h2>振动预警-设备</h2><StatisticsEquipmentChart :data="vibrationRanking" type="vibration" /></article>
    </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Monitor } from '@element-plus/icons-vue'
import StatisticsTrendChart from '@/components/statistics/StatisticsTrendChart.vue'
import StatisticsEquipmentChart from '@/components/statistics/StatisticsEquipmentChart.vue'
import { fetchEquipment, fetchVibrationAlarmOptions, fetchVibrationAlarmStatistics, fetchIntelligentDiagnosisStatistics } from '@/api/equipment'

const route = useRoute()
const loading = ref(false)
const statisticsRows = ref([])
const diagnosisTrend = ref([])
const vibrationTrend = ref([])
const diagnosisRanking = ref([])
const vibrationRanking = ref([])
const periodDays = computed(() => {
  const start = new Date(`${route.query.startDate || '2026-07-07'}T00:00:00`)
  const end = new Date(`${route.query.endDate || '2026-08-05'}T00:00:00`)
  return Math.max(1, Math.round((end - start) / 86400000) + 1)
})
const overview = reactive({ equipmentCount: 0, sensorCount: 0 })
const totals = computed(() => statisticsRows.value.reduce((sum, row) => ({
  innerRace: sum.innerRace + row.innerRace,
  outerRace: sum.outerRace + row.outerRace,
  diagnosisTotal: sum.diagnosisTotal + row.diagnosisTotal,
  vibrationWarning: sum.vibrationWarning + row.vibrationWarning,
  vibrationAlarm: sum.vibrationAlarm + row.vibrationAlarm,
  vibrationDanger: sum.vibrationDanger + row.vibrationDanger,
  vibrationTotal: sum.vibrationTotal + row.vibrationTotal,
}), { innerRace: 0, outerRace: 0, diagnosisTotal: 0, vibrationWarning: 0, vibrationAlarm: 0, vibrationDanger: 0, vibrationTotal: 0 }))
const diagnosisMax = computed(() => Math.max(1, ...diagnosisTrend.value.map((item) => item.total)))
const vibrationMax = computed(() => Math.max(1, ...vibrationTrend.value.map((item) => item.total)))
const diagnosisRankingMax = computed(() => Math.max(1, ...diagnosisRanking.value.map((item) => item.total)))
const vibrationRankingMax = computed(() => Math.max(1, ...vibrationRanking.value.map((item) => item.total)))
function percent(value, max) { return Math.round(Number(value || 0) / max * 100) }
function barStyle(value, max) { return { height: `${Math.max(value ? 3 : 0, Number(value || 0) / max * 100)}%` } }

async function loadStatistics() {
  loading.value = true
  try {
    const params = { startDate: route.query.startDate, endDate: route.query.endDate }
    const [equipment, options, diagnosis, vibration] = await Promise.all([
      fetchEquipment(),
      fetchVibrationAlarmOptions(),
      fetchIntelligentDiagnosisStatistics(params),
      fetchVibrationAlarmStatistics(params),
    ])
    overview.equipmentCount = equipment.length
    overview.sensorCount = options.sensors?.length || 0
    const diagnosisByCode = new Map((diagnosis.equipmentRanking || []).map((item) => [item.equipmentCode, item]))
    const vibrationByCode = new Map((vibration.equipmentRanking || []).map((item) => [item.equipmentCode, item]))
    diagnosisTrend.value = (diagnosis.dailyTrend || []).map((item) => ({ ...item, total: Number(item.innerRace || 0) + Number(item.outerRace || 0) }))
    vibrationTrend.value = (vibration.dailyTrend || []).map((item) => ({ ...item, total: Number(item.warning || 0) + Number(item.alarm || 0) + Number(item.danger || 0) }))
    diagnosisRanking.value = (diagnosis.equipmentRanking || []).map((item) => ({ ...item, total: Number(item.innerRace || 0) + Number(item.outerRace || 0) })).sort((a, b) => b.total - a.total)
    vibrationRanking.value = (vibration.equipmentRanking || []).map((item) => ({ ...item, total: Number(item.warning || 0) + Number(item.alarm || 0) + Number(item.danger || 0) })).sort((a, b) => b.total - a.total)
    statisticsRows.value = equipment.map((item, index) => {
      const diagnosisItem = diagnosisByCode.get(item.equipCode) || {}
      const vibrationItem = vibrationByCode.get(item.equipCode) || {}
      const innerRace = Number(diagnosisItem.innerRace || 0)
      const outerRace = Number(diagnosisItem.outerRace || 0)
      const vibrationWarning = Number(vibrationItem.warning || 0)
      const vibrationAlarm = Number(vibrationItem.alarm || 0)
      const vibrationDanger = Number(vibrationItem.danger || 0)
      const diagnosisTotal = innerRace + outerRace
      return { index: index + 1, name: item.equipCode || item.equipName, innerRace, outerRace, diagnosisTotal, vibrationWarning, vibrationAlarm, vibrationDanger, vibrationTotal: vibrationWarning + vibrationAlarm + vibrationDanger }
    })
  } catch (error) {
    console.error('读取统计管理数据失败', error)
    statisticsRows.value = []
    diagnosisTrend.value = []; vibrationTrend.value = []; diagnosisRanking.value = []; vibrationRanking.value = []
  } finally { loading.value = false }
}

watch(() => [route.query.startDate, route.query.endDate], loadStatistics, { immediate: true })

function exportCsv() {
  const lines = [
    ['序号', '设备名称', '智能诊断-轴承内圈故障(次)', '智能诊断-轴承外圈故障(次)', '智能诊断-总数(次)', '振动预警-预警(次)', '振动预警-报警(次)', '振动预警-危险(次)', '振动预警-总数(次)'],
    ...statisticsRows.value.map((row) => [row.index, row.name, row.innerRace, row.outerRace, row.diagnosisTotal, row.vibrationWarning, row.vibrationAlarm, row.vibrationDanger, row.vibrationTotal]),
  ]
  const blob = new Blob([`\uFEFF${lines.map((row) => row.join(',')).join('\n')}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = '健康监测统计.csv'; link.click()
  URL.revokeObjectURL(url)
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[character]))
}

function exportWord() {
  const startDate = route.query.startDate || '--'
  const endDate = route.query.endDate || '--'
  const rows = statisticsRows.value.map((row) => `<tr><td>${row.index}</td><td>${escapeHtml(row.name)}</td><td>${row.innerRace}</td><td>${row.outerRace}</td><td>${row.diagnosisTotal}</td><td>${row.vibrationWarning}</td><td>${row.vibrationAlarm}</td><td>${row.vibrationDanger}</td><td>${row.vibrationTotal}</td></tr>`).join('')
  const totalRow = `<tr class="total"><td colspan="2">合计</td><td>${totals.value.innerRace}</td><td>${totals.value.outerRace}</td><td>${totals.value.diagnosisTotal}</td><td>${totals.value.vibrationWarning}</td><td>${totals.value.vibrationAlarm}</td><td>${totals.value.vibrationDanger}</td><td>${totals.value.vibrationTotal}</td></tr>`
  const documentHtml = `<!DOCTYPE html><html><head><meta charset="utf-8"><style>body{font-family:SimSun,'Microsoft YaHei',sans-serif;color:#24384d;}h1{text-align:center;font-size:20pt;}p{font-size:10.5pt;}table{width:100%;border-collapse:collapse;font-size:9.5pt;}th,td{padding:6px;border:1px solid #b9c7d5;text-align:center;}th{background:#eaf2fb;}td:nth-child(2){text-align:left;}.total td{font-weight:bold;background:#f4f7fa;}</style></head><body><h1>健康监测统计报表</h1><p>统计时间：${escapeHtml(startDate)} 至 ${escapeHtml(endDate)}</p><p>设备数量：${overview.equipmentCount} 台；传感器数量：${overview.sensorCount} 支</p><table><thead><tr><th rowspan="2">序号</th><th rowspan="2">设备名称</th><th colspan="3">智能诊断</th><th colspan="4">振动预警</th></tr><tr><th>轴承内圈故障(次)</th><th>轴承外圈故障(次)</th><th>总数(次)</th><th>预警(次)</th><th>报警(次)</th><th>危险(次)</th><th>总数(次)</th></tr></thead><tbody>${rows || '<tr><td colspan="9">当前时间范围暂无统计数据</td></tr>'}${statisticsRows.value.length ? totalRow : ''}</tbody></table></body></html>`
  const blob = new Blob(['\ufeff', documentHtml], { type: 'application/msword;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `健康监测统计_${startDate}_${endDate}.doc`
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.statistics-page { min-height: calc(100vh - 68px); padding: 12px; color: #24384d; background: #fff; box-sizing: border-box; }
.section-heading { display: flex; align-items: center; min-height: 25px; margin: 0 0 12px; }.section-heading h2, .health-section > h2 { margin: 0; color: #172f47; font-size: 14px; font-weight: 600; }
.export-actions { display: flex; gap: 5px; margin-left: 10px; }.export-actions button { height: 23px; padding: 0 9px; color: #fff; font-size: 12px; cursor: pointer; background: #4095e5; border: 0; border-radius: 2px; }.export-actions button:hover { background: #2f80ce; }
.overview-cards { display: grid; grid-template-columns: repeat(2, 1fr); min-height: 126px; background: #f3f8ff; }.overview-card { display: flex; align-items: center; justify-content: center; gap: 14px; }.overview-card + .overview-card { border-left: 2px solid #dfecfb; }.overview-icon { display: grid; width: 50px; height: 50px; color: #7eaeea; background: #e4f1ff; border-radius: 50%; place-items: center; }.overview-icon :deep(svg) { width: 30px; height: 30px; }.overview-card p { margin: 0 0 12px; color: #172f47; font-size: 14px; }.overview-card strong { color: #001a33; font-size: 25px; font-weight: 500; }
.health-section { margin-top: 16px; }.health-section > h2 { margin-bottom: 16px; }.statistics-table-wrap { max-height: calc(100vh - 310px); overflow: auto; }.statistics-table { width: 100%; min-width: 900px; border-collapse: collapse; table-layout: fixed; }.statistics-table th, .statistics-table td { height: 33px; color: #374b61; font-size: 12px; text-align: center; border: 1px solid #e2e8f0; }.statistics-table th { position: sticky; top: 0; z-index: 1; color: #718198; font-weight: 500; background: #f5f8fc; }.statistics-table th:first-child { width: 48px; }.statistics-table th:nth-child(2) { width: 140px; }.statistics-table td { background: #fff; }.statistics-table tbody tr:nth-child(even) td { background: #fbfcfe; }.statistics-table tfoot td { position: sticky; bottom: 0; z-index: 1; height: 34px; color: #374b61; font-weight: 500; background: #f5f8fc; }.device-name { padding: 0 12px; color: #2c526f; text-align: left; }.empty-data { height: 72px !important; color: #91a0ae !important; }.statistics-table i { display: inline-block; width: 12px; height: 12px; margin-left: 5px; vertical-align: -2px; border-radius: 50%; }.statistics-table i.yellow { background: #f3d400; }.statistics-table i.orange { background: #ff9800; }.statistics-table i.red { background: #d53535; }.statistics-table i.inner-fault { background: #4599ed; }.statistics-table i.outer-fault { background: #8b6edc; }.statistics-table i.sort-mark { position: relative; width: 0; height: 0; margin-left: 7px; border-right: 4px solid transparent; border-bottom: 5px solid #9eabb9; border-left: 4px solid transparent; border-radius: 0; }.statistics-table i.sort-mark::after { position: absolute; top: 7px; left: -4px; content: ''; border-top: 5px solid #9eabb9; border-right: 4px solid transparent; border-left: 4px solid transparent; }
.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 14px; align-items: start; }.chart-sidebar { display: grid; gap: 10px; min-width: 0; }.chart-card { min-height: 132px; overflow: hidden; background: #fff; border-bottom: 1px solid #e2e8f0; }.chart-card h2 { margin: 0 0 8px; padding-left: 8px; color: #3176b9; font-size: 14px; line-height: 15px; border-left: 5px solid #367fc4; }.trend-chart { display: flex; align-items: end; gap: 3px; height: 92px; padding: 4px 8px 0; border-bottom: 1px solid #ccd8e5; background: repeating-linear-gradient(to top, transparent 0, transparent 22px, #eef3f8 23px); }.trend-bar { flex: 1; min-width: 2px; background: #4599ed; border-radius: 1px 1px 0 0; }.warning-chart .trend-bar { background: #f3d400; }.ranking-chart { display: grid; gap: 5px; padding: 1px 3px; }.ranking-row { display: grid; grid-template-columns: 135px 1fr 24px; align-items: center; gap: 4px; color: #40576d; font-size: 11px; }.ranking-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.ranking-row i { height: 12px; background: #edf1f5; }.ranking-row b { display: block; height: 100%; background: #f3d400; }.ranking-row em { color: #52677c; font-style: normal; text-align: right; }.ranking-row b:not(.warning-fill) { background: #4599ed; }
.content-grid { height: calc(100vh - 95px); }.chart-sidebar { grid-template-rows: repeat(4, minmax(0, 1fr)); height: 100%; max-height: none; overflow: hidden; }.chart-card { display: grid; grid-template-rows: 30px minmax(0, 1fr); min-height: 0; background: #f7f7f7; border-radius: 3px; }.chart-card h2 { margin-bottom: 0; background: #fff; }.statistics-trend-chart, .statistics-equipment-chart { height: 100%; min-height: 0; }
@media (max-width: 700px) { .content-grid { grid-template-columns: 1fr; height: auto; }.chart-sidebar { grid-template-columns: repeat(2, minmax(220px, 1fr)); height: auto; max-height: none; overflow: visible; }.statistics-table-wrap { max-height: 420px; } }
@media (max-width: 650px) { .chart-sidebar { grid-template-columns: 1fr; } }
@media (max-width: 650px) { .overview-cards { grid-template-columns: 1fr; }.overview-card { min-height: 108px; }.overview-card + .overview-card { border-top: 2px solid #dfecfb; border-left: 0; } }
</style>
