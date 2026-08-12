package com.cq.vibration.dto;

import java.time.LocalDateTime;

/**
 * 某一传感器最新批次的三轴速度有效值。
 *
 * @param sensorId 传感器主键
 * @param sensorCode 传感器编码
 * @param sensorName 传感器名称
 * @param mountPosition 安装位置
 * @param status 最新批次的状态
 * @param xVelocityRms X 轴速度有效值，单位 mm/s RMS
 * @param yVelocityRms Y 轴速度有效值，单位 mm/s RMS
 * @param zVelocityRms Z 轴速度有效值，单位 mm/s RMS
 * @param collectTime 最新采集时间
 */
public record SensorAxisVibration(
        Long sensorId,
        String sensorCode,
        String sensorName,
        String mountPosition,
        String status,
        Double xVelocityRms,
        Double yVelocityRms,
        Double zVelocityRms,
        LocalDateTime collectTime
) {
}
