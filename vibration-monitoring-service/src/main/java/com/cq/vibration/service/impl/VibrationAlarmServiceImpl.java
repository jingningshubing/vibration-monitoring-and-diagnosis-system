package com.cq.vibration.service.impl;

import com.cq.vibration.dto.VibrationAlarmOptions;
import com.cq.vibration.dto.VibrationAlarmPage;
import com.cq.vibration.dto.VibrationAlarmStatistics;
import com.cq.vibration.service.VibrationAlarmService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 使用报警记录、传感器和设备台账实现振动预警查询。 */
@Service
public class VibrationAlarmServiceImpl implements VibrationAlarmService {
    private static final String FROM_SQL = """
            FROM jzjc01_alarm_record alarm
            JOIN jzjc01_sensor_device sensor ON sensor.id = alarm.point_id
            LEFT JOIN jzjc01_equipment equipment ON equipment.id = sensor.equipment_id
            """;
    private final JdbcTemplate jdbcTemplate;

    public VibrationAlarmServiceImpl(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    /** {@inheritDoc} */
    @Override
    public VibrationAlarmOptions getOptions() {
        List<VibrationAlarmOptions.Equipment> equipment = jdbcTemplate.query("""
                SELECT id, equip_code, equip_name FROM jzjc01_equipment ORDER BY equip_code
                """, (rs, row) -> new VibrationAlarmOptions.Equipment(rs.getLong("id"), rs.getString("equip_code"), rs.getString("equip_name")));
        List<VibrationAlarmOptions.Sensor> sensors = jdbcTemplate.query("""
                SELECT id, equipment_id, sensor_code, sensor_name, mount_position,
                       warning_threshold, alarm_threshold, danger_threshold
                FROM jzjc01_sensor_device ORDER BY sensor_code
                """, (rs, row) -> new VibrationAlarmOptions.Sensor(rs.getLong("id"), rs.getObject("equipment_id", Long.class),
                rs.getString("sensor_code"), rs.getString("sensor_name"), rs.getString("mount_position"),
                rs.getObject("warning_threshold", Double.class), rs.getObject("alarm_threshold", Double.class),
                rs.getObject("danger_threshold", Double.class)));
        return new VibrationAlarmOptions(equipment, sensors);
    }

    /** {@inheritDoc} */
    @Override
    public VibrationAlarmPage query(Long equipmentId, Long sensorId, String alarmLevel,
                                    LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Filter filter = filter(equipmentId, sensorId, alarmLevel, startDate, endDate);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + FROM_SQL + filter.sql, filter.args.toArray(), Long.class);
        List<Object> arguments = new ArrayList<>(filter.args);
        arguments.add(safeSize);
        arguments.add((safePage - 1) * safeSize);
        List<VibrationAlarmPage.Record> records = jdbcTemplate.query("""
                SELECT alarm.id, alarm.batch_id, alarm.alarm_time, sensor.equipment_id, sensor.id AS sensor_id, equipment.equip_code,
                       sensor.sensor_code, sensor.mount_position, alarm.alarm_level, alarm.message
                """ + FROM_SQL + filter.sql + " ORDER BY alarm.alarm_time DESC, alarm.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new VibrationAlarmPage.Record(rs.getLong("id"), rs.getLong("batch_id"),
                        rs.getTimestamp("alarm_time").toLocalDateTime(), rs.getObject("equipment_id", Long.class), rs.getLong("sensor_id"), rs.getString("equip_code"),
                        rs.getString("sensor_code"), rs.getString("mount_position"), rs.getString("alarm_level"), rs.getString("message")),
                arguments.toArray());
        return new VibrationAlarmPage(records, total, safePage, safeSize);
    }

    /** {@inheritDoc} */
    @Override
    public VibrationAlarmStatistics statistics(Long equipmentId, Long sensorId, String alarmLevel, LocalDate startDate, LocalDate endDate) {
        Filter filter = filter(equipmentId, sensorId, alarmLevel, startDate, endDate);
        VibrationAlarmStatistics.LevelCounts records = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(alarm.alarm_level='NORMAL'),0) AS normal_count, COALESCE(SUM(alarm.alarm_level='WARNING'),0) AS warning_count,
                       COALESCE(SUM(alarm.alarm_level='ALARM'),0) AS alarm_count, COALESCE(SUM(alarm.alarm_level='DANGER'),0) AS danger_count
                """ + FROM_SQL + filter.sql, (rs, row) -> new VibrationAlarmStatistics.LevelCounts(rs.getLong("normal_count"),rs.getLong("warning_count"),rs.getLong("alarm_count"),rs.getLong("danger_count")), filter.args.toArray());
        StringBuilder conditions = new StringBuilder(); java.util.List<Object> args = new java.util.ArrayList<>();
        if (startDate != null) { conditions.append(" AND alarm.alarm_time >= ?"); args.add(Timestamp.valueOf(startDate.atStartOfDay())); }
        if (endDate != null) { conditions.append(" AND alarm.alarm_time < ?"); args.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay())); }
        if (sensorId != null) { conditions.append(" AND alarm.point_id = ?"); args.add(sensorId); }
        // 设备状态分布与表格、趋势、排行使用同一状态等级筛选口径。
        if (alarmLevel != null && !alarmLevel.isBlank()) { conditions.append(" AND alarm.alarm_level = ?"); args.add(alarmLevel); }
        VibrationAlarmStatistics.LevelCounts equipment = jdbcTemplate.queryForObject("""
                SELECT SUM(level_value=0) AS normal_count, SUM(level_value=1) AS warning_count, SUM(level_value=2) AS alarm_count, SUM(level_value=3) AS danger_count
                FROM (SELECT equipment.id, COALESCE(MAX(CASE alarm.alarm_level WHEN 'DANGER' THEN 3 WHEN 'ALARM' THEN 2 WHEN 'WARNING' THEN 1 ELSE 0 END),0) AS level_value
                      FROM jzjc01_equipment equipment LEFT JOIN jzjc01_sensor_device sensor ON sensor.equipment_id=equipment.id
                      LEFT JOIN jzjc01_alarm_record alarm ON alarm.point_id=sensor.id
                """ + conditions + " WHERE 1=1" + (equipmentId == null ? "" : " AND equipment.id = ?") + " GROUP BY equipment.id) levels",
                (rs,row) -> new VibrationAlarmStatistics.LevelCounts(rs.getLong("normal_count"),rs.getLong("warning_count"),rs.getLong("alarm_count"),rs.getLong("danger_count")), append(args, equipmentId));
        // 趋势图固定展示截至筛选结束日（未传时为当天）的近 30 天，查询时同步收窄时间范围，避免扫描无展示用途的历史报警。
        LocalDate lastDate = endDate == null ? LocalDate.now() : endDate;
        LocalDate firstDate = lastDate.minusDays(29);
        LocalDate trendStartDate = startDate == null || startDate.isBefore(firstDate) ? firstDate : startDate;
        Filter trendFilter = filter(equipmentId, sensorId, alarmLevel, trendStartDate, lastDate);
        java.util.Map<LocalDate, VibrationAlarmStatistics.DailyCount> dailyMap = new java.util.HashMap<>();
        jdbcTemplate.query("""
                SELECT DATE(alarm.alarm_time) AS alarm_date, SUM(alarm.alarm_level='WARNING') AS warning_count,
                       SUM(alarm.alarm_level='ALARM') AS alarm_count, SUM(alarm.alarm_level='DANGER') AS danger_count
                """ + FROM_SQL + trendFilter.sql + " GROUP BY DATE(alarm.alarm_time)", (rs, row) -> {
            LocalDate date = rs.getDate("alarm_date").toLocalDate();
            dailyMap.put(date, new VibrationAlarmStatistics.DailyCount(date, rs.getLong("warning_count"), rs.getLong("alarm_count"), rs.getLong("danger_count"))); return null;
        }, trendFilter.args.toArray());
        java.util.List<VibrationAlarmStatistics.DailyCount> trend = new java.util.ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) trend.add(dailyMap.getOrDefault(date, new VibrationAlarmStatistics.DailyCount(date, 0, 0, 0)));
        StringBuilder rankConditions = new StringBuilder(); java.util.List<Object> rankArgs = new java.util.ArrayList<>();
        if (startDate != null) { rankConditions.append(" AND alarm.alarm_time >= ?"); rankArgs.add(Timestamp.valueOf(startDate.atStartOfDay())); }
        if (endDate != null) { rankConditions.append(" AND alarm.alarm_time < ?"); rankArgs.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay())); }
        if (sensorId != null) { rankConditions.append(" AND alarm.point_id = ?"); rankArgs.add(sensorId); }
        if (alarmLevel != null && !alarmLevel.isBlank()) { rankConditions.append(" AND alarm.alarm_level = ?"); rankArgs.add(alarmLevel); }
        if (equipmentId != null) rankArgs.add(equipmentId);
        java.util.List<VibrationAlarmStatistics.EquipmentCount> ranking = jdbcTemplate.query("""
                SELECT equipment.equip_code, equipment.equip_name, COALESCE(SUM(alarm.alarm_level='WARNING'),0) AS warning_count,
                       COALESCE(SUM(alarm.alarm_level='ALARM'),0) AS alarm_count, COALESCE(SUM(alarm.alarm_level='DANGER'),0) AS danger_count
                FROM jzjc01_equipment equipment LEFT JOIN jzjc01_sensor_device sensor ON sensor.equipment_id=equipment.id
                LEFT JOIN jzjc01_alarm_record alarm ON alarm.point_id=sensor.id
                """ + rankConditions + " WHERE 1=1" + (equipmentId == null ? "" : " AND equipment.id=?") + " GROUP BY equipment.id,equipment.equip_code,equipment.equip_name ORDER BY (warning_count+alarm_count+danger_count) DESC,equipment.equip_code",
                (rs,row) -> new VibrationAlarmStatistics.EquipmentCount(rs.getString("equip_code"), rs.getString("equip_name"),rs.getLong("warning_count"),rs.getLong("alarm_count"),rs.getLong("danger_count")), rankArgs.toArray());
        return new VibrationAlarmStatistics(equipment, records, trend, ranking);
    }

    /** 向已有 SQL 参数集合追加可选设备参数。 */
    private Object[] append(java.util.List<Object> values, Object optionalValue) { java.util.List<Object> copy = new java.util.ArrayList<>(values); if (optionalValue != null) copy.add(optionalValue); return copy.toArray(); }

    /** 将可选条件转为列表与总数共用的 SQL 条件。 */
    private Filter filter(Long equipmentId, Long sensorId, String alarmLevel, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (equipmentId != null) { sql.append(" AND sensor.equipment_id = ?"); args.add(equipmentId); }
        if (sensorId != null) { sql.append(" AND alarm.point_id = ?"); args.add(sensorId); }
        if (alarmLevel != null && !alarmLevel.isBlank()) { sql.append(" AND alarm.alarm_level = ?"); args.add(alarmLevel); }
        if (startDate != null) { sql.append(" AND alarm.alarm_time >= ?"); args.add(Timestamp.valueOf(startDate.atStartOfDay())); }
        if (endDate != null) { sql.append(" AND alarm.alarm_time < ?"); args.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay())); }
        return new Filter(sql.toString(), args);
    }

    /** 查询条件与参数组合。 */
    private record Filter(String sql, List<Object> args) { }
}
