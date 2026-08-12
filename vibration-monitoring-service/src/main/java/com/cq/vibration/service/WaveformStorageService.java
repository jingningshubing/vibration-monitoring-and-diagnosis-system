package com.cq.vibration.service;

import com.cq.vibration.dto.TriAxisWaveform;
import java.nio.file.Path;
import java.time.LocalDateTime;

/** 保存和定位三轴原始波形文件。 */
public interface WaveformStorageService {
    /** 输入：传感器编码、采集时间、三轴波形；输出：写入成功后的 gzip 文件路径。 */
    Path save(String sensorCode, LocalDateTime collectTime, TriAxisWaveform waveform);

    /** 读取已保存的 JSON_GZIP_3AXIS 波形文件。 */
    TriAxisWaveform load(String waveformPath);
}
