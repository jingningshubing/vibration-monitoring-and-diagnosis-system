package com.cq.vibration.scheduler;

import com.cq.vibration.service.CsvImportService;
import com.cq.vibration.service.ScheduledBearingDiagnosisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;

/** 按整点触发 CSV 导入，并在四小时整点执行诊断。 */
@Component
public class CsvImportScheduler {
    private final CsvImportService importer;
    private final ScheduledBearingDiagnosisService scheduledDiagnosisService;
    private final JdbcTemplate jdbcTemplate;
    @Value("${vibration.replay.enabled:false}") private boolean enabled;
    @Value("${vibration.replay.catch-up-enabled:false}") private boolean catchUpEnabled;
    @Value("${vibration.replay.catch-up-start-time:}") private String catchUpStartTime;
    public CsvImportScheduler(CsvImportService importer, ScheduledBearingDiagnosisService scheduledDiagnosisService, JdbcTemplate jdbcTemplate) { this.importer = importer; this.scheduledDiagnosisService = scheduledDiagnosisService; this.jdbcTemplate = jdbcTemplate; }
    /** 每小时整点执行一次采集和预警，四小时整点追加诊断。 */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Shanghai")
    public void run() { runAt(LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)); }

    @PostConstruct
    public void catchUpMissingHours() {
        if (!enabled || !catchUpEnabled) return;
        LocalDateTime last = jdbcTemplate.query("SELECT MAX(collect_time) FROM jzjc01_waveform_batch", rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null);
        if (last == null) {
            if (catchUpStartTime == null || catchUpStartTime.isBlank()) return;
            last = LocalDateTime.parse(catchUpStartTime).minusHours(1);
        }
        LocalDateTime target = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        for (LocalDateTime time = last.plusHours(1); !time.isAfter(target); time = time.plusHours(1)) runAt(time);
    }

    private void runAt(LocalDateTime time) {
        if (!enabled) return;
        LocalDateTime collectTime = time.withMinute(0).withSecond(0).withNano(0);
        importer.importScheduledBatch(collectTime);
        if (collectTime.getHour() % 4 == 0) scheduledDiagnosisService.runDueDiagnoses(collectTime);
    }
}
