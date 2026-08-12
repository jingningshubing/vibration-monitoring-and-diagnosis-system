package com.cq.vibration.controller;

import com.cq.vibration.dto.BearingDiagnosis;
import com.cq.vibration.service.BearingDiagnosisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 波形批次的按需智能诊断查询接口。 */
@RestController
@RequestMapping("/api/vibration/diagnosis")
public class BearingDiagnosisController {
    private final BearingDiagnosisService bearingDiagnosisService;

    public BearingDiagnosisController(BearingDiagnosisService bearingDiagnosisService) {
        this.bearingDiagnosisService = bearingDiagnosisService;
    }

    /** 输入采集批次 ID，返回 ONNX 模型的诊断概率和汇总结论。 */
    @GetMapping("/waveform-batches/{batchId}")
    public BearingDiagnosis diagnose(@PathVariable Long batchId) {
        return bearingDiagnosisService.diagnose(batchId);
    }
}
