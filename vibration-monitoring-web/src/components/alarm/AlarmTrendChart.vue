<template>
  <div
    ref="chartRef"
    class="trend-chart"
    aria-label="近三十天振动预警次数趋势"
  ></div>
</template>
<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { init, use } from "echarts/core";
import { BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { statusLevels } from '@/config/statusLevels'
use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);
const props = defineProps({
  data: { type: Array, default: () => [] },
  homeTheme: { type: Boolean, default: false },
  showNormal: { type: Boolean, default: true },
});
const chartRef = ref(null);
let chart;
function render() {
  if (!chart) return;
  const dates = props.data.map((item) => item.date?.slice(5));
  const axisTextColor = props.homeTheme ? "white" : "#34495e";
  const axisLineColor = props.homeTheme ? "#718397" : "#34495e";
  const levels = props.showNormal ? statusLevels : statusLevels.filter(s => s.code !== 'NORMAL');
  chart.setOption(
    {
      color: levels.map(s => s.color),
      grid: { left: 40, right: 8, top: 12, bottom: 75 },
      tooltip: {
        trigger: "axis",
        backgroundColor: "#fff",
        borderColor: "#75be4f",
        borderWidth: 1,
        textStyle: { color: "#526273" },
        formatter: (params) => {
          const total = params.reduce((sum, item) => sum + item.value, 0);
          const date = props.data[params[0].dataIndex]?.date ?? "";
          const rows = params.map(item => {
            const cfg = statusLevels.find(s => s.name === item.seriesName);
            const color = cfg ? cfg.color : item.color || '#000';
            const marker = `<span style="display:inline-block;margin-right:6px;border-radius:3px;width:10px;height:10px;background:${color};"></span>`;
            return `${marker}${item.seriesName}：${item.value}次（${total ? Math.round((item.value / total) * 100) : 0}%）`;
          });
          return `${date}（共${total}次）<br/>${rows.join("<br/>")}`;
        },
      },
      xAxis: {
        type: "category",
        data: dates,
        axisTick: { show: true },
        axisLabel: {
          show: true,
          fontSize: 10,
          color: axisTextColor,
          interval: "auto",
          rotate: -45,
          margin: 8,
        },
        axisLine: { show: true, lineStyle: { color: axisLineColor } },
      },
      yAxis: {
        type: "value",
        name: "次",
        axisTick: { show: true },
        nameTextStyle: { color: axisTextColor },
        minInterval: 1,
        axisLabel: { fontSize: 10, color: axisTextColor },
        axisLine: { show: true, lineStyle: { color: axisLineColor } },
        splitLine: { show: false },
      },
      series: levels.map((lvl) => ({
        name: lvl.name,
        type: 'bar',
        stack: 'total',
        barMaxWidth: 9,
        data: props.data.map(item => Number(item[lvl.code.toLowerCase()] || 0)),
        itemStyle: { color: lvl.color },
      })),
    },
    true,
  );
}
onMounted(async () => {
  await nextTick();
  chart = init(chartRef.value);
  chart.resize();
  render();
  window.addEventListener("resize", () => chart?.resize());
});
watch(() => props.data, render, { deep: true });
onBeforeUnmount(() => chart?.dispose());
</script>
<style scoped>
.trend-chart {
  width: 100%;
  height: 100%;
  min-height: 170px;
  padding-bottom: 6px;
}
</style>
