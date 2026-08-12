import { createRouter, createWebHashHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import HomeView from '@/views/HomeView.vue'
import DeviceStatusView from '@/views/DeviceStatusView.vue'
import VibrationAlarmView from '@/views/VibrationAlarmView.vue'
import IntelligentDiagnosisView from '@/views/IntelligentDiagnosisView.vue'
import StatisticsView from '@/views/StatisticsView.vue'
import DeviceVibrationView from '@/views/DeviceVibrationView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: 'home', component: HomeView },
        { path: 'device-status', component: DeviceStatusView },
        { path: 'vibration-alarms', component: VibrationAlarmView },
        { path: 'intelligent-diagnosis', component: IntelligentDiagnosisView },
        { path: 'statistics', component: StatisticsView },
        { path: 'device-vibration', component: DeviceVibrationView },
      ],
    },
  ],
})

export default router
