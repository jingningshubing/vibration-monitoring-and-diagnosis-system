package com.cq.vibration.service;

import com.cq.vibration.dto.TriAxisWaveform;
import com.cq.vibration.dto.VibrationFeatures;

/** 从三轴原始波形中计算预警所需的特征值。 */
public interface VibrationFeatureService {
    /** 输入：一个三轴加速度波形；输出：三轴 RMS、总振值和最大方向。 */
    VibrationFeatures calculate(TriAxisWaveform waveform);
}
