<template>
  <section class="dashboard">
    <header class="system-banner">
      <time>📅 {{ currentTime }}</time>
      <h1>动设备-管线无人值守监测系统</h1>
    </header>

    <section class="dashboard-grid">
      <aside class="left-column">
        <article class="panel overview-panel">
          <h2>系统概览</h2>
          <div class="overview-content">
            <div class="overview-counts">
              <p>
                <span>设备</span><b>{{ equipmentStatus.total }}</b
                ><em>台</em>
              </p>
              <p>
                <span>传感器</span><b>{{ sensorStatus.total }}</b
                ><em>支</em>
              </p>
            </div>
            <div class="online-rate">
              <svg viewBox="0 0 120 120">
                <circle class="rate-base" cx="60" cy="60" r="40" />
                <circle
                  class="rate-value"
                  cx="60"
                  cy="60"
                  r="40"
                  :stroke-dasharray="`${onlineRate * 2.51327} 251.327`"
                  transform="rotate(-90 60 60)"
                />
                <text x="60" y="65">{{ onlineRate.toFixed(1) }}%</text></svg
              ><span>测点在线率</span>
              <div class="online-tooltip">
                <p>测点总数：{{ sensorStatus.total }} 支</p>
                <p>
                  <i class="online-dot"></i>在线：{{
                    sensorStatus.total - sensorStatus.offline
                  }}
                  支
                </p>
                <p>
                  <i class="offline-dot"></i>离线：{{ sensorStatus.offline }} 支
                </p>
              </div>
            </div>
          </div>
        </article>
        <article class="panel ranking-panel">
          <h2>预警设备排序</h2>
          <HomeAlarmEquipmentChart :data="equipmentRanking" />
        </article>
      </aside>

      <section class="center-column">
        <article class="panel map-panel">
          <div class="process-flow-stage">
            <img
              class="process-flow-image"
              src="/process-flow.svg"
              alt="净化厂生产工艺流程图"
            />
            <button
              v-for="sensor in processFlowSensors"
              :key="sensor.sensorId"
              class="process-sensor-marker"
              :class="`is-${sensor.status.toLowerCase()}`"
              :style="{ left: `${sensor.x}%`, top: `${sensor.y}%` }"
              type="button"
              :aria-label="`${sensor.sensorCode}：${statusName(sensor.status)}`"
              @click="openSensorDetail(sensor)"
            >
              <span>{{ sensor.sensorCode }}</span>
              <span class="process-sensor-tooltip">
                <b>{{ sensor.equipmentCode }} / {{ sensor.sensorCode }}</b>
                <em>{{ sensor.mountPosition || sensor.sensorName }}</em>
                <p><i :class="`is-${sensor.status.toLowerCase()}`"></i>{{ statusName(sensor.status) }}</p>
                <p>垂直：{{ vibrationValue(sensor.xVelocityRms) }} mm/s RMS</p>
                <p>水平：{{ vibrationValue(sensor.yVelocityRms) }} mm/s RMS</p>
                <p>轴向：{{ vibrationValue(sensor.zVelocityRms) }} mm/s RMS</p>
              </span>
            </button>
          </div>
        </article>
        <article class="panel diagnosis-panel">
          <h2>最新诊断结论</h2>
          <div class="latest-diagnosis-table">
            <table>
              <thead>
                <tr>
                  <th>时间</th>
                  <th>设备</th>
                  <th>位置</th>
                  <th>诊断结论</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!latestDiagnoses.length">
                  <td colspan="4">暂无诊断记录</td>
                </tr>
                <tr v-for="item in latestDiagnoses" :key="item.id">
                  <td>{{ diagnosisTime(item.diagnosisTime) }}</td>
                  <td>{{ item.equipmentCode || "--" }}</td>
                  <td>{{ item.mountPosition || item.sensorCode || "--" }}</td>
                  <td>
                    <i
                      :style="{
                        backgroundColor: diagnosisColor(item.diagnosisText),
                      }"
                    ></i
                    >{{ item.diagnosisText }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <aside class="right-column">
        <div class="status-panels">
          <article class="panel compact-panel">
            <h2>设备报警状态</h2>
            <div class="home-donut-wrap">
              <svg viewBox="0 0 120 120">
                <circle class="home-donut-base" cx="60" cy="60" r="37" />
                <circle
                  v-for="segment in equipmentSegments"
                  :key="segment.code"
                  class="home-donut-segment"
                  cx="60"
                  cy="60"
                  r="37"
                  :stroke="segment.color"
                  :stroke-dasharray="segment.dasharray"
                  :stroke-dashoffset="segment.dashoffset"
                  transform="rotate(-90 60 60)"
                />
                <text
                  v-for="segment in equipmentSegments.filter(
                    (item) => item.count > 0,
                  )"
                  :key="`${segment.code}-label`"
                  class="home-segment-label"
                  :x="segment.labelX"
                  :y="segment.labelY"
                >
                  {{ segment.count }}
                </text>
                <text class="home-total-label" x="60" y="65">
                  {{ equipmentStatus.total }}
                </text>
              </svg>
              <div class="home-tooltip">
                <p>共 {{ equipmentStatus.total }} 台</p>
                <p v-for="item in equipmentSegments" :key="item.code">
                  <i :style="{ backgroundColor: item.color }"></i
                  >{{ item.name }}：{{ item.count }} 台（{{ item.percent }}%）
                </p>
              </div>
            </div>
          </article>
          <article class="panel compact-panel">
            <h2>测点报警状态</h2>
            <div class="home-donut-wrap">
              <svg viewBox="0 0 120 120">
                <circle class="home-donut-base" cx="60" cy="60" r="37" />
                <circle
                  v-for="segment in sensorSegments"
                  :key="segment.code"
                  class="home-donut-segment"
                  cx="60"
                  cy="60"
                  r="37"
                  :stroke="segment.color"
                  :stroke-dasharray="segment.dasharray"
                  :stroke-dashoffset="segment.dashoffset"
                  transform="rotate(-90 60 60)"
                />
                <text
                  v-for="segment in sensorSegments.filter(
                    (item) => item.count > 0,
                  )"
                  :key="`${segment.code}-label`"
                  class="home-segment-label"
                  :x="segment.labelX"
                  :y="segment.labelY"
                >
                  {{ segment.count }}
                </text>
                <text class="home-total-label" x="60" y="65">
                  {{ sensorStatus.total }}
                </text>
              </svg>
              <div class="home-tooltip">
                <p>共 {{ sensorStatus.total }} 个</p>
                <p v-for="item in sensorSegments" :key="item.code">
                  <i :style="{ backgroundColor: item.color }"></i
                  >{{ item.name }}：{{ item.count }} 个（{{ item.percent }}%）
                </p>
              </div>
            </div>
          </article>
        </div>
        <article class="panel trend-panel">
          <h2>振动预警趋势（次/日）</h2>
          <HomeAlarmTrendChart :data="vibrationTrendData" />
        </article>
        <article class="panel trend-panel">
          <h2>智能诊断预警趋势（次/日）</h2>
          <HomeDiagnosisTrendChart :data="diagnosisTrendData" />
        </article>
      </aside>
    </section>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  fetchEquipment,
  fetchEquipmentVibrationStatusStatistics,
  fetchIntelligentDiagnoses,
  fetchSensorVibrations,
  fetchSensorVibrationStatusStatistics,
  fetchHomeStatistics,
} from "@/api/equipment";
import { statusLevels } from "@/config/statusLevels";
import HomeAlarmEquipmentChart from "@/components/home/HomeAlarmEquipmentChart.vue";
import HomeAlarmTrendChart from "@/components/home/HomeAlarmTrendChart.vue";
import HomeDiagnosisTrendChart from "@/components/home/HomeDiagnosisTrendChart.vue";

