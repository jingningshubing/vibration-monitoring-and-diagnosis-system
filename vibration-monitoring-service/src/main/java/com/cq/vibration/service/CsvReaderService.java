package com.cq.vibration.service;

import com.cq.vibration.dto.TriAxisWaveform;
import java.nio.file.Path;

/** 负责从公开数据集 CSV 中读取固定时长的三轴波形。 */
public interface CsvReaderService {
    /**
     * 输入：CSV 路径和从 0 开始的一秒片段序号。
     * 输出：恰好 10,000 点/轴、采样率为 10 kHz 的三轴加速度波形。
     */
    TriAxisWaveform readOneSecond(Path file, int segment);
}
