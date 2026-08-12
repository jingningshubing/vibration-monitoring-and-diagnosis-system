package com.cq.vibration.controller;

import com.cq.vibration.dto.WaveformDetail;
import com.cq.vibration.service.WaveformQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 三轴波形和 FFT 频谱查询接口。 */
@RestController
@RequestMapping("/api/vibration/waveform-batches")
public class WaveformQueryController {
    private final WaveformQueryService waveformQueryService;
    public WaveformQueryController(WaveformQueryService waveformQueryService) { this.waveformQueryService = waveformQueryService; }
    /** @return 指定采集批次的三轴加速度、速度和单边 RMS 频谱。 */
    @GetMapping("/{batchId}")
    public WaveformDetail detail(@PathVariable Long batchId, @RequestParam(required = false) Integer downsample) {
        return waveformQueryService.getDetail(batchId, downsample);
    }
}
