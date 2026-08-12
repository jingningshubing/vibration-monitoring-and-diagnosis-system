package com.cq.vibration.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 振动预警表格分页响应。 */
public record VibrationAlarmPage(List<Record> records, long total, int page, int size) {
    /** 左侧预警表格的一行数据。 */
    public record Record(Long id, Long batchId, LocalDateTime alarmTime, Long equipmentId, Long sensorId,
                         String equipmentCode, String sensorCode, String mountPosition, String alarmLevel, String message) { }
}
