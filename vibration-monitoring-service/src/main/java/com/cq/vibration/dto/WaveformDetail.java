package com.cq.vibration.dto;

import java.time.LocalDateTime;

/** 波形详情弹窗的后端响应，包含同一采集批次的时域和频域数据。 */
public record WaveformDetail(Long batchId, LocalDateTime collectTime, String equipmentCode, String equipmentName,
                             String sensorCode, String mountPosition, int sampleRate, int sampleCount,
                             AxisWaveform acceleration, AxisWaveform velocity,
                             AxisSpectrum accelerationSpectrum, AxisSpectrum velocitySpectrum) {
    /** 三轴时域波形；timeSeconds 与每轴数组下标一一对应。 */
    public record AxisWaveform(double[] timeSeconds, double[] x, double[] y, double[] z, String unit) { }
    /** 三轴单边 RMS 频谱；frequencyHz 与每轴数组下标一一对应。 */
    public record AxisSpectrum(double[] frequencyHz, double[] x, double[] y, double[] z, String unit) { }
}
