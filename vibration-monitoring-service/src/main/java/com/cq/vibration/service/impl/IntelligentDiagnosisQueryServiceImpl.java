package com.cq.vibration.service.impl;

import com.cq.vibration.dto.IntelligentDiagnosisPage;
import com.cq.vibration.dto.IntelligentDiagnosisStatistics;
import com.cq.vibration.dto.EquipmentLatestDiagnosis;
import com.cq.vibration.service.IntelligentDiagnosisQueryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 从轴承辅助诊断结果表联合设备与传感器台账读取分页数据。 */
@Service
public class IntelligentDiagnosisQueryServiceImpl implements IntelligentDiagnosisQueryService {
    private static final String FROM_SQL = " FROM jzjc01_bearing_diagnosis diagnosis JOIN jzjc01_sensor_device sensor ON sensor.id=diagnosis.sensor_id LEFT JOIN jzjc01_equipment equipment ON equipment.id=sensor.equipment_id ";
    private final JdbcTemplate jdbcTemplate;
    public IntelligentDiagnosisQueryServiceImpl(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    /** {@inheritDoc} */
    @Override
    public IntelligentDiagnosisPage query(Long equipmentId, Long sensorId, LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page; int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Filter filter = filter(equipmentId, sensorId, startDate, endDate);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + FROM_SQL + filter.sql, filter.args.toArray(), Long.class);
        List<Object> args = new ArrayList<>(filter.args); args.add(safeSize); args.add((safePage - 1) * safeSize);
        List<IntelligentDiagnosisPage.Record> records = jdbcTemplate.query("""
                SELECT diagnosis.id,diagnosis.latest_batch_id,diagnosis.diagnosis_time,sensor.equipment_id,sensor.id AS sensor_id,equipment.equip_code,sensor.sensor_code,sensor.mount_position,
                       diagnosis.diagnosis_text,diagnosis.confidence,diagnosis.stability,diagnosis.window_count,diagnosis.description,diagnosis.diagnosis_mode
                """ + FROM_SQL + filter.sql + " ORDER BY diagnosis.diagnosis_time DESC,diagnosis.id DESC LIMIT ? OFFSET ?", (rs, row) ->
                new IntelligentDiagnosisPage.Record(rs.getLong("id"), rs.getLong("latest_batch_id"), rs.getTimestamp("diagnosis_time").toLocalDateTime(),
                        rs.getObject("equipment_id", Long.class), rs.getLong("sensor_id"), rs.getString("equip_code"), rs.getString("sensor_code"), rs.getString("mount_position"), rs.getString("diagnosis_text"),
                        rs.getDouble("confidence"), rs.getDouble("stability"), rs.getInt("window_count"), rs.getString("description"), rs.getString("diagnosis_mode")), args.toArray());
        return new IntelligentDiagnosisPage(records, total, safePage, safeSize);
    }
    /** {@inheritDoc} */
    @Override
    public IntelligentDiagnosisStatistics statistics(Long equipmentId, Long sensorId, LocalDate startDate, LocalDate endDate) {
        Filter filter = filter(equipmentId, sensorId, startDate, endDate);
        IntelligentDiagnosisStatistics.Counts records = counts("SELECT " + countsSql() + FROM_SQL + filter.sql, filter.args);
        IntelligentDiagnosisStatistics.Counts equipment = counts("""
                SELECT %s FROM (
                    SELECT diagnosis.diagnosis_text, ROW_NUMBER() OVER (PARTITION BY equipment.id ORDER BY diagnosis.diagnosis_time DESC, diagnosis.id DESC) AS rn
                """.formatted(countsSql()) + FROM_SQL + filter.sql + ") latest WHERE rn=1", filter.args);
        LocalDate lastDate = endDate == null ? LocalDate.now() : endDate;
        LocalDate firstDate = lastDate.minusDays(29);
        LocalDate trendStartDate = startDate == null || startDate.isBefore(firstDate) ? firstDate : startDate;
        Filter trendFilter = filter(equipmentId, sensorId, trendStartDate, lastDate);
        java.util.Map<LocalDate, IntelligentDiagnosisStatistics.DailyCount> dailyMap = new java.util.HashMap<>();
        jdbcTemplate.query("""
                SELECT DATE(diagnosis.diagnosis_time) AS diagnosis_date,
                       COALESCE(SUM(diagnosis.diagnosis_text='健康'),0) AS healthy_count,
                       COALESCE(SUM(diagnosis.diagnosis_text='轴承内圈故障'),0) AS inner_count,
                       COALESCE(SUM(diagnosis.diagnosis_text='轴承外圈故障'),0) AS outer_count,
                       COALESCE(SUM(diagnosis.diagnosis_text='当前数据特征不明确'),0) AS unclear_count
                """ + FROM_SQL + trendFilter.sql + " GROUP BY DATE(diagnosis.diagnosis_time)", (rs, row) -> {
            LocalDate date = rs.getDate("diagnosis_date").toLocalDate();
            dailyMap.put(date, new IntelligentDiagnosisStatistics.DailyCount(date, rs.getLong("healthy_count"), rs.getLong("inner_count"), rs.getLong("outer_count"), rs.getLong("unclear_count")));
            return null;
        }, trendFilter.args.toArray());
        List<IntelligentDiagnosisStatistics.DailyCount> trend = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            trend.add(dailyMap.getOrDefault(date, new IntelligentDiagnosisStatistics.DailyCount(date, 0, 0, 0, 0)));
        }
        StringBuilder rankingJoin = new StringBuilder(" LEFT JOIN jzjc01_bearing_diagnosis diagnosis ON diagnosis.sensor_id=sensor.id");
        List<Object> rankingArgs = new ArrayList<>();
        if (startDate != null) { rankingJoin.append(" AND diagnosis.diagnosis_time>=?"); rankingArgs.add(Timestamp.valueOf(startDate.atStartOfDay())); }
        if (endDate != null) { rankingJoin.append(" AND diagnosis.diagnosis_time<?"); rankingArgs.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay())); }
        StringBuilder rankingWhere = new StringBuilder(" WHERE 1=1");
        if (equipmentId != null) { rankingWhere.append(" AND equipment.id=?"); rankingArgs.add(equipmentId); }
        if (sensorId != null) { rankingWhere.append(" AND sensor.id=?"); rankingArgs.add(sensorId); }
        List<IntelligentDiagnosisStatistics.EquipmentCount> ranking = jdbcTemplate.query("""
                SELECT equipment.equip_code,equipment.equip_name,
                       COALESCE(SUM(diagnosis.diagnosis_text='健康'),0) AS healthy_count,
                       COALESCE(SUM(diagnosis.diagnosis_text='轴承内圈故障'),0) AS inner_count,
                       COALESCE(SUM(diagnosis.diagnosis_text='轴承外圈故障'),0) AS outer_count,
                       COALESCE(SUM(diagnosis.diagnosis_text='当前数据特征不明确'),0) AS unclear_count
                FROM jzjc01_equipment equipment LEFT JOIN jzjc01_sensor_device sensor ON sensor.equipment_id=equipment.id
                """ + rankingJoin + rankingWhere + " GROUP BY equipment.id,equipment.equip_code,equipment.equip_name ORDER BY (healthy_count+inner_count+outer_count+unclear_count) DESC,equipment.equip_code", (rs, row) ->
                new IntelligentDiagnosisStatistics.EquipmentCount(rs.getString("equip_code"), rs.getString("equip_name"), rs.getLong("healthy_count"), rs.getLong("inner_count"), rs.getLong("outer_count"), rs.getLong("unclear_count")), rankingArgs.toArray());
        return new IntelligentDiagnosisStatistics(equipment, records, trend, ranking);
    }
    /** {@inheritDoc} */
    @Override
    public List<EquipmentLatestDiagnosis> latestByEquipment() {
        return jdbcTemplate.query("""
                SELECT equipment_id,diagnosis_text,diagnosis_time FROM (
                    SELECT equipment_id,diagnosis_text,diagnosis_time,
                           ROW_NUMBER() OVER (
                               PARTITION BY equipment_id
                               ORDER BY CASE diagnosis_text
                                   WHEN '轴承内圈故障' THEN 2
                                   WHEN '轴承外圈故障' THEN 2
                                   WHEN '健康' THEN 1
                                   ELSE 0 END DESC,
                                   diagnosis_time DESC, id DESC
                           ) AS rn
                    FROM (
                        SELECT sensor.equipment_id, diagnosis.id, diagnosis.diagnosis_text, diagnosis.diagnosis_time,
                               ROW_NUMBER() OVER (
                                   PARTITION BY diagnosis.sensor_id
                                   ORDER BY diagnosis.diagnosis_time DESC, diagnosis.id DESC
                               ) AS sensor_rn
                        FROM jzjc01_bearing_diagnosis diagnosis
                        JOIN jzjc01_sensor_device sensor ON sensor.id=diagnosis.sensor_id
                        WHERE diagnosis.diagnosis_text IN ('健康', '轴承内圈故障', '轴承外圈故障')
                    ) sensor_latest
                    WHERE sensor_rn=1
                ) equipment_current WHERE rn=1
                """, (rs, row) -> new EquipmentLatestDiagnosis(rs.getLong("equipment_id"), rs.getString("diagnosis_text"), rs.getTimestamp("diagnosis_time").toLocalDateTime()));
    }
    /** 聚合四类结论，并将空结果按 0 返回。 */
    private IntelligentDiagnosisStatistics.Counts counts(String sql, List<Object> args) {
        return jdbcTemplate.queryForObject(sql, (rs, row) -> new IntelligentDiagnosisStatistics.Counts(rs.getLong("healthy_count"), rs.getLong("inner_count"), rs.getLong("outer_count"), rs.getLong("unclear_count")), args.toArray());
    }
    private String countsSql() { return "COALESCE(SUM(diagnosis_text='健康'),0) AS healthy_count, COALESCE(SUM(diagnosis_text='轴承内圈故障'),0) AS inner_count, COALESCE(SUM(diagnosis_text='轴承外圈故障'),0) AS outer_count, COALESCE(SUM(diagnosis_text='当前数据特征不明确'),0) AS unclear_count"; }
    /** 将可选筛选项转为参数化 SQL，供总数与分页列表共用。 */
    private Filter filter(Long equipmentId, Long sensorId, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1"); List<Object> args = new ArrayList<>();
        if (equipmentId != null) { sql.append(" AND sensor.equipment_id=?"); args.add(equipmentId); }
        if (sensorId != null) { sql.append(" AND diagnosis.sensor_id=?"); args.add(sensorId); }
        if (startDate != null) { sql.append(" AND diagnosis.diagnosis_time>=?"); args.add(Timestamp.valueOf(startDate.atStartOfDay())); }
        if (endDate != null) { sql.append(" AND diagnosis.diagnosis_time<?"); args.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay())); }
        return new Filter(sql.toString(), args);
    }
    private record Filter(String sql, List<Object> args) { }
}
