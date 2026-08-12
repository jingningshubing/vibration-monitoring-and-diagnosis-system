<template>
  <header class="alarm-toolbar">
    <span class="title">振动预警</span>
    <select v-model="filters.equipmentId" aria-label="设备">
      <option value="">全部设备</option>
      <option v-for="item in options.equipment" :key="item.id" :value="String(item.id)">{{ item.code }} · {{ item.name }}</option>
    </select>
    <select v-model="filters.sensorId" aria-label="传感器">
      <option value="">全部传感器</option>
      <option v-for="item in filteredSensors" :key="item.id" :value="String(item.id)">{{ item.code }} · {{ item.mountPosition || item.name }}</option>
    </select>
    <select v-model="filters.alarmLevel" aria-label="传感器状态">
      <option value="">全部状态</option>
      <option value="NORMAL">正常</option>
      <option value="WARNING">预警</option>
      <option value="ALARM">报警</option>
      <option value="DANGER">危险</option>
    </select>
    <span class="time-label">时间</span>
    <input v-model="filters.startDate" type="date" aria-label="开始时间" />
    <span class="separator">至</span>
    <input v-model="filters.endDate" type="date" aria-label="结束时间" />
  </header>
</template>

<script setup>
import { computed, onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchVibrationAlarmOptions } from '@/api/equipment'

/** 返回 YYYY-MM-DD 格式日期。 */
function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const today = new Date()
const weekAgo = new Date(today)
weekAgo.setDate(today.getDate() - 6)

/** 振动预警顶部筛选条件，默认全部与最近一周。 */
const route = useRoute()
const router = useRouter()
const options = reactive({ equipment: [], sensors: [] })
const filters = reactive({ equipmentId: '', sensorId: '', alarmLevel: '', startDate: formatDate(weekAgo), endDate: formatDate(today) })
const filteredSensors = computed(() => !filters.equipmentId ? options.sensors : options.sensors.filter((item) => String(item.equipmentId) === filters.equipmentId))

/** 从地址栏恢复筛选值，使刷新、翻页和筛选保持一致。 */
function syncFromRoute() {
  filters.equipmentId = route.query.equipmentId || ''
  filters.sensorId = route.query.sensorId || ''
  filters.alarmLevel = route.query.alarmLevel || ''
  filters.startDate = route.query.startDate || formatDate(weekAgo)
  filters.endDate = route.query.endDate || formatDate(today)
}

/** 写入筛选条件并回到第一页，表格页会根据地址栏查询数据。 */
function updateRoute() {
  const query = { startDate: filters.startDate, endDate: filters.endDate }
  if (filters.equipmentId) query.equipmentId = filters.equipmentId
  if (filters.sensorId) query.sensorId = filters.sensorId
  if (filters.alarmLevel) query.alarmLevel = filters.alarmLevel
  router.replace({ query })
}

watch(() => route.query, syncFromRoute, { immediate: true })
watch(() => [filters.equipmentId, filters.sensorId, filters.alarmLevel, filters.startDate, filters.endDate], updateRoute)
watch(() => filters.equipmentId, () => {
  if (!filteredSensors.value.some((item) => String(item.id) === filters.sensorId)) filters.sensorId = ''
})
onMounted(async () => {
  try { Object.assign(options, await fetchVibrationAlarmOptions()) } catch (error) { console.error('读取预警筛选项失败：', error) }
})
</script>

<style scoped>
.alarm-toolbar { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; }
.title { margin-right: 10px; color: #253548; font-size: 18px; font-weight: 600; white-space: nowrap; }
select, input { height: 34px; padding: 0 10px; color: #526273; font: inherit; font-size: 14px; background: #fff; border: 1px solid #ccd7e1; border-radius: 3px; box-sizing: border-box; }
select { width: 180px; }
input { width: 142px; }
.time-label, .separator { color: #526273; font-size: 14px; white-space: nowrap; }
@media (max-width: 1050px) { .alarm-toolbar { overflow-x: auto; padding-bottom: 2px; }.title { position: sticky; left: 0; padding-right: 6px; background: #fff; } }
</style>
