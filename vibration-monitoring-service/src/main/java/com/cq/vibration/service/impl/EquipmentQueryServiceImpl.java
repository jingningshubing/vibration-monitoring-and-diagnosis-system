package com.cq.vibration.service.impl;

import com.cq.vibration.dto.EquipmentStatusSummary;
import com.cq.vibration.dto.EquipmentSummary;
import com.cq.vibration.dto.SensorAxisVibration;
import com.cq.vibration.dto.EquipmentVibrationStatusStatistics;
import com.cq.vibration.dto.SensorVibrationStatusStatistics;
import com.cq.vibration.service.EquipmentQueryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

/**
 * 设备台账与设备当前状态的只读查询实现。
 */
@Service
public class EquipmentQueryServiceImpl implements EquipmentQueryService {

    private final JdbcTemplate jdbcTemplate;

    public EquipmentQueryServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询全部设备台账。
     *
     * <p>输入：无。输出：设备基础信息列表。</p>
     *
     * @return 设备列表
     */
    @Override
    public List<EquipmentSummary> findAll() {
        return jdbcTemplate.query("""
                        SELECT id, equip_code, equip_name, equip_type, install_position
                        FROM jzjc01_equipment
                        ORDER BY id
                        """,
                (resultSet, rowNum) -> new EquipmentSummary(
                        resultSet.getLong("id"),
                        resultSet.getString("equip_code"),
                        resultSet.getString("equip_name"),
                        resultSet.getString("equip_type"),
                        resultSet.getString("install_position")
                ));
    }

