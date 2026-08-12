<template>
  <section class="device-status-layout">
    <section class="device-status-page">
      <article
        v-for="equipment in equipmentCards"
        :key="equipment.id"
        class="device-card"
        @click="openEquipmentTrend(equipment)"
        @mouseenter="showSensorPopover(equipment, $event)"
        @mouseleave="schedulePopoverClose"
      >
        <h2>{{ equipment.equipCode }}</h2>
        <div class="device-card-content">
          <p>
            <i :style="{ backgroundColor: statusMeta(equipment.status).color }"></i>
            总振值：{{ formatTotalVibration(equipment.totalVibration) }}
          </p>
          <p>
            <i :style="{ backgroundColor: diagnosisMeta(equipment.diagnosis?.diagnosisText).color }"></i>
            智能诊断：{{ equipment.diagnosis?.diagnosisText || '暂无诊断结果' }}
          </p>
        </div>
      </article>
    </section>

    <aside class="statistics-sidebar">
      <article class="statistics-card"><h3>设备最近状态</h3><div class="vibration-statistics-content"><StatusDistributionDonut title="设备最近状态分布" :total="equipmentCards.length" :segments="latestStatusSegments" /></div></article>
      <article class="statistics-card">
        <h3>总振值状态</h3>
        <div class="vibration-statistics-content">
          <div class="status-chart-wrap">
            <div class="status-donut">
              <svg viewBox="0 0 150 150" aria-label="总振值状态分布环图">
                <circle class="donut-background" cx="75" cy="75" r="46" />
                <circle
                  v-for="segment in vibrationDonutSegments"
                  :key="segment.code"
                  class="donut-segment"
                  cx="75"
                  cy="75"
                  r="46"
                  :stroke="segment.color"
                  :stroke-dasharray="segment.dasharray"
                  :stroke-dashoffset="segment.dashoffset"
                  transform="rotate(-90 75 75)"
                />
                <circle class="donut-hole" cx="75" cy="75" r="30" />
                <text
                  v-for="segment in vibrationDonutSegments"
                  v-show="segment.count > 0"
                  :key="`${segment.code}-label`"
                  class="donut-segment-label"
                  :x="segment.labelX"
                  :y="segment.labelY"
                >
                  {{ segment.count }}
                </text>
                <text class="donut-total-label" x="75" y="82">{{ vibrationStatusStatistics.total }}</text>
              </svg>
            </div>
            <div class="status-tooltip">
              <p>共 {{ vibrationStatusStatistics.total }} 台</p>
              <p v-for="item in vibrationStatusLegend" :key="item.code">
                <i :style="{ backgroundColor: item.color }"></i>
                {{ item.name }}：{{ item.count }} 台（{{ statusPercent(item.count) }}%）
              </p>
            </div>
          </div>
        </div>
      </article>
      <article class="statistics-card"><h3>智能诊断状态</h3><div class="vibration-statistics-content"><StatusDistributionDonut title="智能诊断状态分布" :total="equipmentCards.length" :segments="diagnosisStatusSegments" /></div></article>
    </aside>

    <section
      v-if="activeEquipment"
      class="sensor-popover"
      :style="popoverStyle"
      @mouseenter="cancelPopoverClose"
      @mouseleave="schedulePopoverClose"
    >
      <h3>{{ activeEquipment.equipCode }}</h3>
      <div v-if="activeSensors.length" class="sensor-list">
        <article v-for="sensor in activeSensors" :key="sensor.sensorId" class="sensor-detail">
          <h4>{{ sensor.sensorName }}（{{ sensor.sensorCode }}）</h4>
          <p><i :style="{ backgroundColor: statusMeta(sensor.status).color }"></i>X轴：{{ formatAxis(sensor.xVelocityRms) }}</p>
          <p><i :style="{ backgroundColor: statusMeta(sensor.status).color }"></i>Y轴：{{ formatAxis(sensor.yVelocityRms) }}</p>
          <p><i :style="{ backgroundColor: statusMeta(sensor.status).color }"></i>Z轴：{{ formatAxis(sensor.zVelocityRms) }}</p>
        </article>
      </div>
      <p v-else class="empty-detail">暂无最新三轴振动数据</p>
    </section>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchEquipmentStatus,
  fetchEquipmentVibrationStatusStatistics,
  fetchLatestDiagnosisByEquipment,
  fetchSensorVibrations,
} from '@/api/equipment'
import { statusLevels } from '@/config/statusLevels'
import StatusDistributionDonut from '@/components/status/StatusDistributionDonut.vue'

