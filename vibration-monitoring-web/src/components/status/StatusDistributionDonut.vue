<template>
  <div class="status-chart-wrap">
    <div class="status-donut">
      <svg viewBox="0 0 150 150" :aria-label="title">
        <circle class="donut-background" cx="75" cy="75" r="46" />
        <circle v-for="segment in segments" :key="segment.code" class="donut-segment" cx="75" cy="75" r="46" :stroke="segment.color" :stroke-dasharray="segment.dasharray" :stroke-dashoffset="segment.dashoffset" transform="rotate(-90 75 75)" />
        <circle class="donut-hole" cx="75" cy="75" r="30" />
        <text v-for="segment in segments" v-show="segment.count > 0" :key="`${segment.code}-label`" class="donut-segment-label" :x="segment.labelX" :y="segment.labelY">{{ segment.count }}</text>
        <text class="donut-total-label" x="75" y="82">{{ total }}</text>
      </svg>
    </div>
    <div class="status-tooltip">
      <p>共 {{ total }} 台</p>
      <p v-for="segment in segments" :key="segment.code"><i :style="{ backgroundColor: segment.color }"></i>{{ segment.name }}：{{ segment.count }} 台（{{ segment.percent }}%）</p>
    </div>
  </div>
</template>
<script setup>
defineProps({ title: { type: String, required: true }, total: { type: Number, default: 0 }, segments: { type: Array, default: () => [] } })
</script>
<style scoped>
.status-chart-wrap{position:relative}.status-donut{position:relative;width:168px;height:168px}.status-donut svg{width:100%;height:100%}.donut-background,.donut-segment{fill:none;stroke-width:22px}.donut-background{stroke:#dfe5ec}.donut-segment{stroke-linecap:butt}.donut-hole{fill:#f7f7f7}.donut-segment-label{fill:#fff;font-size:14px;font-weight:700;text-anchor:middle}.donut-total-label{fill:#4f6174;font-size:22px;font-weight:700;text-anchor:middle}.status-tooltip{position:absolute;z-index:7;right:calc(100% + 12px);bottom:8px;display:none;min-width:160px;padding:10px 12px;color:#5f6872;background:#fff;border:1px solid #7bbe58;border-radius:4px;box-shadow:0 2px 8px #0002}.status-chart-wrap:hover .status-tooltip{display:block}.status-tooltip p{display:flex;align-items:center;margin:4px 0;font-size:13px;white-space:nowrap}.status-tooltip p:first-child{margin-top:0;font-weight:700}.status-tooltip p:last-child{margin-bottom:0}.status-tooltip i{width:10px;height:10px;margin-right:7px}
</style>
