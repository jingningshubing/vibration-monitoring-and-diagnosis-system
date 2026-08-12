package com.cq.vibration.dto;

import java.time.LocalDate;
import java.util.List;

/** 智能诊断页面的圆环和按天趋势统计响应。 */
public record IntelligentDiagnosisStatistics(Counts equipment, Counts records, List<DailyCount> dailyTrend, List<EquipmentCount> equipmentRanking) {
    /** 按诊断结论归类的数量。 */
    public record Counts(long healthy, long innerRace, long outerRace, long unclear) { }
    /** 单个自然日内四类诊断结论的次数，用于近 30 天趋势图。 */
    public record DailyCount(LocalDate date, long healthy, long innerRace, long outerRace, long unclear) { }
    /** 单台设备按诊断结论汇总的次数，用于设备排行卡片。 */
    public record EquipmentCount(String equipmentCode, String equipmentName, long healthy, long innerRace, long outerRace, long unclear) { }
}