const equipmentCards = ref([])
const router = useRouter()
const sensorCache = ref({})
const activeEquipment = ref(null)
const activeSensors = ref([])
const popoverStyle = ref({})
const vibrationStatusStatistics = ref({
  normal: 0,
  warning: 0,
  alarm: 0,
  danger: 0,
  offline: 0,
  total: 0,
})
let closeTimer
function openEquipmentTrend(equipment) { router.push({ path: '/device-vibration', query: { equipmentId: String(equipment.id) } }) }

const vibrationStatusLegend = computed(() => [
  { ...statusMeta('NORMAL'), code: 'NORMAL', count: vibrationStatusStatistics.value.normal },
  { ...statusMeta('WARNING'), code: 'WARNING', count: vibrationStatusStatistics.value.warning },
  { ...statusMeta('ALARM'), code: 'ALARM', count: vibrationStatusStatistics.value.alarm },
  { ...statusMeta('DANGER'), code: 'DANGER', count: vibrationStatusStatistics.value.danger },
  { name: '离线', color: '#8a8a8a', code: 'OFFLINE', count: vibrationStatusStatistics.value.offline },
])

const vibrationDonutSegments = computed(() => {
  const total = Number(vibrationStatusStatistics.value.total) || 0
  const radius = 46
  const circumference = 2 * Math.PI * radius
  let offset = 0

  return vibrationStatusLegend.value.map((item) => {
    const count = Number(item.count) || 0
    const length = total === 0 ? 0 : (count / total) * circumference
    const centerAngle = -90 + ((offset + length / 2) / circumference) * 360
    const radians = (centerAngle * Math.PI) / 180
    const segment = {
      ...item,
      dasharray: `${length} ${circumference}`,
      dashoffset: -offset,
      labelX: 75 + radius * Math.cos(radians),
      labelY: 75 + radius * Math.sin(radians) + 4,
    }
    offset += length
    return segment
  })
})

const latestStatusLegend = computed(() => {
  const levels = [...statusLevels, { code: 'OFFLINE', name: '离线', color: '#8a8a8a' }]
  return levels.map((level) => ({ ...level, count: equipmentCards.value.filter((item) => (item.status || 'OFFLINE') === level.code).length }))
})

const latestStatusSegments = computed(() => buildDonutSegments(latestStatusLegend.value, equipmentCards.value.length))

const diagnosisStatusLegend = computed(() => {
  const categories = [
    { code: 'HEALTHY', name: '健康', color: '#6cba4e', text: '健康' },
    { code: 'INNER_RACE', name: '轴承内圈故障', color: '#4599ed', text: '轴承内圈故障' },
    { code: 'OUTER_RACE', name: '轴承外圈故障', color: '#8b6edc', text: '轴承外圈故障' },
    { code: 'UNCLEAR', name: '数据特征不明确', color: '#1f2937', text: '当前数据特征不明确' },
    { code: 'PENDING', name: '暂无诊断', color: '#8a8a8a', text: null },
  ]
  return categories.map((category) => ({ ...category, count: equipmentCards.value.filter((item) => (item.diagnosis?.diagnosisText || null) === category.text).length }))
})

const diagnosisStatusSegments = computed(() => buildDonutSegments(diagnosisStatusLegend.value, equipmentCards.value.length))

/** 将分类数量换算为 SVG 圆环分段的位置、弧长及悬浮占比。 */
function buildDonutSegments(items, total) {
  const radius = 46
  const circumference = 2 * Math.PI * radius
  let offset = 0
  return items.map((item) => {
    const count = Number(item.count) || 0
    const length = total === 0 ? 0 : (count / total) * circumference
    const radians = ((-90 + ((offset + length / 2) / circumference) * 360) * Math.PI) / 180
    const segment = { ...item, percent: total === 0 ? 0 : Math.round((count / total) * 100), dasharray: `${length} ${circumference}`, dashoffset: -offset, labelX: 75 + radius * Math.cos(radians), labelY: 75 + radius * Math.sin(radians) + 4 }
    offset += length
    return segment
  })
}

/**
 * 读取设备状态卡片数据。
 *
 * <p>输入：无。输出：设备状态接口数据写入 equipmentCards。</p>
 */
