package com.cq.vibration.service;

import com.cq.vibration.dto.BearingDiagnosis;

/** 基于 ONNX 模型提供按需轴承状态辅助诊断。 */
public interface BearingDiagnosisService {
    /** 输入：波形批次主键；输出：健康、内圈或外圈的汇总推理结果；用途：详情弹窗手动诊断。 */
    BearingDiagnosis diagnose(Long batchId);
}
