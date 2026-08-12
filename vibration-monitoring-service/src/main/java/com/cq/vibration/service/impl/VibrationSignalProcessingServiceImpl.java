package com.cq.vibration.service.impl;

import com.cq.vibration.service.VibrationSignalProcessingService;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/** 《振动数据处理、展示与报警判定口径（V1.0）》的基础信号处理实现。 */
@Service
public class VibrationSignalProcessingServiceImpl implements VibrationSignalProcessingService {
    private static final double GRAVITY = 9.80665;

    /** {@inheritDoc} */
    @Override
    public double[] demean(double[] samples) {
        if (samples == null || samples.length == 0) return new double[0];
        double mean = Arrays.stream(samples).average().orElse(0D);
        double[] result = new double[samples.length];
        for (int index = 0; index < samples.length; index++) result[index] = samples[index] - mean;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public double[] toVelocity(double[] acceleration, int sampleRate) {
        if (acceleration == null || acceleration.length == 0) return new double[0];
        if (sampleRate <= 0) throw new IllegalArgumentException("采样率必须大于 0");
        double[] velocity = new double[acceleration.length];
        double step = 1D / sampleRate;
        for (int index = 1; index < acceleration.length; index++) {
            velocity[index] = velocity[index - 1]
                    + (acceleration[index - 1] + acceleration[index]) / 2D * GRAVITY * 1000D * step;
        }
        return demean(velocity);
    }

    /** {@inheritDoc} */
    @Override
    public double rms(double[] samples) {
        if (samples == null || samples.length == 0) return 0D;
        double sum = 0D;
        for (double sample : samples) sum += sample * sample;
        return Math.sqrt(sum / samples.length);
    }
}
