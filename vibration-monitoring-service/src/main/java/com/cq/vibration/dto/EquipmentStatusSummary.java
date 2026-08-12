package com.cq.vibration.dto;

import java.time.LocalDateTime;

/**
 * 设备卡片的当前状态汇总结果。
 *
 * <p>总振值和来源测点均取设备下风险等级最高的传感器；同等级时取总振值更大的传感器。</p>
 *
 * @param id 设备主键
 * @param equipCode 设备编码
 * @param equipName 设备名称
 * @param equipType 设备类型
 * @param installPosition 设备安装位置
 * @param status 汇总状态：NORMAL、WARNING、ALARM、DANGER 或 OFFLINE
 * @param totalVibration 当前关注测点的总振值，单位 mm/s；离线时为 null
 * @param sourceSensorCode 产生当前设备状态的传感器编码；离线时为 null
 * @param collectTime 该测点最近一次采集时间；离线时为 null
 */
public record EquipmentStatusSummary(
        Long id,
        String equipCode,
        String equipName,
        String equipType,
        String installPosition,
        String status,
        Double totalVibration,
        String sourceSensorCode,
        LocalDateTime collectTime
) {
}