const router = useRouter();
const currentDate = ref(new Date());
const currentTime = computed(() => {
  return currentDate.value
    .toLocaleString("zh-CN", { hour12: false })
    .replaceAll("/", "-");
});
const clock = window.setInterval(() => {
  currentDate.value = new Date();
}, 1000);
const equipmentStatus = ref({
  normal: 0,
  warning: 0,
  alarm: 0,
  danger: 0,
  offline: 0,
  total: 0,
});
const sensorStatus = ref({
  normal: 0,
  warning: 0,
  alarm: 0,
  danger: 0,
  offline: 0,
  total: 0,
});
const latestDiagnoses = ref([]);
// Data for home statistics cards
const equipmentRanking = ref([]);
const vibrationTrendData = ref([]);
const diagnosisTrendData = ref([]);
const processSensorLayout = {
  "V-01": { x: 9.5, y: 81.5 },
  "V-02": { x: 12.8, y: 77.5 },
  "V-03": { x: 9.5, y: 41.5 },
  "V-04": { x: 12.8, y: 37.5 },
  "V-05": { x: 20.5, y: 68.5 },
  "V-06": { x: 20.5, y: 29.5 },
  "V-07": { x: 69, y: 26.5 },
  "V-08": { x: 87, y: 67.5 },
  "V-09": { x: 89, y: 17.5 },
  "V-10": { x: 54.5, y: 48 },
};
const processFlowSensors = ref([]);

