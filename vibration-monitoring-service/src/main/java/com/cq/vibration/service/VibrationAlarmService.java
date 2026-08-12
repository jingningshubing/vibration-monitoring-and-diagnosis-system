package com.cq.vibration.service;

import com.cq.vibration.dto.VibrationAlarmOptions;
import com.cq.vibration.dto.VibrationAlarmPage;

import java.time.LocalDate;
import com.cq.vibration.dto.VibrationAlarmStatistics;

/** 振动预警页面的筛选项和列表查询服务。 */
public interface VibrationAlarmService {
    /**
     * 查询设备及传感器选项。
     *
     * @return 顶部设备、传感器下拉框数据
     */
    VibrationAlarmOptions getOptions();

    /**
     * 按筛选条件分页查询报警记录。
     *
     * @return 表格分页数据；结束日期按当天 24:00 前的数据计算
     */
    VibrationAlarmPage query(Long equipmentId, Long sensorId, String alarmLevel,
                             LocalDate startDate, LocalDate endDate, Integer page, Integer size);

    /** 按当前筛选条件统计发生报警的设备台数和报警次数。 */
    VibrationAlarmStatistics statistics(Long equipmentId, Long sensorId, String alarmLevel, LocalDate startDate, LocalDate endDate);
}
