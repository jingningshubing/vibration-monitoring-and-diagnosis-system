package com.cq.vibration.service.impl;

import com.cq.vibration.dto.HomeStatistics;
import com.cq.vibration.service.HomeStatisticsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 基于采集批次和已完成诊断记录汇总首页统计。 */
@Service
public class HomeStatisticsServiceImpl implements HomeStatisticsService {
    private final JdbcTemplate jdbc;
    public HomeStatisticsServiceImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** {@inheritDoc} */
    @Override public HomeStatistics statistics() {
        LocalDate end = LocalDate.now(); LocalDate start = end.minusDays(29); Timestamp startTime = Timestamp.valueOf(start.atStartOfDay());
        List<HomeStatistics.EquipmentCount> ranking = jdbc.query("""
                SELECT e.equip_code,COALESCE(SUM(b.detection_status='NORMAL'),0) normal_count,COALESCE(SUM(b.detection_status='WARNING'),0) warning_count,
                       COALESCE(SUM(b.detection_status='ALARM'),0) alarm_count,COALESCE(SUM(b.detection_status='DANGER'),0) danger_count
                FROM jzjc01_equipment e LEFT JOIN jzjc01_sensor_device s ON s.equipment_id=e.id
                LEFT JOIN jzjc01_waveform_batch b ON b.point_id=s.id AND b.collect_time>=?
                GROUP BY e.id,e.equip_code ORDER BY (warning_count+alarm_count+danger_count) DESC,normal_count DESC,e.equip_code
                """, (rs,row)->new HomeStatistics.EquipmentCount(rs.getString("equip_code"),rs.getLong("normal_count"),rs.getLong("warning_count"),rs.getLong("alarm_count"),rs.getLong("danger_count")), startTime);
        Map<LocalDate, HomeStatistics.VibrationDailyCount> vibration = new HashMap<>();
        jdbc.query("SELECT DATE(collect_time) day,COALESCE(SUM(detection_status='NORMAL'),0) normal_count,COALESCE(SUM(detection_status='WARNING'),0) warning_count,COALESCE(SUM(detection_status='ALARM'),0) alarm_count,COALESCE(SUM(detection_status='DANGER'),0) danger_count FROM jzjc01_waveform_batch WHERE collect_time>=? GROUP BY DATE(collect_time)", rs->{ LocalDate day=rs.getDate("day").toLocalDate(); vibration.put(day,new HomeStatistics.VibrationDailyCount(day,rs.getLong("normal_count"),rs.getLong("warning_count"),rs.getLong("alarm_count"),rs.getLong("danger_count"))); }, startTime);
        Map<LocalDate, HomeStatistics.DiagnosisDailyCount> diagnosis = new HashMap<>();
        jdbc.query("SELECT DATE(diagnosis_time) day,COALESCE(SUM(diagnosis_text='健康'),0) healthy_count,COALESCE(SUM(diagnosis_text='轴承内圈故障'),0) inner_count,COALESCE(SUM(diagnosis_text='轴承外圈故障'),0) outer_count,COALESCE(SUM(diagnosis_text='当前数据特征不明确'),0) unclear_count FROM jzjc01_bearing_diagnosis WHERE diagnosis_time>=? GROUP BY DATE(diagnosis_time)", rs->{ LocalDate day=rs.getDate("day").toLocalDate(); diagnosis.put(day,new HomeStatistics.DiagnosisDailyCount(day,rs.getLong("healthy_count"),rs.getLong("inner_count"),rs.getLong("outer_count"),rs.getLong("unclear_count"))); }, startTime);
        List<HomeStatistics.VibrationDailyCount> vibrationTrend=new ArrayList<>(); List<HomeStatistics.DiagnosisDailyCount> diagnosisTrend=new ArrayList<>();
        for(LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)){ vibrationTrend.add(vibration.getOrDefault(day,new HomeStatistics.VibrationDailyCount(day,0,0,0,0))); diagnosisTrend.add(diagnosis.getOrDefault(day,new HomeStatistics.DiagnosisDailyCount(day,0,0,0,0))); }
        return new HomeStatistics(ranking,vibrationTrend,diagnosisTrend);
    }
}
