<template>
  <div class="status-level-legend" aria-label="设备状态等级">
    <span
      v-for="level in statusLevels"
      :key="level.code"
      :title="level.name"
      :style="{ backgroundColor: level.color }"
    >
      ✓
    </span>
  </div>
</template>

<script setup>
import { statusLevels } from '@/config/statusLevels'
</script>

<style scoped>
.status-level-legend {
  display: flex;
  overflow: hidden;
  border-radius: 1px;
}

.status-level-legend span {
  display: grid;
  width: 32px;
  height: 32px;
  color: #fff;
  font-size: 23px;
  font-weight: 700;
  line-height: 1;
  place-items: center;
}
</style>
