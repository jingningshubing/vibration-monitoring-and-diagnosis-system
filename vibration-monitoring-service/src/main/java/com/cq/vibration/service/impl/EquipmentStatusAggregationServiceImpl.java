package com.cq.vibration.service.impl;

import com.cq.vibration.service.EquipmentStatusAggregationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 将传感器最新批次汇总为设备当前状态的实现。
 *
 * <p>设备状态优先级为 DANGER、ALARM、WARNING、NORMAL、OFFLINE。
 * 同一等级下，选择总振值更大的传感器作为设备卡片的展示来源。</p>
 */
@Service
public class EquipmentStatusAggregationServiceImpl implements EquipmentStatusAggregationService {

    private final JdbcTemplate jdbcTemplate;

    public EquipmentStatusAggregationServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 汇总所有设备并写入当前状态缓存表。
     *
     * <p>输入：本轮采集时间。输出：每台设备一条当前状态记录。</p>
     *
     * @param collectTime 本轮定时采集时间
     */
    @Override
    public void refreshAll(LocalDateTime collectTime) {
        Map<Integer, MutableEquipmentStatus> grouped = new LinkedHashMap<>();

        jdbcTemplate.query(latestSensorBatchSql(), resultSet -> {
            int equipmentId = resultSet.getInt("equipment_id");
            MutableEquipmentStatus summary = grouped.computeIfAbsent(
                    equipmentId,
                    MutableEquipmentStatus::new
            );

            String sensorCode = resultSet.getString("sensor_code");
            if (sensorCode == null) {
                return;
            }

            Object sensorIdValue = resultSet.getObject("sensor_id");
            Integer sensorId = sensorIdValue == null ? null : ((Number) sensorIdValue).intValue();
            Object vibrationValue = resultSet.getObject("total_vibration");
            Double totalVibration = vibrationValue == null ? null : ((Number) vibrationValue).doubleValue();
            Timestamp timestamp = resultSet.getTimestamp("collect_time");

            summary.accept(
                    normalizeStatus(resultSet.getString("detection_status")),
                    sensorId,
                    sensorCode,
                    totalVibration,
                    timestamp == null ? null : timestamp.toLocalDateTime()
            );
        });

        String upsertSql = """
                INSERT INTO jzjc01_equipment_current_status(
                    equipment_id,
                    current_status,
                    current_total_vibration,
                    source_sensor_id,
                    source_sensor_code,
                    last_collect_time
                ) VALUES(?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_status = VALUES(current_status),
                    current_total_vibration = VALUES(current_total_vibration),
                    source_sensor_id = VALUES(source_sensor_id),
                    source_sensor_code = VALUES(source_sensor_code),
                    last_collect_time = VALUES(last_collect_time)
                """;

        for (MutableEquipmentStatus summary : grouped.values()) {
            jdbcTemplate.update(
                    upsertSql,
                    summary.equipmentId,
                    summary.status,
                    summary.isOffline() ? null : summary.totalVibration,
                    summary.isOffline() ? null : summary.sourceSensorId,
                    summary.isOffline() ? null : summary.sourceSensorCode,
                    summary.isOffline() ? null : summary.collectTime
            );
        }
    }

    private String latestSensorBatchSql() {
        return """
                SELECT e.id AS equipment_id,
                       s.id AS sensor_id,
                       s.sensor_code,
                       latest_batch.detection_status,
                       latest_batch.collect_time,
                       feature.total_vibration
                FROM jzjc01_equipment e
                LEFT JOIN jzjc01_sensor_device s ON s.equipment_id = e.id
                LEFT JOIN jzjc01_waveform_batch latest_batch ON latest_batch.id = (
                    SELECT batch.id
                    FROM jzjc01_waveform_batch batch
                    WHERE batch.point_id = s.id
                    ORDER BY batch.collect_time DESC, batch.id DESC
                    LIMIT 1
                )
                LEFT JOIN jzjc01_vibration_feature feature ON feature.batch_id = latest_batch.id
                ORDER BY e.id, s.id
                """;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "OFFLINE";
        }

        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "NORMAL", "WARNING", "ALARM", "DANGER", "OFFLINE" -> status.trim().toUpperCase(Locale.ROOT);
            default -> "OFFLINE";
        };
    }

    private int severityOf(String status) {
        return switch (status) {
            case "DANGER" -> 4;
            case "ALARM" -> 3;
            case "WARNING" -> 2;
            case "NORMAL" -> 1;
            default -> 0;
        };
    }

    private final class MutableEquipmentStatus {

        private final int equipmentId;
        private String status = "OFFLINE";
        private Double totalVibration;
        private Integer sourceSensorId;
        private String sourceSensorCode;
        private LocalDateTime collectTime;

        private MutableEquipmentStatus(int equipmentId) {
            this.equipmentId = equipmentId;
        }

        private void accept(
                String candidateStatus,
                Integer candidateSensorId,
                String candidateSensorCode,
                Double candidateTotalVibration,
                LocalDateTime candidateCollectTime
        ) {
            int candidateSeverity = severityOf(candidateStatus);
            int currentSeverity = severityOf(status);
            boolean moreSevere = candidateSeverity > currentSeverity;
            boolean sameSeverityWithHigherVibration = candidateSeverity == currentSeverity
                    && candidateTotalVibration != null
                    && (totalVibration == null || candidateTotalVibration > totalVibration);

            if (!moreSevere && !sameSeverityWithHigherVibration) {
                return;
            }

            status = candidateStatus;
            totalVibration = candidateTotalVibration;
            sourceSensorId = candidateSensorId;
            sourceSensorCode = candidateSensorCode;
            collectTime = candidateCollectTime;
        }

        private boolean isOffline() {
            return "OFFLINE".equals(status);
        }
    }
}
