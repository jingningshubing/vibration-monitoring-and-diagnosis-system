package com.cq.vibration.dto;

/** 首页测点当前报警状态的数量统计。 */
public record SensorVibrationStatusStatistics(long normal, long warning, long alarm, long danger, long offline, long total) { }
