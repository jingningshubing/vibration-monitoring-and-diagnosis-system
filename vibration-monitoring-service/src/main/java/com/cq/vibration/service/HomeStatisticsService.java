package com.cq.vibration.service;

import com.cq.vibration.dto.HomeStatistics;

/** 首页统计服务。 */
public interface HomeStatisticsService {
    /** 输入：无；输出：最近 30 天的设备状态与诊断统计；用途：首页三张统计卡片。 */
    HomeStatistics statistics();
}
