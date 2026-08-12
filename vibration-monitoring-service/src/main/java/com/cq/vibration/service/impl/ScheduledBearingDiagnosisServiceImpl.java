package com.cq.vibration.service.impl;

import com.cq.vibration.dto.BearingDiagnosis;
import com.cq.vibration.service.BearingDiagnosisService;
import com.cq.vibration.service.ScheduledBearingDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 实现 2 小时危险、4 小时报警的定时诊断调度；正常、预警、离线传感器均不会进入本服务。 */
@Service
public class ScheduledBearingDiagnosisServiceImpl implements ScheduledBearingDiagnosisService {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledBearingDiagnosisServiceImpl.class);
    private static final int DIAGNOSIS_BATCH_COUNT = 4;
    private final JdbcTemplate jdbcTemplate;
    private final BearingDiagnosisService diagnosisService;
    public ScheduledBearingDiagnosisServiceImpl(JdbcTemplate jdbcTemplate, BearingDiagnosisService diagnosisService) { this.jdbcTemplate = jdbcTemplate; this.diagnosisService = diagnosisService; }

    /** {@inheritDoc} */
    @Override
    public void runDueDiagnoses(LocalDateTime now) {
        List<Candidate> candidates = jdbcTemplate.query("SELECT id FROM jzjc01_sensor_device",
                (rs, row) -> new Candidate(rs.getLong("id")));
        for (Candidate candidate : candidates) {
            try { diagnoseIfDue(candidate, now); }
            catch (Exception exception) { LOG.warn("传感器 {} 定时辅助诊断失败，不影响本轮报警流程", candidate.sensorId, exception); }
        }
    }

    /** 若达到当前等级的间隔且具有最近连续三条有效批次，则执行并保存一次汇总结果。 */
    private void diagnoseIfDue(Candidate candidate, LocalDateTime now) {
        java.sql.Timestamp lastTimestamp = jdbcTemplate.queryForObject(
                "SELECT MAX(diagnosis_time) FROM jzjc01_bearing_diagnosis WHERE sensor_id=? AND diagnosis_mode='SCHEDULED'",
                java.sql.Timestamp.class, candidate.sensorId);
        LocalDateTime lastTime = lastTimestamp == null ? null : lastTimestamp.toLocalDateTime();
        long requiredHours = 4;
        if (lastTime != null && Duration.between(lastTime, now).compareTo(Duration.ofHours(requiredHours)) < 0) return;
        diagnoseAndSave(candidate, now);
    }

    /** 读取最近三条有效波形并保存定时诊断。 */
    private BearingDiagnosis diagnoseAndSave(Candidate candidate, LocalDateTime now) {
        List<Long> batchIds = jdbcTemplate.query("""
                SELECT id FROM jzjc01_waveform_batch
                WHERE point_id=? AND waveform_path IS NOT NULL AND waveform_path<>'' AND sample_count>=1000
                ORDER BY collect_time DESC,id DESC LIMIT 4
                """, (rs, row) -> rs.getLong(1), candidate.sensorId);
        if (batchIds.size() < DIAGNOSIS_BATCH_COUNT) {
            LOG.info("传感器 {} 最近有效波形批次仅 {} 条，等待凑满连续 {} 条后再执行定时诊断", candidate.sensorId, batchIds.size(), DIAGNOSIS_BATCH_COUNT);
            return null;
        }
        BearingDiagnosis[] diagnoses = batchIds.stream().map(diagnosisService::diagnose).toArray(BearingDiagnosis[]::new);
        BearingDiagnosis merged = merge(diagnoses);
        jdbcTemplate.update("""
                INSERT INTO jzjc01_bearing_diagnosis(sensor_id,latest_batch_id,diagnosis_time,diagnosis_mode,diagnosis_label,diagnosis_text,confidence,stability,window_count,healthy_probability,inner_race_probability,outer_race_probability,description)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, candidate.sensorId, batchIds.get(0), now, "SCHEDULED", merged.label(), merged.diagnosis(), merged.confidence(), merged.stability(), merged.windowCount(), merged.probabilities().healthy(), merged.probabilities().innerRace(), merged.probabilities().outerRace(), merged.description());
        LOG.info("传感器 {} 已完成定时诊断，结论：{}", candidate.sensorId, merged.diagnosis());
        return merged;
    }

    /** 对最近三批的三类概率做均值汇总，并以批次结论一致性作为稳定性。 */
    private BearingDiagnosis merge(BearingDiagnosis[] diagnoses) {
        double healthy = 0, inner = 0, outer = 0; int windows = 0, innerVotes = 0, outerVotes = 0, healthyVotes = 0;
        for (BearingDiagnosis diagnosis : diagnoses) { healthy += diagnosis.probabilities().healthy(); inner += diagnosis.probabilities().innerRace(); outer += diagnosis.probabilities().outerRace(); windows += diagnosis.windowCount(); switch (diagnosis.label()) { case "INNER_RACE" -> innerVotes++; case "OUTER_RACE" -> outerVotes++; default -> healthyVotes++; } }
        healthy /= diagnoses.length; inner /= diagnoses.length; outer /= diagnoses.length;
        double[] values = {healthy, inner, outer}; int best = values[1] > values[0] ? 1 : 0; if (values[2] > values[best]) best = 2;
        int votes = new int[]{healthyVotes, innerVotes, outerVotes}[best]; double confidence = values[best], stability = (double) votes / diagnoses.length;
        boolean uncertain = confidence < .60 || stability < .60;
        String label = new String[]{"HEALTHY", "INNER_RACE", "OUTER_RACE"}[best];
        String text = uncertain ? "当前数据特征不明确" : switch (label) { case "HEALTHY" -> "健康"; case "INNER_RACE" -> "轴承内圈故障"; default -> "轴承外圈故障"; };
        String description = maintenanceAdvice(label, uncertain);
        return new BearingDiagnosis(label, text, confidence, stability, windows, new BearingDiagnosis.Probabilities(healthy, inner, outer), description);
    }
    /** 定时诊断入库的描述字段统一保存维护建议，而非模型说明文本。 */
    private String maintenanceAdvice(String label, boolean uncertain) {
        if (uncertain) return "建议持续跟踪后续批次趋势，并结合现场点检、润滑和运行工况复核。";
        return switch (label) {
            case "HEALTHY" -> "建议保持现有巡检频率，持续观察振动趋势。";
            case "INNER_RACE" -> "建议安排轴承内圈及配合状态检查，复核润滑、轴向载荷和安装情况。";
            default -> "建议检查轴承外圈座、润滑和紧固状态，并结合现场点检确认。";
        };
    }
    private record Candidate(long sensorId) { }
}