    /**
     * 查询已由采集轮次刷新完成的设备当前状态。
     *
     * <p>输入：无。输出：设备基础信息和状态缓存表的合并结果。</p>
     *
     * @return 设备状态汇总列表
     */
    @Override
    public List<EquipmentStatusSummary> findAllStatus() {
        String sql = """
                SELECT e.id,
                       e.equip_code,
                       e.equip_name,
                       e.equip_type,
                       e.install_position,
                       COALESCE(status.current_status, 'OFFLINE') AS current_status,
                       status.current_total_vibration,
                       status.source_sensor_code,
                       status.last_collect_time
                FROM jzjc01_equipment e
                LEFT JOIN jzjc01_equipment_current_status status ON status.equipment_id = e.id
                ORDER BY e.id
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNum) -> {
            Timestamp timestamp = resultSet.getTimestamp("last_collect_time");
            return new EquipmentStatusSummary(
                    resultSet.getLong("id"),
                    resultSet.getString("equip_code"),
                    resultSet.getString("equip_name"),
                    resultSet.getString("equip_type"),
                    resultSet.getString("install_position"),
                    resultSet.getString("current_status"),
                    resultSet.getObject("current_total_vibration", Double.class),
                    resultSet.getString("source_sensor_code"),
                    timestamp == null ? null : timestamp.toLocalDateTime()
            );
        });
    }

    /**
     * 查询指定设备传感器的最新三轴速度有效值。
     *
     * <p>输入：设备 ID。输出：每个传感器的最新 X、Y、Z 轴速度有效值，单位 mm/s RMS。</p>
     *
     * @param equipmentId 设备主键
     * @return 传感器三轴振动列表
     */
    @Override
    public List<SensorAxisVibration> findLatestSensorVibrations(Long equipmentId) {
        String sql = """
                SELECT sensor.id,
                       sensor.sensor_code,
                       sensor.sensor_name,
                       sensor.mount_position,
                       COALESCE(batch.detection_status, 'OFFLINE') AS status,
                       feature.x_velocity_rms,
                       feature.y_velocity_rms,
                       feature.z_velocity_rms,
                       batch.collect_time
                FROM jzjc01_sensor_device sensor
                LEFT JOIN jzjc01_waveform_batch batch ON batch.id = (
                    SELECT latest_batch.id
                    FROM jzjc01_waveform_batch latest_batch
                    WHERE latest_batch.point_id = sensor.id
                    ORDER BY latest_batch.collect_time DESC, latest_batch.id DESC
                    LIMIT 1
                )
                LEFT JOIN jzjc01_vibration_feature feature ON feature.batch_id = batch.id
                WHERE sensor.equipment_id = ?
                ORDER BY sensor.id
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNum) -> {
            Timestamp timestamp = resultSet.getTimestamp("collect_time");
            return new SensorAxisVibration(
                    resultSet.getLong("id"),
                    resultSet.getString("sensor_code"),
                    resultSet.getString("sensor_name"),
                    resultSet.getString("mount_position"),
                    resultSet.getString("status"),
                    resultSet.getObject("x_velocity_rms", Double.class),
                    resultSet.getObject("y_velocity_rms", Double.class),
                    resultSet.getObject("z_velocity_rms", Double.class),
                    timestamp == null ? null : timestamp.toLocalDateTime()
            );
        }, equipmentId);
    }

    /**
     * 统计设备的总振值状态分布。
     *
     * <p>输入：无。输出：四档状态数量。状态表不存在记录或为 OFFLINE 的设备按 NORMAL 统计。</p>
     *
     * @return 总振值状态统计
     */
    @Override
    public EquipmentVibrationStatusStatistics getVibrationStatusStatistics() {
        String sql = """
                SELECT SUM(CASE WHEN effective_status = 'NORMAL' THEN 1 ELSE 0 END) AS normal_count,
                       SUM(CASE WHEN effective_status = 'WARNING' THEN 1 ELSE 0 END) AS warning_count,
                       SUM(CASE WHEN effective_status = 'ALARM' THEN 1 ELSE 0 END) AS alarm_count,
                       SUM(CASE WHEN effective_status = 'DANGER' THEN 1 ELSE 0 END) AS danger_count,
                       SUM(CASE WHEN effective_status = 'OFFLINE' THEN 1 ELSE 0 END) AS offline_count,
                       COUNT(*) AS total_count
                FROM (
                    SELECT CASE
                               WHEN equipment_status.current_status IS NULL THEN 'OFFLINE'
                               ELSE equipment_status.current_status
                           END AS effective_status
                    FROM jzjc01_equipment equipment
                    LEFT JOIN jzjc01_equipment_current_status equipment_status
                           ON equipment_status.equipment_id = equipment.id
                ) status_rows
                """;

        return jdbcTemplate.queryForObject(sql, (resultSet, rowNum) ->
                new EquipmentVibrationStatusStatistics(
                        resultSet.getLong("normal_count"),
                        resultSet.getLong("warning_count"),
                        resultSet.getLong("alarm_count"),
                        resultSet.getLong("danger_count"),
                        resultSet.getLong("offline_count"),
                        resultSet.getLong("total_count")
                ));
    }

    /** {@inheritDoc} */
    @Override
    public SensorVibrationStatusStatistics getSensorStatusStatistics() {
        return jdbcTemplate.queryForObject("""
                SELECT SUM(CASE WHEN effective_status='NORMAL' THEN 1 ELSE 0 END) AS normal_count,
                       SUM(CASE WHEN effective_status='WARNING' THEN 1 ELSE 0 END) AS warning_count,
                       SUM(CASE WHEN effective_status='ALARM' THEN 1 ELSE 0 END) AS alarm_count,
                       SUM(CASE WHEN effective_status='DANGER' THEN 1 ELSE 0 END) AS danger_count,
                       SUM(CASE WHEN effective_status='OFFLINE' THEN 1 ELSE 0 END) AS offline_count,
                       COUNT(*) AS total_count
                FROM (SELECT COALESCE(current_status,'OFFLINE') AS effective_status FROM jzjc01_sensor_device) status_rows
                """, (rs, row) -> new SensorVibrationStatusStatistics(rs.getLong("normal_count"), rs.getLong("warning_count"), rs.getLong("alarm_count"), rs.getLong("danger_count"), rs.getLong("offline_count"), rs.getLong("total_count")));
    }
}
