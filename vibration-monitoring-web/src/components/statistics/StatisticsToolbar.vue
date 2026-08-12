<template>
  <div class="statistics-toolbar" aria-label="统计时间筛选">
    <span class="title">统计管理</span>
    <div class="period-tabs" role="group" aria-label="统计周期">
      <button v-for="item in periods" :key="item.value" type="button" :class="{ active: period === item.value }" @click="selectPeriod(item.value)">
        {{ item.label }}
      </button>
    </div>
    <span class="time-label">时间</span>
    <input v-model="startDate" type="date" aria-label="开始日期" />
    <span class="range-separator">至</span>
    <input v-model="endDate" type="date" aria-label="结束日期" />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const periods = [
  { label: '日', value: 'day' },
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
  { label: '季', value: 'quarter' },
]

const period = ref('day')
const startDate = ref('2026-07-07')
const endDate = ref('2026-08-05')
const route = useRoute()
const router = useRouter()

function selectPeriod(value) {
  period.value = value
  const end = new Date(`${endDate.value}T00:00:00`)
  if (Number.isNaN(end.getTime())) return
  const start = new Date(end)
  if (value === 'day') {
    // same day
  } else if (value === 'week') {
    start.setDate(end.getDate() - 6)
  } else if (value === 'month') {
    start.setDate(1)
  } else if (value === 'quarter') {
    start.setMonth(Math.floor(end.getMonth() / 3) * 3, 1)
  }
  const nextStart = formatDate(start)
  if (nextStart !== startDate.value) startDate.value = nextStart
  updateQuery(nextStart, endDate.value)
}

function formatDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function updateQuery(start, end) {
  router.replace({ query: { ...route.query, startDate: start, endDate: end } })
}

function syncFromRoute() {
  startDate.value = route.query.startDate || '2026-07-07'
  endDate.value = route.query.endDate || '2026-08-05'
}

watch(() => route.query, syncFromRoute, { immediate: true })
watch([startDate, endDate], () => {
  if (!startDate.value || !endDate.value) return
  updateQuery(startDate.value, endDate.value)
})

onMounted(() => {
  if (!route.query.startDate || !route.query.endDate) {
    router.replace({ query: { ...route.query, startDate: startDate.value, endDate: endDate.value } })
  }
})
</script>

<style scoped>
.statistics-toolbar { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; }
.title { margin-right: 10px; color: #253548; font-size: 18px; font-weight: 600; white-space: nowrap; }
.period-tabs { display: flex; height: 34px; overflow: hidden; border: 1px solid #ccd7e1; border-radius: 3px; }
.period-tabs button { width: 37px; padding: 0; color: #526273; font-size: 14px; line-height: 32px; cursor: pointer; background: #fff; border: 0; border-right: 1px solid #ccd7e1; }
.period-tabs button:last-child { border-right: 0; }.period-tabs button:hover { color: #287aca; background: #f3f8fd; }.period-tabs button.active { color: #fff; font-weight: 600; background: #4095e5; }
.statistics-toolbar input { width: 142px; height: 34px; padding: 0 10px; color: #526273; font: inherit; font-size: 14px; background: #fff; border: 1px solid #ccd7e1; border-radius: 3px; box-sizing: border-box; }
.time-label, .range-separator { color: #526273; font-size: 14px; white-space: nowrap; }
@media (max-width: 700px) { .statistics-toolbar { gap: 7px; }.title { margin-right: 2px; }.statistics-toolbar input { width: 126px; }.period-tabs button { width: 31px; } }
</style>
