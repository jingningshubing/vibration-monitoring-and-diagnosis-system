package com.cq.vibration.service;

import java.time.LocalDateTime;

/** 在报警流程已成功结束后，检查并执行到期的报警/危险传感器定时辅助诊断。 */
public interface ScheduledBearingDiagnosisService {
    /** 输入：本轮报警完成时间；输出：仅对已到期的 ALARM、DANGER 传感器保存一次三批次汇总诊断。 */
    void runDueDiagnoses(LocalDateTime now);
}
