<template>
  <header class="diagnosis-toolbar">
    <span class="title">智能诊断</span>
    <select v-model="filters.equipmentId"><option value="">全部设备</option><option v-for="item in options.equipment" :key="item.id" :value="String(item.id)">{{ item.code }} / {{ item.name }}</option></select>
    <select v-model="filters.sensorId"><option value="">全部传感器</option><option v-for="item in sensors" :key="item.id" :value="String(item.id)">{{ item.code }} / {{ item.mountPosition || item.name }}</option></select>
    <span class="time-label">时间</span><input v-model="filters.startDate" type="date" /><span class="separator">至</span><input v-model="filters.endDate" type="date" />
    <div class="diagnosis-level-legend" aria-label="诊断故障类别">
      <span v-for="level in diagnosisLevels" :key="level.name" :title="level.name" :style="{ backgroundColor: level.color }"></span>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchVibrationAlarmOptions } from '@/api/equipment'

const formatDate = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const today = new Date()
const weekAgo = new Date(today)
weekAgo.setDate(today.getDate() - 6)
const route = useRoute()
const router = useRouter()
const options = reactive({ equipment: [], sensors: [] })
const filters = reactive({ equipmentId: '', sensorId: '', startDate: formatDate(weekAgo), endDate: formatDate(today) })
const diagnosisLevels = [
  { name: '轴承内圈故障', color: '#4599ed' },
  { name: '轴承外圈故障', color: '#8b6edc' },
]
const sensors = computed(() => !filters.equipmentId ? options.sensors : options.sensors.filter((item) => String(item.equipmentId) === filters.equipmentId))

function sync() {
  filters.equipmentId = route.query.equipmentId || ''
  filters.sensorId = route.query.sensorId || ''
  filters.startDate = route.query.startDate || formatDate(weekAgo)
  filters.endDate = route.query.endDate || formatDate(today)
}
function update() {
  const query = { startDate: filters.startDate, endDate: filters.endDate }
  if (filters.equipmentId) query.equipmentId = filters.equipmentId
  if (filters.sensorId) query.sensorId = filters.sensorId
  router.replace({ query })
}

watch(() => route.query, sync, { immediate: true })
watch(() => [filters.equipmentId, filters.sensorId, filters.startDate, filters.endDate], update)
watch(() => filters.equipmentId, () => { if (!sensors.value.some((item) => String(item.id) === filters.sensorId)) filters.sensorId = '' })
onMounted(async () => { try { Object.assign(options, await fetchVibrationAlarmOptions()) } catch (error) { console.error('读取诊断筛选项失败', error) } })
</script>

<style scoped>
.diagnosis-toolbar { display:flex; align-items:center; gap:12px; flex:1; min-width:0; }.title { margin-right:10px; color:#253548; font-size:18px; font-weight:600; white-space:nowrap; } select,input { height:34px; padding:0 10px; color:#526273; font:inherit; font-size:14px; background:#fff; border:1px solid #ccd7e1; border-radius:3px; box-sizing:border-box; } select { width:180px; } input { width:142px; }.time-label,.separator { color:#526273; font-size:14px; white-space:nowrap; }.diagnosis-level-legend { display:flex; margin-left:auto; overflow:hidden; border-radius:1px; }.diagnosis-level-legend span { width:32px; height:32px; }.diagnosis-level-legend span + span { border-left:1px solid #ffffff66; }
</style>