const homeLevels = [
  ...statusLevels,
  { code: "OFFLINE", name: "离线", color: "#8a8a8a" },
];
function donutSegments(source) {
  const total = Number(source.value.total) || 0;
  const circumference = 2 * Math.PI * 37;
  let offset = 0;
  return homeLevels.map((level) => {
    const count = Number(source.value[level.code.toLowerCase()] || 0);
    const length = total ? (count / total) * circumference : 0;
    const angle =
      -Math.PI / 2 + ((offset + length / 2) / circumference) * 2 * Math.PI;
    const item = {
      ...level,
      count,
      percent: total ? Math.round((count / total) * 100) : 0,
      dasharray: `${length} ${circumference}`,
      dashoffset: -offset,
      labelX: 60 + 37 * Math.cos(angle),
      labelY: 64 + 37 * Math.sin(angle),
    };
    offset += length;
    return item;
  });
}
const equipmentSegments = computed(() => donutSegments(equipmentStatus));
const sensorSegments = computed(() => donutSegments(sensorStatus));
const onlineRate = computed(() => {
  const total = Number(sensorStatus.value.total) || 0;
  return total
    ? ((total - Number(sensorStatus.value.offline || 0)) / total) * 100
    : 0;
});
async function loadStatus() {
  try {
    const [equipment, sensor, diagnosisPage] = await Promise.all([
      fetchEquipmentVibrationStatusStatistics(),
      fetchSensorVibrationStatusStatistics(),
      fetchIntelligentDiagnoses({ page: 1, size: 5 }),
    ]);
    equipmentStatus.value = equipment;
    sensorStatus.value = sensor;
    latestDiagnoses.value = diagnosisPage.records || [];
  } catch (error) {
    console.error("读取首页报警状态统计失败：", error);
  }
}

async function loadHomeStats() {
  try {
    const resp = await fetchHomeStatistics();
    // resp should match HomeStatistics DTO: { equipmentRanking: [], vibrationTrend: [], diagnosisTrend: [] }
    equipmentRanking.value = resp.equipmentRanking || [];
    vibrationTrendData.value = (resp.vibrationTrend || []).map((item) => ({
      date: item.date,
      normal: item.normal || 0,
      warning: item.warning || 0,
      alarm: item.alarm || 0,
      danger: item.danger || 0,
    }));
    diagnosisTrendData.value = (resp.diagnosisTrend || []).map((item) => ({
      date: item.date,
      healthy: item.healthy || 0,
      innerRace: item.innerRace || 0,
      outerRace: item.outerRace || 0,
    }));
  } catch (error) {
    console.error("读取首页统计数据失败：", error);
  }
}

