package com.cq.vibration.dto;

/**
 * 基于设备总振值阈值状态的数量统计。
 *
 * @param normal 正常设备数
 * @param warning 预警设备数
 * @param alarm 报警设备数
 * @param danger 危险设备数
 * @param offline 离线或尚未初始化状态的设备数
 * @param total 设备总数
 */
public record EquipmentVibrationStatusStatistics(
        long normal,
        long warning,
        long alarm,
        long danger,
        long offline,
        long total
) {
}
