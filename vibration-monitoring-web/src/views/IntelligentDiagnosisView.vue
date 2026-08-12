<template>
  <section class="diagnosis-layout">
    <section class="diagnosis-table-panel">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>序号</th>
              <th>时间</th>
              <th>设备</th>
              <th>位置</th>
              <th>诊断结论</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="empty-row">正在加载…</td>
            </tr>
            <tr v-else-if="error">
              <td colspan="6" class="empty-row error-row">
                {{ error }} <button @click="load">重新加载</button>
              </td>
            </tr>
            <tr v-else-if="!pageData.records.length">
              <td colspan="6" class="empty-row">当前条件下暂无诊断记录</td>
            </tr>
            <tr v-for="(row, index) in pageData.records" :key="row.id">
              <td>{{ (pageData.page - 1) * pageData.size + index + 1 }}</td>
              <td class="time">
                <span>{{ date(row.diagnosisTime) }}</span
                ><small>{{ clock(row.diagnosisTime) }}</small>
              </td>
              <td>
                <span class="equipment">{{ row.equipmentCode || "--" }}</span>
              </td>
              <td><router-link class="position-link" :to="{ path: '/device-vibration', query: { equipmentId: row.equipmentId, sensorId: row.sensorId } }">{{ row.mountPosition || row.sensorCode || "--" }}</router-link></td>
              <td class="conclusion">
                <span class="diagnosis-message"
                  ><i
                    class="diagnosis-indicator"
                    :style="{
                      backgroundColor: diagnosisColor(row.diagnosisText),
                    }"
                  ></i
                  ><b>{{ row.diagnosisText }}</b></span
                >
              </td>
              <td>
                <button
                  class="detail"
                  aria-label="查看波形详情"
                  @click="view(row)"
                >
                  <svg viewBox="0 0 24 16" aria-hidden="true">
                    <polyline
                      points="1,9 4,9 6,3 9,14 12,6 15,10 18,2 21,9 23,9"
                    />
                  </svg>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer>
        <span>共 {{ pageData.total }} 条</span
        ><select>
          <option>20条/页</option></select
        ><button :disabled="pageData.page <= 1" @click="go(pageData.page - 1)">
          ‹</button
        ><button
          v-for="item in pages"
          :key="item"
          :class="{ active: item === pageData.page }"
          @click="go(item)"
        >
          {{ item }}</button
        ><button
          :disabled="pageData.page >= totalPages"
          @click="go(pageData.page + 1)"
        >
          ›
        </button>
      </footer>
    </section>
    <aside class="diagnosis-sidebar">
      <article class="statistics-card">
        <h2>诊断台数/次数</h2>
        <div class="diagnosis-count-content">
          <div
            v-for="item in diagnosisDonuts"
            :key="item.name"
            class="diagnosis-donut"
          >
            <svg viewBox="0 0 120 120">
              <circle class="donut-base" cx="60" cy="60" r="42" />
              <circle
                v-for="segment in item.segments"
                :key="segment.key"
                cx="60"
                cy="60"
                r="42"
                class="donut-segment"
                :stroke="segment.color"
                :stroke-dasharray="segment.dasharray"
                :stroke-dashoffset="segment.dashoffset"
                transform="rotate(-90 60 60)"
              />
              <text
                v-for="segment in item.segments.filter(
                  (segment) => segment.count > 0,
                )"
                :key="`${segment.key}-label`"
                class="segment-label"
                :x="segment.labelX"
                :y="segment.labelY"
              >
                {{ segment.count }}
              </text>
              <text x="60" y="64">{{ item.total }}</text></svg
            ><span>{{ item.name }}</span>
            <div class="diagnosis-tooltip">
              <p class="tooltip-total">共 {{ item.total }} {{ item.unit }}</p>
              <p v-for="segment in item.segments" :key="segment.key">
                <i :style="{ backgroundColor: segment.color }"></i
                >{{ segment.name }}：{{ segment.count }} {{ item.unit }}（{{
                  segment.percent
                }}%）
              </p>
            </div>
          </div>
        </div>
      </article>
      <article class="statistics-card">
        <h2>智能诊断-时间（按天）</h2>
        <div class="trend-content"><DiagnosisTrendChart :data="diagnosisStatistics.dailyTrend" /></div>
      </article>
      <article class="statistics-card">
        <h2>智能诊断-设备</h2>
        <div class="equipment-scroll"><DiagnosisEquipmentChart :data="diagnosisStatistics.equipmentRanking" /></div>
      </article>
    </aside>
    <div v-if="selected" class="overlay" @click.self="close">
      <section class="dialog">
        <header>
          <h2>
            {{ selected.equipmentCode || "--" }} /
            {{ selected.mountPosition || selected.sensorCode || "--" }} @
            {{ fullTime(selected.diagnosisTime) }}
          </h2>
          <button @click="close">×</button>
        </header>
        <nav>
          <button
            :class="{ active: tab === 'diagnosis' }"
            @click="tab = 'diagnosis'"
          >
            诊断结论</button
          ><button
            :class="{ active: tab === 'velocity' }"
            @click="tab = 'velocity'"
          >
            速度</button
          ><button
            :class="{ active: tab === 'acceleration' }"
            @click="tab = 'acceleration'"
          >
            加速度
          </button>
        </nav>
        <div v-if="tab === 'diagnosis'" class="detail-content">
          <h3>{{ selected.diagnosisText }}</h3>
          <div class="metrics">
            <span
              >置信度<b>{{ percent(selected.confidence) }}</b></span
            ><span
              >结果稳定性<b>{{ percent(selected.stability) }}</b></span
            >
          </div>
          <p>
            <strong>维护建议：</strong
            >{{ selected.maintenanceSuggestion || "--" }}
          </p>
        </div>
        <div v-else-if="waveLoading" class="chart-state">
          正在加载三轴波形与频谱…
        </div>
        <div v-else-if="waveError" class="chart-state">{{ waveError }}</div>
        <div v-else-if="waveDetail" class="charts">
          <WaveformAxisChart
            v-for="axis in axes"
            :key="axis.key"
            :axis="axis"
            :mode="tab"
            :detail="waveDetail"
          />
        </div>
      </section>
    </div>
  </section>
