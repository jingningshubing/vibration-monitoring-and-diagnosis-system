package com.cq.vibration.controller;

import com.cq.vibration.dto.AxisTrendPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.List;

@RestController
@RequestMapping("/api/vibration/device-trends")
public class DeviceVibrationController {
    private final JdbcTemplate jdbc;
    public DeviceVibrationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/axes")
    public List<AxisTrendPoint> axes(@RequestParam Long sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "velocity") String measure) {
        String x = "acceleration".equalsIgnoreCase(measure) ? "x_acceleration_rms" : "x_velocity_rms";
        String y = "acceleration".equalsIgnoreCase(measure) ? "y_acceleration_rms" : "y_velocity_rms";
        String z = "acceleration".equalsIgnoreCase(measure) ? "z_acceleration_rms" : "z_velocity_rms";
        String sql = "SELECT batch.id AS batch_id, batch.collect_time, feature." + x + " AS x_value, feature." + y + " AS y_value, feature." + z + " AS z_value, batch.detection_status, diagnosis.diagnosis_text, diagnosis.confidence, diagnosis.stability, diagnosis.description "
                + "FROM jzjc01_waveform_batch batch JOIN jzjc01_vibration_feature feature ON feature.batch_id=batch.id "
                + "LEFT JOIN jzjc01_bearing_diagnosis diagnosis ON diagnosis.latest_batch_id=batch.id AND diagnosis.diagnosis_mode='SCHEDULED' "
                + "WHERE batch.point_id=?" + (startDate == null ? "" : " AND batch.collect_time>=?")
                + (endDate == null ? "" : " AND batch.collect_time<?") + " ORDER BY batch.collect_time";
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(); args.add(sensorId);
        if (startDate != null) args.add(Timestamp.valueOf(startDate.atStartOfDay()));
        if (endDate != null) args.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
        return jdbc.query(sql, (rs, row) -> new AxisTrendPoint(rs.getLong("batch_id"), rs.getTimestamp("collect_time").toLocalDateTime(), rs.getObject("x_value", Double.class), rs.getObject("y_value", Double.class), rs.getObject("z_value", Double.class), rs.getString("detection_status"), rs.getString("diagnosis_text"), rs.getObject("confidence", Double.class), rs.getObject("stability", Double.class), rs.getString("description")), args.toArray());
    }
}