async function loadEquipment() {
  try {
    const [equipment, statistics, latestDiagnoses] = await Promise.all([
      fetchEquipmentStatus(),
      fetchEquipmentVibrationStatusStatistics(),
      fetchLatestDiagnosisByEquipment(),
    ])
    const diagnosisByEquipment = Object.fromEntries(latestDiagnoses.map((item) => [item.equipmentId, item]))
    equipmentCards.value = equipment.map((item) => ({ ...item, diagnosis: diagnosisByEquipment[item.id] }))
    vibrationStatusStatistics.value = statistics
  } catch (error) {
    console.error('读取设备状态失败：', error)
    equipmentCards.value = []
  }
}

/**
 * 显示设备传感器三轴详情，并按需加载该设备的数据。
 *
 * <p>输入：设备对象、触发悬浮的鼠标事件。输出：更新悬浮详情的位置和内容。</p>
 */
async function showSensorPopover(equipment, event) {
  cancelPopoverClose()
  activeEquipment.value = equipment
  positionPopover(event.currentTarget.getBoundingClientRect())

  if (!sensorCache.value[equipment.id]) {
    try {
      sensorCache.value[equipment.id] = await fetchSensorVibrations(equipment.id)
    } catch (error) {
      console.error('读取传感器三轴数据失败：', error)
      sensorCache.value[equipment.id] = []
    }
  }

  if (activeEquipment.value?.id === equipment.id) {
    activeSensors.value = sensorCache.value[equipment.id]
  }
}

/**
 * 计算悬浮详情位置，避免详情框超出浏览器右侧。
 *
 * <p>输入：设备卡片的屏幕坐标。输出：更新固定定位的弹层样式。</p>
 */
function positionPopover(cardRect) {
  popoverStyle.value = {
    left: `${Math.max(16, cardRect.left)}px`,
    top: `${cardRect.bottom + 10}px`,
  }
}

/**
 * 延迟关闭弹层，使鼠标可以从卡片移动到详情框。
 */
function schedulePopoverClose() {
  closeTimer = window.setTimeout(() => {
    activeEquipment.value = null
    activeSensors.value = []
  }, 150)
}

/**
 * 取消待执行的关闭操作。
 */
function cancelPopoverClose() {
  window.clearTimeout(closeTimer)
}

/**
 * 将状态编码映射为统一的中文名称和颜色。
 */
function statusMeta(status) {
  return statusLevels.find((item) => item.code === status)
    ?? { name: '离线', color: '#8a8a8a' }
}

/** 将诊断结论映射为智能诊断页面一致的展示颜色；仅作辅助说明，不影响预警状态。 */
function diagnosisMeta(text) {
  return [
    { text: '健康', color: '#6cba4e' },
    { text: '轴承内圈故障', color: '#4599ed' },
    { text: '轴承外圈故障', color: '#8b6edc' },
    { text: '当前数据特征不明确', color: '#1f2937' },
  ].find((item) => item.text === text) ?? { color: '#8a8a8a' }
}

/**
 * 格式化设备总振值。
 */
function formatTotalVibration(value) {
  return value === null || value === undefined
    ? '--'
    : `${Number(value).toFixed(2)} mm/s`
}

/**
 * 格式化三轴速度有效值。
 */
function formatAxis(value) {
  return value === null || value === undefined
    ? '--'
    : `${Number(value).toFixed(3)} mm/s RMS`
}

/**
 * 计算状态数量在全部设备中的占比。
 */
function statusPercent(count) {
  const total = Number(vibrationStatusStatistics.value.total) || 0
  return total === 0 ? 0 : Math.round((Number(count) / total) * 100)
}

onMounted(loadEquipment)
onBeforeUnmount(cancelPopoverClose)
</script>

<style scoped>
.device-status-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  min-height: calc(100vh - 68px);
  background: #f4f6f8;
}

.device-status-page {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  align-content: start;
  gap: 22px;
  padding: 18px;
  box-sizing: border-box;
}

.statistics-sidebar {
  display: grid;
  grid-template-rows: repeat(3, minmax(190px, 1fr));
  gap: 8px;
  padding: 8px;
  border-left: 1px solid #dce3eb;
  background: #fff;
}

.statistics-card {
  display: grid;
  position: relative;
  z-index: 1;
  grid-template-rows: 50px 1fr;
  min-height: 0;
  overflow: visible;
  background: #f7f7f7;
}

.statistics-card:has(.status-chart-wrap:hover) {
  z-index: 5;
}

