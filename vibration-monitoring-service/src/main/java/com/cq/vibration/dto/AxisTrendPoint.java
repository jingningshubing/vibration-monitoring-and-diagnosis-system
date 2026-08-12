package com.cq.vibration.dto;

import java.time.LocalDateTime;

public record AxisTrendPoint(Long batchId, LocalDateTime time, Double x, Double y, Double z, String status,
                             String diagnosisText, Double diagnosisConfidence, Double diagnosisStability,
                             String maintenanceSuggestion) { }
