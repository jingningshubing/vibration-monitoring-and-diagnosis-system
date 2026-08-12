package com.cq.vibration.dto;

import java.time.LocalDateTime;

/** 设备状态页展示的每台设备最新一条智能诊断结论。 */
public record EquipmentLatestDiagnosis(Long equipmentId, String diagnosisText, LocalDateTime diagnosisTime) { }
