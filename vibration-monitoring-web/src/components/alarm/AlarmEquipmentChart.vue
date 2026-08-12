<template>
  <div
    ref="chartRef"
    class="equipment-chart"
    :style="{ height: `${Math.max(170, data.length * 28)}px` }"
  ></div>
</template>
<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { init, use } from "echarts/core";
import { BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { statusLevels } from "@/config/statusLevels";
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
  const labelColor = props.homeTheme ? "white" : "#34495e";
  chart.resize();
  const levels = props.showNormal ? statusLevels : statusLevels.filter((s) => s.code !== 'NORMAL');
  chart.setOption(
    {
      color: levels.map((s) => s.color),
      grid: { left: 20, right: 80, top: 12, bottom: 8 },
      tooltip: {
        trigger: "axis",
        appendToBody: true,
        confine: false,
        axisPointer: { type: "shadow" },
        backgroundColor: "#fff",
        borderColor: "#75be4f",
        borderWidth: 1,
        textStyle: { color: "#526273" },
        formatter: (p) => {
          const item = props.data[p[0].dataIndex],
            total = p.reduce((s, x) => s + x.value, 0);
          const rows = p.map((x) => {
            const cfg = statusLevels.find((s) => s.name === x.seriesName);
            const color = cfg ? cfg.color : x.color || "#000";
            const marker = `<span style="display:inline-block;margin-right:6px;border-radius:3px;width:10px;height:10px;background:${color};"></span>`;
            return `${marker}${x.seriesName}：${x.value}次（${total ? Math.round((x.value / total) * 100) : 0}%）`;
          });
          return `${item.equipmentCode}（共${total}次）<br/>${rows.join("<br/>")}`;
        },
      },
      xAxis: { type: "value", show: false },
      yAxis: {
        type: "category",
        inverse: true,
        data: props.data.map((x) => x.equipmentCode),
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { show: false },
      },
      series: [
        ...levels.map((lvl) => ({
          name: lvl.name,
          type: 'bar',
          stack: 'total',
          barMaxWidth: 15,
          data: props.data.map((x) => Number(x[lvl.code.toLowerCase()] || 0)),
          itemStyle: { color: lvl.color },
        })),
        // label series: render equipmentCode at the end of each stacked bar
        {
          name: "label",
          type: "bar",
          stack: "total",
          barGap: "-100%",
          barMaxWidth: 15,
          data: props.data.map(() => 0),
          itemStyle: { color: "transparent" },
          label: {
            show: true,
            position: "right",
            color: labelColor,
            fontWeight: 700,
            formatter: (params) => props.data[params.dataIndex]?.equipmentCode || "",
          },
          emphasis: { disabled: true },
          silent: true,
        },
      ],
    },
    true,
  );
}
onMounted(async () => {
  await nextTick();
  chart = init(chartRef.value);
  render();
  window.addEventListener("resize", () => chart?.resize());
});
watch(() => props.data, render, { deep: true });
onBeforeUnmount(() => chart?.dispose());
</script>
<style scoped>
.equipment-chart {
  width: 100%;
  min-height: 170px;
}
</style>
