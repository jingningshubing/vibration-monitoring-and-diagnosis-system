package com.cq.vibration.service;

import com.cq.vibration.dto.WaveformDetail;

/** 波形详情查询服务。 */
public interface WaveformQueryService {
    /** 读取指定批次的原始三轴波形，并返回加速度、速度及频谱。 */
    WaveformDetail getDetail(Long batchId, Integer downsample);
}
