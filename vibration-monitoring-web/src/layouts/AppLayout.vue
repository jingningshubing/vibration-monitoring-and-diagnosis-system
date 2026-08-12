<template>
  <section class="app-layout">
    <aside class="sidebar">
      <div class="brand">VMS</div>
      <nav>
        <router-link v-for="item in navigationItems" :key="item.path" :to="item.path">
          <i class="nav-icon"><component :is="icons[item.icon]" /></i>
          {{ item.name }}
        </router-link>
      </nav>
    </aside>

    <main class="page-content">
      <header class="top-bar">
        <VibrationAlarmToolbar v-if="route.path === '/vibration-alarms'" />
        <IntelligentDiagnosisToolbar v-else-if="route.path === '/intelligent-diagnosis'" />
        <StatisticsToolbar v-else-if="route.path === '/statistics'" />
        <span v-else>{{ pageTitle }}</span>
        <StatusLevelLegend v-if="route.path === '/device-status' || route.path === '/vibration-alarms'" />
      </header>
      <router-view />
    </main>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import StatusLevelLegend from '@/components/common/StatusLevelLegend.vue'
import VibrationAlarmToolbar from '@/components/alarm/VibrationAlarmToolbar.vue'
import IntelligentDiagnosisToolbar from '@/components/diagnosis/IntelligentDiagnosisToolbar.vue'
import StatisticsToolbar from '@/components/statistics/StatisticsToolbar.vue'
import {
  Cpu,
  DataAnalysis,
  HomeFilled,
  Monitor,
  WarningFilled,
} from '@element-plus/icons-vue'
import { navigationItems } from '@/config/navigation'

const route = useRoute()
const pageTitle = computed(() => {
  if (route.path === '/home') return '监测概览'
  return navigationItems.find((item) => item.path === route.path)?.name ?? '监测概览'
})

const icons = {
  home: HomeFilled,
  device: Monitor,
  alarm: WarningFilled,
  diagnosis: Cpu,
  statistics: DataAnalysis,
}
</script>

<style scoped>
:global(html), :global(body), :global(#app) { min-width: 0; min-height: 100%; margin: 0; }
.app-layout { min-height: 100vh; color: #34495e; background: #f5f7fa; }
.top-bar { display: flex; align-items: center; min-height: 68px; padding: 0 24px; color: #253548; font-size: 18px; font-weight: 600; background: #fff; border-bottom: 1px solid #dfe5ec; box-sizing: border-box; }.top-bar :deep(.status-level-legend) { margin-left: auto; }
.sidebar { position: fixed; z-index: 4; top: 0; bottom: 0; width: 64px; overflow: hidden; background: #31475f; box-shadow: 2px 0 8px #0002; transition: width .2s ease; }
.sidebar:hover { width: 220px; }.brand { display: grid; height: 70px; place-items: center; color: #f04747; font-size: 0; font-weight: 800; letter-spacing: 1px; background: #263b52; }.brand::after { content: 'V'; font-size: 26px; }.sidebar:hover .brand { font-size: 29px; }.sidebar:hover .brand::after { display: none; }
nav { padding-top: 10px; } nav a { display: flex; align-items: center; gap: 17px; height: 56px; padding: 0 22px; color: #dbe7f2; font-size: 0; text-decoration: none; white-space: nowrap; } nav a:hover { background: #3c566f; } nav a.router-link-active { padding-left: 18px; color: #fff; background: #1d6aa2; border-left: 4px solid #56b8ed; }.sidebar:hover nav a { font-size: 16px; }.nav-icon { display: grid; width: 20px; flex: none; color: #a9c6db; place-items: center; }.nav-icon :deep(svg) { width: 20px; height: 20px; }
.page-content { min-height: 100vh; padding-left: 64px; box-sizing: border-box; } @media (max-width: 700px) { .sidebar { width: 56px; }.sidebar:hover { width: 190px; }.page-content { padding-left: 56px; } }
</style>
