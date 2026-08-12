package com.cq.vibration.dto;

/**
 * 一次采集得到的三轴原始加速度波形。
 *
 * @param x X 轴加速度采样点，长度等于 sampleRate × 采集时长
 * @param y Y 轴加速度采样点，长度等于 sampleRate × 采集时长
 * @param z Z 轴加速度采样点，长度等于 sampleRate × 采集时长
 * @param sampleRate 采样率，单位 Hz
 * @param sourceFile 原始 CSV 文件名
 * @param startRow 此批数据在 CSV 中的起始行号
 */
public record TriAxisWaveform(
        double[] x,
        double[] y,
        double[] z,
        int sampleRate,
        String sourceFile,
        int startRow) {
}
