package com.cq.vibration.dto;

/**
 * 一个三轴波形批次的计算特征。
 * 加速度 RMS 单位为 g；速度 RMS 与 totalVibration 单位为 mm/s。
 */
public record VibrationFeatures(
        double xAccelerationRms,
        double yAccelerationRms,
        double zAccelerationRms,
        double xVelocityRms,
        double yVelocityRms,
        double zVelocityRms,
        double totalVibration,
        String maxAxis) {
}
