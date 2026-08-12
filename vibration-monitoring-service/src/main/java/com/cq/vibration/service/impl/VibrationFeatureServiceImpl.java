package com.cq.vibration.service.impl;

import com.cq.vibration.dto.TriAxisWaveform;
import com.cq.vibration.dto.VibrationFeatures;
import com.cq.vibration.service.VibrationFeatureService;
import com.cq.vibration.service.VibrationSignalProcessingService;
import org.springframework.stereotype.Service;

/** 按统一口径计算三轴加速度 RMS、速度 RMS、总振值和最大振动方向。 */
@Service
public class VibrationFeatureServiceImpl implements VibrationFeatureService {
    private final VibrationSignalProcessingService signalProcessingService;

    public VibrationFeatureServiceImpl(VibrationSignalProcessingService signalProcessingService) {
        this.signalProcessingService = signalProcessingService;
    }

    /** 输入：三轴原始加速度波形；输出：用于数据库和报警判断的统一口径特征。 */
    @Override
    public VibrationFeatures calculate(TriAxisWaveform waveform) {
        double[] xAcceleration = signalProcessingService.demean(waveform.x());
        double[] yAcceleration = signalProcessingService.demean(waveform.y());
        double[] zAcceleration = signalProcessingService.demean(waveform.z());
        double[] accelerationRms = {
                signalProcessingService.rms(xAcceleration), signalProcessingService.rms(yAcceleration), signalProcessingService.rms(zAcceleration)
        };
        double[] velocityRms = {
                signalProcessingService.rms(signalProcessingService.toVelocity(xAcceleration, waveform.sampleRate())),
                signalProcessingService.rms(signalProcessingService.toVelocity(yAcceleration, waveform.sampleRate())),
                signalProcessingService.rms(signalProcessingService.toVelocity(zAcceleration, waveform.sampleRate()))
        };
        int maxIndex = maxIndex(velocityRms);
        return new VibrationFeatures(accelerationRms[0], accelerationRms[1], accelerationRms[2],
                velocityRms[0], velocityRms[1], velocityRms[2], velocityRms[maxIndex], "XYZ".substring(maxIndex, maxIndex + 1));
    }

    /** 输入：三轴速度 RMS；输出：最大值所在轴的数组下标。 */
    private int maxIndex(double[] values) {
        return values[0] >= values[1] && values[0] >= values[2] ? 0 : values[1] >= values[2] ? 1 : 2;
    }
}
