package com.cq.vibration.dto;

/** 当前筛选条件下的报警设备数和报警记录次数。 */
import java.time.LocalDate;
import java.util.List;

public record VibrationAlarmStatistics(LevelCounts equipment, LevelCounts records, List<DailyCount> dailyTrend, List<EquipmentCount> equipmentRanking) {
    /** 四档状态的数量分布。 */
    public record LevelCounts(long normal, long warning, long alarm, long danger) { }
    /** 某一自然日的报警等级次数。 */
    public record DailyCount(LocalDate date, long warning, long alarm, long danger) { }
    /** 单台设备按等级统计的报警次数。 */
    public record EquipmentCount(String equipmentCode, String equipmentName, long warning, long alarm, long danger) { }
}
