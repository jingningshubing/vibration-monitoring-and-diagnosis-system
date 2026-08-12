package com.cq.vibration.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 智能诊断页面的分页查询响应。 */
public record IntelligentDiagnosisPage(List<Record> records, long total, int page, int size) {
    /** 单条已保存的定时或测试诊断结果，包含设备/测点定位、诊断结论和维护建议。 */
    public record Record(Long id, Long batchId, LocalDateTime diagnosisTime, Long equipmentId, Long sensorId,
                         String equipmentCode, String sensorCode, String mountPosition, String diagnosisText,
                         double confidence, double stability, int windowCount, String maintenanceSuggestion, String diagnosisMode) { }
}