async function loadProcessFlowSensors() {
  try {
    const equipment = await fetchEquipment();
    const sensorGroups = await Promise.all(
      equipment.map(async (item) => ({
        equipment: item,
        sensors: await fetchSensorVibrations(item.id),
      })),
    );
    processFlowSensors.value = sensorGroups.flatMap(({ equipment, sensors }) =>
      sensors
        .filter((sensor) => processSensorLayout[sensor.sensorCode])
        .map((sensor) => ({
          ...sensor,
          equipmentId: equipment.id,
          equipmentCode: equipment.equipCode,
          ...processSensorLayout[sensor.sensorCode],
        })),
    );
  } catch (error) {
    console.error("读取流程图测点状态失败：", error);
  }
}

function statusName(status) {
  return (
    {
      NORMAL: "正常",
      WARNING: "预警",
      ALARM: "报警",
      DANGER: "危险",
      OFFLINE: "离线",
    }[status] || "离线"
  );
}
function vibrationValue(value) {
  return Number.isFinite(Number(value)) ? Number(value).toFixed(2) : "--";
}
function openSensorDetail(sensor) {
  router.push({
    path: "/device-vibration",
    query: { equipmentId: sensor.equipmentId, sensorId: sensor.sensorId },
  });
}

function diagnosisTime(value) {
  return value ? value.replace("T", " ").slice(0, 16) : "--";
}
function diagnosisColor(text) {
  return (
    {
      健康: "#6cba4e",
      轴承内圈故障: "#4599ed",
      轴承外圈故障: "#8b6edc",
      当前数据特征不明确: "#1f2937",
    }[text] || "#8a8a8a"
  );
}

