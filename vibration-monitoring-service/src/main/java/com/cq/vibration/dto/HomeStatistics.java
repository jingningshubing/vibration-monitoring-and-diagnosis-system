package com.cq.vibration.dto;

import java.time.LocalDate;
import java.util.List;

/** 首页预警设备排序、振动趋势和智能诊断趋势的统计响应。 */
public record HomeStatistics(List<EquipmentCount> equipmentRanking, List<VibrationDailyCount> vibrationTrend, List<DiagnosisDailyCount> diagnosisTrend) {
    public record EquipmentCount(String equipmentCode, long normal, long warning, long alarm, long danger) { }
    public record VibrationDailyCount(LocalDate date, long normal, long warning, long alarm, long danger) { }
    public record DiagnosisDailyCount(LocalDate date, long healthy, long innerRace, long outerRace, long unclear) { }
}