</template>
<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  fetchIntelligentDiagnoses,
  fetchIntelligentDiagnosisStatistics,
  fetchEquipmentStatus,
  fetchLatestDiagnosisByEquipment,
  fetchWaveformDetail,
} from "@/api/equipment";
import WaveformAxisChart from "@/components/alarm/WaveformAxisChart.vue";
import DiagnosisTrendChart from "@/components/diagnosis/DiagnosisTrendChart.vue";
import DiagnosisEquipmentChart from "@/components/diagnosis/DiagnosisEquipmentChart.vue";
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const error = ref("");
const pageData = ref({ records: [], total: 0, page: 1, size: 20 });
const diagnosisStatistics = ref({
  equipment: { healthy: 0, innerRace: 0, outerRace: 0, unclear: 0 },
  records: { healthy: 0, innerRace: 0, outerRace: 0, unclear: 0 },
  dailyTrend: [],
  equipmentRanking: [],
});
const deviceDiagnosisCounts = ref({ healthy: 0, innerRace: 0, outerRace: 0, offline: 0 });
const selected = ref(null);
const tab = ref("diagnosis");
const waveDetail = ref(null);
const waveLoading = ref(false);
const waveError = ref("");
const axes = [
  { key: "x", name: "水平" },
  { key: "y", name: "垂直" },
  { key: "z", name: "轴向" },
];
const totalPages = computed(() =>
  Math.max(1, Math.ceil(pageData.value.total / pageData.value.size)),
);
const pages = computed(() =>
  Array.from(
    { length: Math.min(7, totalPages.value) },
    (_, index) => index + 1,
  ),
);
const diagnosisCategories = [
  { key: "healthy", name: "健康", color: "#6cba4e" },
  { key: "innerRace", name: "轴承内圈故障", color: "#4599ed" },
  { key: "outerRace", name: "轴承外圈故障", color: "#8b6edc" },
  { key: "unclear", name: "数据特征不明确", color: "#1f2937" },
];
const faultCategories = diagnosisCategories.filter(
  (category) => category.key === "innerRace" || category.key === "outerRace",
);
const deviceDiagnosisCategories = [
  { key: "healthy", name: "健康", color: "#6cba4e" },
  { key: "innerRace", name: "轴承内圈故障", color: "#4599ed" },
  { key: "outerRace", name: "轴承外圈故障", color: "#8b6edc" },
  { key: "offline", name: "离线", color: "#9dacba" },
];
const recordDiagnosisCategories = diagnosisCategories.filter(
  (category) => category.key !== "unclear",
);
const diagnosisDonuts = computed(() =>
  [
    {
      name: "诊断设备",
      unit: "台",
      levels: deviceDiagnosisCounts.value,
      categories: deviceDiagnosisCategories,
    },
    { name: "诊断次数", unit: "次", levels: diagnosisStatistics.value.records, categories: recordDiagnosisCategories },
  ].map((item) => {
    const total = item.categories.reduce(
      (sum, category) => sum + Number(item.levels[category.key] || 0),
      0,
    );
    const circumference = 2 * Math.PI * 42;
    let offset = 0;
    const segments = item.categories.map((category) => {
      const count = Number(item.levels[category.key] || 0);
      const length = total ? (count / total) * circumference : 0;
      const angle =
        -Math.PI / 2 + ((offset + length / 2) / circumference) * 2 * Math.PI;
      const segment = {
        ...category,
        count,
        percent: total ? Math.round((count / total) * 100) : 0,
        dasharray: `${length} ${circumference}`,
        dashoffset: -offset,
        labelX: 60 + 42 * Math.cos(angle),
        labelY: 64 + 42 * Math.sin(angle),
      };
      offset += length;
      return segment;
    });
    return { ...item, total, segments };
  }),
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const params = {
      ...route.query,
      page: Number(route.query.page) || 1,
      size: 20,
    };
    const [page, stats, equipmentStatuses, latestDiagnoses] = await Promise.all([
      fetchIntelligentDiagnoses(params),
      fetchIntelligentDiagnosisStatistics(params),
      fetchEquipmentStatus(),
      fetchLatestDiagnosisByEquipment(),
    ]);
    pageData.value = page;
    diagnosisStatistics.value = stats;
    const selectedEquipmentId = route.query.equipmentId ? String(route.query.equipmentId) : "";
    const latestByEquipment = new Map(latestDiagnoses.map((item) => [String(item.equipmentId), item.diagnosisText]));
    const counts = { healthy: 0, innerRace: 0, outerRace: 0, offline: 0 };
    equipmentStatuses
      .filter((item) => !selectedEquipmentId || String(item.id) === selectedEquipmentId)
      .forEach((item) => {
        if (item.status === "OFFLINE") { counts.offline += 1; return; }
        const diagnosis = latestByEquipment.get(String(item.id));
        if (diagnosis === "健康") counts.healthy += 1;
        else if (diagnosis === "轴承内圈故障") counts.innerRace += 1;
        else if (diagnosis === "轴承外圈故障") counts.outerRace += 1;
      });
    deviceDiagnosisCounts.value = counts;
  } catch (e) {
    console.error(e);
    error.value = "诊断记录加载失败，请检查后端服务后重试";
    pageData.value = { records: [], total: 0, page: 1, size: 20 };
  } finally {
    loading.value = false;
  }
}
function go(page) {
  router.push({
    query: {
      ...route.query,
      page: String(Math.max(1, Math.min(page, totalPages.value))),
    },
  });
}
function date(v) {
  return v ? v.slice(0, 10) : "--";
}
function clock(v) {
  return v ? v.replace("T", " ").slice(11, 16) : "";
}
function fullTime(v) {
  return v ? v.replace("T", " ").slice(0, 19) : "--";
}
function percent(v) {
  return `${Math.round(Number(v || 0) * 100)}%`;
}
function diagnosisColor(text) {
  return (
    diagnosisCategories.find((item) => item.name === text)?.color ?? "#1f2937"
  );
}
async function view(row) {
  selected.value = row;
  tab.value = "diagnosis";
  waveDetail.value = null;
  waveError.value = "";
  waveLoading.value = true;
  try {
    waveDetail.value = await fetchWaveformDetail(row.batchId);
  } catch (e) {
    waveError.value = "关联波形暂时无法读取。";
  } finally {
    waveLoading.value = false;
  }
}
function close() {
  selected.value = null;
  waveDetail.value = null;
}
watch(() => route.query, load, { immediate: true });
</script>
<style scoped>
.diagnosis-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  height: calc(100vh - 68px);
  min-height: 0;
  overflow: hidden;
  background: #f4f6f8;
}
.diagnosis-table-panel {
  display: grid;
  grid-template-rows: minmax(0, 1fr) 38px;
  min-width: 0;
  height: calc(100vh - 68px);
  padding: 8px 6px 0 8px;
  box-sizing: border-box;
}
.table-scroll {
  min-height: 0;
  overflow-x: auto;
  overflow-y: scroll;
  background: #fff;
  border: 1px solid #e3e9f0;
}
table {
  width: 100%;
  min-width: 880px;
  border-collapse: collapse;
  table-layout: fixed;
}
th,
td {
  height: 58px;
  padding: 0 14px;
  color: #3f566c;
  font-size: 14px;
  text-align: center;
  border-right: 1px solid #e7edf3;
  border-bottom: 1px solid #e7edf3;
  box-sizing: border-box;
}
th {
  height: 42px;
  color: #5d7590;
  font-size: 15px;
  font-weight: 700;
  background: linear-gradient(180deg, #fbfdff, #f2f6fa);
  border-bottom: 2px solid #dce7f0;
}
th:nth-child(1) {
  width: 60px;
}
th:nth-child(2) {
  width: 122px;
}
th:nth-child(3) {
  width: 160px;
}
th:nth-child(4) {
  width: 134px;
}
th:nth-child(6) {
  width: 78px;
}
tbody tr:nth-child(even) td {
  background: #fbfcfe;
}
tbody tr:hover td {
  background: #f1f8ff;
}
.empty-row {
  color: #8b98a5;
}
.error-row {
  color: #c95555;
}
.error-row button {
  margin-left: 12px;
  color: #337dcc;
  cursor: pointer;
  background: #fff;
  border: 1px solid #bcd6ee;
  border-radius: 3px;
}
.time span,
.time small {
  display: block;
  line-height: 1.35;
}
.time small {
  margin-top: 3px;
  color: #8c9bad;
  font-size: 12px;
}
.equipment {
  display: inline-flex;
  align-items: center;
  min-height: 27px;
  padding: 0 10px;
  color: #2869a8;
  font-weight: 600;
  background: #eaf4ff;
  border: 1px solid #cce5fb;
  border-radius: 14px;
}
.position-link {
  color: #3176b9;
  text-decoration: none;
}
.position-link:hover {
  text-decoration: underline;
}
.conclusion {
  text-align: center;
}
.diagnosis-message {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  align-items: center;
  justify-content: start;
  column-gap: 10px;
  padding: 0 24px 0 40%;
  text-align: left;
}
.diagnosis-message b {
  color: #3176b9;
  line-height: 1.5;
}
.diagnosis-indicator {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  box-shadow: 0 0 0 3px #edf2f7;
}
.detail {
  display: inline-grid;
  width: 45px;
  height: 32px;
  padding: 0;
  cursor: pointer;
  place-items: center;
  background: #4599ed;
  border: 0;
  border-radius: 4px;
}
.detail svg {
  width: 22px;
  height: 16px;
  overflow: visible;
}
.detail polyline {
  fill: none;
  stroke: #fff;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}
.diagnosis-table-panel footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 38px;
  color: #435466;
  font-size: 13px;
  background: #fff;
  border: 1px solid #e3e9f0;
  border-top: 0;
}
.diagnosis-table-panel footer button {
  padding: 0;
  color: #31475f;
  cursor: pointer;
  background: transparent;
  border: 0;
}
.diagnosis-table-panel footer .active {
  color: #3387dd;
  font-weight: 700;
}
.diagnosis-table-panel footer select {
  height: 27px;
  border: 1px solid #d5dfe8;
  border-radius: 3px;
}
.diagnosis-sidebar {
  display: grid;
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 8px;
  height: calc(100vh - 68px);
  min-height: 0;
  padding: 8px 8px 0;
  box-sizing: border-box;
  background: #fff;
  border-left: 1px solid #dce3eb;
}
.statistics-card {
  display: grid;
  position: relative;
  z-index: 1;
  grid-template-rows: 48px minmax(0, 1fr);
  min-height: 0;
  overflow: visible;
  background: #f7f7f7;
  border-radius: 3px;
}
.statistics-card:first-child {
  position: relative;
  z-index: 4;
}
.statistics-card h2 {
  display: flex;
  align-items: center;
  margin: 0;
  padding: 0 16px;
  color: #3176b9;
  font-size: 17px;
  font-weight: 700;
  background: #fff;
}
.statistics-card h2::before {
  width: 5px;
  height: 20px;
  margin-right: 11px;
  content: "";
  background: #367fc4;
}
.trend-content,
.equipment-scroll {
  min-height: 0;
}
.equipment-scroll {
  overflow-y: auto;
}
.overlay {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: grid;
  padding: 24px;
  background: #1d2e3d73;
  place-items: center;
  box-sizing: border-box;
}
.dialog {
  display: grid;
  grid-template-rows: 56px 44px minmax(360px, 1fr);
  width: min(1220px, 100%);
  height: min(760px, 100%);
  overflow: hidden;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 12px 34px #13213055;
}
.dialog header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-bottom: 1px solid #e2e8ef;
}
.dialog header h2 {
  margin: 0;
  color: #34495e;
  font-size: 17px;
  font-weight: 500;
}
.dialog header button {
  width: 30px;
  height: 30px;
  padding: 0;
  color: #8997a5;
  font-size: 24px;
  cursor: pointer;
  background: transparent;
  border: 0;
}
.dialog nav {
  display: flex;
  align-items: end;
  gap: 2px;
  padding: 0 18px;
  border-bottom: 1px solid #dce4ec;
}
.dialog nav button {
  min-width: 68px;
  height: 32px;
  color: #526273;
  cursor: pointer;
  background: #fff;
  border: 1px solid #d4dfe9;
  border-bottom: 0;
  border-radius: 3px 3px 0 0;
}
.dialog nav .active {
  color: #fff;
  background: #4599ed;
  border-color: #4599ed;
}
.detail-content {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 17px;
  padding: 35px;
  text-align: center;
  background: #f8fbfe;
}
.detail-content h3 {
  margin: 0;
  color: #3278be;
  font-size: 24px;
}
.detail-content p {
  margin: 0;
  color: #657789;
}
.metrics {
  display: flex;
  gap: 12px;
}
.metrics span {
  display: grid;
  gap: 6px;
  min-width: 115px;
  padding: 10px;
  background: #fff;
  border: 1px solid #dce7f0;
  border-radius: 4px;
}
.metrics b {
  color: #34495e;
  font-size: 17px;
}
.chart-state {
  display: grid;
  color: #657789;
  place-items: center;
}
.charts {
  min-height: 0;
  padding: 10px 16px;
  overflow: auto;
}
.diagnosis-count-content {
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  padding: 8px 4px;
}
.diagnosis-donut {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 3px;
  color: #617385;
  font-size: 13px;
}
.diagnosis-donut svg {
  width: 108px;
  height: 108px;
}
.diagnosis-donut circle {
  fill: none;
  stroke-width: 18px;
}
.donut-base {
  stroke: #e4eaf0;
}
.donut-segment {
  stroke-linecap: butt;
}
.diagnosis-donut text {
  fill: #34495e;
  font-size: 24px;
  font-weight: 700;
  text-anchor: middle;
}
.diagnosis-donut .segment-label {
  fill: #fff;
  font-size: 12px;
  font-weight: 700;
  dominant-baseline: middle;
}
.diagnosis-tooltip {
  position: absolute;
  z-index: 8;
  right: calc(100% + 10px);
  bottom: 0;
  display: none;
  min-width: 180px;
  padding: 10px 12px;
  color: #526273;
  background: #fff;
  border: 1px solid #75be4f;
  border-radius: 4px;
  box-shadow: 0 3px 10px #0002;
}
.diagnosis-donut:hover .diagnosis-tooltip {
  display: block;
}
.diagnosis-tooltip p {
  margin: 5px 0;
  font-size: 12px;
  white-space: nowrap;
}
.diagnosis-tooltip .tooltip-total {
  margin-top: 0;
  color: #42576b;
  font-weight: 700;
}
.diagnosis-tooltip p:last-child {
  margin-bottom: 0;
}
.diagnosis-tooltip i {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 7px;
  vertical-align: -1px;
}
</style>
