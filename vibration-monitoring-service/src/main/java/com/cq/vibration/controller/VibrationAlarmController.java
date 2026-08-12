package com.cq.vibration.controller;

import com.cq.vibration.dto.VibrationAlarmOptions;
import com.cq.vibration.dto.VibrationAlarmPage;
import com.cq.vibration.dto.VibrationAlarmStatistics;
import com.cq.vibration.service.VibrationAlarmService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 振动预警页面的设备筛选与报警记录查询接口。 */
@RestController
@RequestMapping("/api/vibration/alarms")
public class VibrationAlarmController {
    private final VibrationAlarmService vibrationAlarmService;
    public VibrationAlarmController(VibrationAlarmService vibrationAlarmService) { this.vibrationAlarmService = vibrationAlarmService; }

    /** @return 顶部设备、传感器筛选项。 */
    @GetMapping("/options")
    public VibrationAlarmOptions options() { return vibrationAlarmService.getOptions(); }

    /** @return 按设备、传感器、等级、日期筛选的报警分页记录。 */
    @GetMapping
    public VibrationAlarmPage query(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) String alarmLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return vibrationAlarmService.query(equipmentId, sensorId, alarmLevel, startDate, endDate, page, size);
    }

    /** @return 当前筛选范围内的报警设备台数和报警记录次数。 */
    @GetMapping("/statistics")
    public VibrationAlarmStatistics statistics(
            @RequestParam(required = false) Long equipmentId, @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) String alarmLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return vibrationAlarmService.statistics(equipmentId, sensorId, alarmLevel, startDate, endDate);
    }
}
