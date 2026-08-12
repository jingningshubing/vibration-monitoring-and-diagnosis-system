package com.cq.vibration.dto;

/** 单个波形批次的 ONNX 轴承状态辅助诊断响应。 */
public record BearingDiagnosis(String label, String diagnosis, double confidence, double stability,
                               int windowCount, Probabilities probabilities, String description) {
    /** 三种分类的平均概率。 */
    public record Probabilities(double healthy, double innerRace, double outerRace) { }
}