.statistics-card h3 {
  display: flex;
  align-items: center;
  margin: 0;
  padding: 0 16px;
  color: #3176b9;
  font-size: 17px;
  font-weight: 700;
  background: #fff;
}

.statistics-card h3::before {
  width: 5px;
  height: 20px;
  margin-right: 11px;
  content: '';
  background: #367fc4;
}

.statistics-card > div {
  min-height: 0;
  background: #f7f7f7;
}

.vibration-statistics-content {
  display: grid;
  place-items: center;
  padding: 16px;
  box-sizing: border-box;
}

.status-chart-wrap {
  position: relative;
}

.status-donut {
  position: relative;
  width: 168px;
  height: 168px;
}

.status-donut svg {
  width: 100%;
  height: 100%;
}

.donut-background,
.donut-segment {
  fill: none;
  stroke-width: 22px;
}

.donut-background {
  stroke: #dfe5ec;
}

.donut-segment {
  stroke-linecap: butt;
}

.donut-hole {
  fill: #f7f7f7;
}

.donut-segment-label {
  fill: #fff;
  font-size: 14px;
  font-weight: 700;
  text-anchor: middle;
}

.donut-total-label {
  fill: #4f6174;
  font-size: 22px;
  font-weight: 700;
  text-anchor: middle;
}

.status-tooltip {
  position: absolute;
  z-index: 2;
  right: calc(100% + 12px);
  bottom: 8px;
  display: none;
  min-width: 160px;
  padding: 10px 12px;
  color: #5f6872;
  background: #fff;
  border: 1px solid #7bbe58;
  border-radius: 4px;
  box-shadow: 0 2px 8px #0002;
}

.status-chart-wrap:hover .status-tooltip {
  display: block;
}

.status-tooltip p {
  display: flex;
  align-items: center;
  margin: 4px 0;
  font-size: 13px;
  white-space: nowrap;
}

.status-tooltip p:first-child {
  margin-top: 0;
  font-weight: 700;
}

.status-tooltip p:last-child {
  margin-bottom: 0;
}

.status-tooltip i {
  width: 10px;
  height: 10px;
  margin-right: 7px;
}

.device-card {
  min-height: 170px;
  overflow: hidden;
  background: #fff;
  border-radius: 3px;
  box-shadow: 1px 3px 7px #0004;
  cursor: default;
}

.device-card h2 {
  display: grid;
  height: 44px;
  margin: 0;
  color: #3c4c5e;
  font-size: 17px;
  font-weight: 700;
  place-items: center;
  background: #f0f0f0;
}

.device-card-content {
  display: grid;
  align-content: start;
  gap: 15px;
  min-height: 126px;
  padding: 18px 14px;
  box-sizing: border-box;
}

.device-card-content p,
.sensor-detail p {
  display: flex;
  align-items: center;
  margin: 0;
}

.device-card-content p {
  color: #34495e;
  font-size: 15px;
}

.device-card-content i,
.sensor-detail i {
  width: 13px;
  height: 13px;
  margin-right: 7px;
  flex: none;
}

.device-card-content .diagnosis-pending {
  background: #8a8a8a;
}

.sensor-popover {
  position: fixed;
  z-index: 20;
  max-width: calc(100vw - 32px);
  overflow: hidden;
  color: #fff;
  background: #2d3137;
  border-radius: 4px;
  box-shadow: 0 4px 14px #0006;
}

.sensor-popover h3 {
  margin: 0;
  padding: 9px 14px;
  font-size: 17px;
  text-align: center;
}

.sensor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 10px 10px;
}

.sensor-detail {
  width: 200px;
  min-height: 110px;
  padding: 10px;
  border: 1px solid #dbe0e6;
  box-sizing: border-box;
}

.sensor-detail h4 {
  margin: 0 0 8px;
  overflow: hidden;
  color: #fff;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sensor-detail p {
  margin: 6px 0;
  color: #fff;
  font-size: 13px;
}

.sensor-detail i {
  width: 10px;
  height: 10px;
}

.empty-detail {
  margin: 0;
  padding: 12px 16px 16px;
  color: #d4d8dd;
  text-align: center;
}

@media (max-width: 1000px) {
  .device-status-layout {
    grid-template-columns: 1fr;
  }

  .statistics-sidebar {
    grid-template-columns: repeat(3, minmax(220px, 1fr));
    grid-template-rows: 230px;
    overflow-x: auto;
    border-top: 1px solid #dce3eb;
    border-left: 0;
  }
}
</style>