onMounted(() => {
  loadStatus();
  loadHomeStats();
  loadProcessFlowSensors();
});
onBeforeUnmount(() => window.clearInterval(clock));
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 68px);
  min-height: 620px;
  padding: 0 10px 10px;
  background: #09183b;
  box-sizing: border-box;
}
.system-banner {
  position: relative;
  display: flex;
  height: 88px;
  align-items: center;
  justify-content: center;
  padding: 0 28px;
  overflow: hidden;
  background: linear-gradient(90deg, #101b67, #061448);
  border-bottom: 1px solid #258bcb;
}
.system-banner::before,
.system-banner::after {
  position: absolute;
  top: 15px;
  width: 30%;
  height: 58px;
  content: "";
  opacity: 0.55;
  background: repeating-linear-gradient(
    90deg,
    transparent 0 12px,
    #0c75bb 13px 15px,
    transparent 16px 25px
  );
  clip-path: polygon(0 40%, 62% 40%, 76% 100%, 100% 100%, 100% 100%, 0 100%);
}
.system-banner::before {
  left: 12%;
}
.system-banner::after {
  right: 12%;
  transform: scaleX(-1);
}
.system-banner time,
.system-banner h1 {
  z-index: 1;
  margin: 0;
  color: #fff;
}
.system-banner time {
  position: absolute;
  left: 28px;
  font-size: 18px;
  font-weight: 600;
}
.system-banner h1 {
  font-size: 30px;
  letter-spacing: 2px;
}
.dashboard-grid {
  display: grid;
  flex: 1;
  min-height: 0;
  grid-template-columns: 26% minmax(410px, 1fr) 26%;
  gap: 10px;
  padding-top: 10px;
}
.left-column,
.center-column,
.right-column {
  display: grid;
  min-width: 0;
  min-height: 0;
  gap: 10px;
}
.left-column {
  grid-template-rows: 3fr 7fr;
}
.center-column {
  grid-template-rows: 62fr 38fr;
}
.right-column {
  grid-template-rows: 198px 1fr calc(38% - 4px);
}
.panel {
  min-height: 0;
  overflow: hidden;
  border: 1px solid #2b4e90;
  border-radius: 3px;
  background: #0e1a48;
}
.panel h2 {
  display: grid;
  height: 34px;
  margin: 0;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  place-items: center;
  background: #344c87;
}
.map-panel {
  display: grid;
  padding: 0;
  place-items: center;
  background: #06141e;
}
.process-flow-stage {
  position: relative;
  width: 100%;
  max-height: 100%;
  aspect-ratio: 2048 / 948;
}
.process-flow-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.process-sensor-marker {
  position: absolute;
  z-index: 2;
  width: 15px;
  height: 15px;
  padding: 0;
  cursor: pointer;
  background: #8a8a8a;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 3px #06141ecc, 0 0 8px currentColor;
  color: #8a8a8a;
  transform: translate(-50%, -50%);
}
.process-sensor-marker span:first-child {
  position: absolute;
  top: -21px;
  left: 50%;
  padding: 1px 4px;
  color: #fff;
  font-size: 10px;
  line-height: 13px;
  white-space: nowrap;
  pointer-events: none;
  background: #071322dd;
  border-radius: 2px;
  transform: translateX(-50%);
}
.process-sensor-marker.is-normal,
.process-sensor-tooltip i.is-normal { background: #75be4f; color: #75be4f; }
.process-sensor-marker.is-warning,
.process-sensor-tooltip i.is-warning { background: #ffe000; color: #ffe000; }
.process-sensor-marker.is-alarm,
.process-sensor-tooltip i.is-alarm { background: #ff9d00; color: #ff9d00; }
.process-sensor-marker.is-danger,
.process-sensor-tooltip i.is-danger { background: #cf3535; color: #cf3535; }
.process-sensor-marker.is-offline,
.process-sensor-tooltip i.is-offline { background: #8a8a8a; color: #8a8a8a; }
.process-sensor-marker:hover,
.process-sensor-marker:focus-visible {
  z-index: 5;
  outline: none;
  transform: translate(-50%, -50%) scale(1.18);
}
.process-sensor-tooltip {
  position: absolute;
  top: 16px;
  left: 12px;
  display: none;
  width: max-content;
  min-width: 182px;
  max-width: 240px;
  padding: 8px 10px;
  color: #243750;
  font-size: 12px;
  line-height: 1.6;
  text-align: left;
  pointer-events: none;
  background: #fff;
  border: 1px solid #9fc4e4;
  border-radius: 3px;
  box-shadow: 0 4px 12px #0007;
}
.process-sensor-marker:hover .process-sensor-tooltip,
.process-sensor-marker:focus-visible .process-sensor-tooltip {
  display: block;
}
.process-sensor-tooltip b,
.process-sensor-tooltip em,
.process-sensor-tooltip p { display: block; margin: 0; }
.process-sensor-tooltip b { color: #176ebd; }
.process-sensor-tooltip em { overflow: hidden; font-style: normal; white-space: nowrap; text-overflow: ellipsis; }
.process-sensor-tooltip i { display: inline-block; width: 8px; height: 8px; margin-right: 5px; border-radius: 50%; }
.status-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.overview-content {
  display: grid;
  grid-template-columns: minmax(118px, 1fr) 1.1fr;
  align-items: center;
  gap: 8px;
  height: calc(100% - 34px);
  padding: 15px 60px;
  box-sizing: border-box;
}
.overview-counts {
  display: grid;
  justify-self: start;
  gap: 17px;
  padding-left: 12px;
}
.overview-counts p {
  display: grid;
  grid-template-columns: auto 55px auto;
  align-items: baseline;
  column-gap: 15px;
  margin: 0;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}
.overview-counts p span {
  display: inline-block;
  text-align: right;
  white-space: nowrap;
}
.overview-counts b {
  color: #28e21a;
  font-size: 30px;
  line-height: 1;
  text-align: right;
}
.overview-counts em {
  color: #fff;
  font-size: 20px;
  font-style: normal;
}
.online-rate {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 0;
  color: #b8d4ee;
  font-size: 16px;
  cursor: default;
}
.online-rate svg {
  width: 130px;
  height: 130px;
}
.rate-base,
.rate-value {
  fill: none;
  stroke-width: 18px;
}
.rate-base {
  stroke: #25396c;
}
.rate-value {
  stroke: #78c153;
  stroke-linecap: butt;
}
.online-rate text {
  fill: #fff;
  font-size: 16px;
  text-anchor: middle;
}
.online-tooltip {
  position: absolute;
  z-index: 12;
  right: calc(100% + 8px);
  bottom: 0;
  display: none;
  min-width: 148px;
  padding: 9px 11px;
  color: #334760;
  background: #fff;
  border: 1px solid #75be4f;
  border-radius: 4px;
  box-shadow: 0 3px 10px #0005;
}
.online-rate:hover .online-tooltip {
  display: block;
}
.online-tooltip p {
  margin: 4px 0;
  font-size: 12px;
  white-space: nowrap;
}
.online-tooltip i {
  display: inline-block;
  width: 9px;
  height: 9px;
  margin-right: 6px;
  border-radius: 50%;
}
.online-dot {
  background: #78c153;
}
.offline-dot {
  background: #8a8a8a;
}
.home-donut-wrap {
  position: relative;
  display: grid;
  height: calc(100% - 34px);
  place-items: center;
}
.home-donut-wrap svg {
  width: 128px;
  height: 128px;
}
.home-donut-base,
.home-donut-segment {
  fill: none;
  stroke-width: 18px;
}
.home-donut-base {
  stroke: #43557d;
}
.home-donut-segment {
  stroke-linecap: butt;
}
.home-segment-label {
  fill: #fff;
  font-size: 11px;
  font-weight: 700;
  text-anchor: middle;
}
.home-total-label {
  fill: #fff;
  font-size: 21px;
  font-weight: 700;
  text-anchor: middle;
}
.home-tooltip {
  position: absolute;
  z-index: 10;
  right: calc(100% + 8px);
  bottom: 8px;
  display: none;
  min-width: 155px;
  padding: 9px 11px;
  color: #334760;
  background: #fff;
  border: 1px solid #75be4f;
  border-radius: 4px;
  box-shadow: 0 3px 10px #0004;
}
.home-donut-wrap:hover .home-tooltip {
  display: block;
}
.home-tooltip p {
  margin: 4px 0;
  font-size: 12px;
  white-space: nowrap;
}
.home-tooltip p:first-child {
  font-weight: 700;
}
.home-tooltip i {
  display: inline-block;
  width: 9px;
  height: 9px;
  margin-right: 6px;
}
.compact-panel {
  position: relative;
  z-index: 1;
  overflow: visible;
}
.compact-panel:has(.home-donut-wrap:hover) {
  z-index: 10;
}
.latest-diagnosis-table {
  height: calc(100% - 34px);
  overflow: hidden;
}
.latest-diagnosis-table table {
  width: 100%;
  height: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.latest-diagnosis-table thead {
  height: 18%;
}
.latest-diagnosis-table tbody tr {
  height: 16.4%;
}
.latest-diagnosis-table th,
.latest-diagnosis-table td {
  padding: 0 13px;
  color: #dceaff;
  font-size: 14px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.latest-diagnosis-table th {
  color: #fff;
  font-weight: 700;
  background: #233a77;
}
.latest-diagnosis-table tbody tr:nth-child(odd) {
  background: #203866;
}
.latest-diagnosis-table tbody tr:nth-child(even) {
  background: #172b57;
}
.latest-diagnosis-table td:nth-child(1) {
  width: 27%;
}
.latest-diagnosis-table td:nth-child(2),
.latest-diagnosis-table td:nth-child(3) {
  color: #70c5ff;
}
.latest-diagnosis-table td:last-child {
  color: #fff;
  font-weight: 600;
}
.latest-diagnosis-table td i {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 7px;
  border-radius: 2px;
  vertical-align: -1px;
}
@media (max-width: 1100px) {
  .dashboard-grid {
    grid-template-columns: 1fr 1fr;
  }
  .right-column {
    grid-column: 1 / -1;
    grid-template-columns: 1fr 1fr;
    grid-template-rows: auto 260px;
  }
  .status-panels {
    grid-column: 1 / -1;
  }
  .left-column {
    grid-template-rows: 198px 360px;
  }
}
@media (max-width: 760px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  .right-column {
    grid-column: auto;
    grid-template-columns: 1fr;
    grid-template-rows: auto 230px 230px;
  }
  .center-column {
    grid-template-rows: 350px 220px;
  }
}
</style>
