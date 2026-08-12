package com.cq.vibration.service;

/** 振动数据处理的统一口径服务：加速度为 g，速度为 mm/s，每轴去均值后计算。 */
public interface VibrationSignalProcessingService {
    /** 去除单轴恒定偏置，返回新数组。 */
    double[] demean(double[] samples);

    /** 将去均值后的加速度按梯形积分转换为速度，并去除积分后的速度偏置。 */
    double[] toVelocity(double[] acceleration, int sampleRate);

    /** 使用完整数据段计算均方根有效值。 */
    double rms(double[] samples);
}
