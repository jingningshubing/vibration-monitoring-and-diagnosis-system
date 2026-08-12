package com.cq.vibration.service;

import java.time.LocalDateTime;

/**
 * 设备当前状态汇总服务。
 */
public interface EquipmentStatusAggregationService {

    /**
     * 根据每个传感器的最新采集批次刷新全部设备的当前状态。
     *
     * <p>输入：本轮采集时间。输出：更新 {@code jzjc01_equipment_current_status}。</p>
     *
     * @param collectTime 本轮定时采集时间
     */
    void refreshAll(LocalDateTime collectTime);
}
