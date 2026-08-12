package com.cq.vibration.dto;

import java.util.List;

/** 振动预警顶部筛选项。 */
public record VibrationAlarmOptions(List<Equipment> equipment, List<Sensor> sensors) {
    /** 设备下拉选项。 */
    public record Equipment(Long id, String code, String name) { }
    /** 传感器下拉选项。 */
    public record Sensor(Long id, Long equipmentId, String code, String name, String mountPosition,
                         Double warningThreshold, Double alarmThreshold, Double dangerThreshold) { }
}
