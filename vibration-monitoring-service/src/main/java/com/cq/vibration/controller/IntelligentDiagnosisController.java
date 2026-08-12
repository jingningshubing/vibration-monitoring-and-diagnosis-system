package com.cq.vibration.controller;

import com.cq.vibration.dto.IntelligentDiagnosisPage;
import com.cq.vibration.dto.IntelligentDiagnosisStatistics;
import com.cq.vibration.dto.EquipmentLatestDiagnosis;
import com.cq.vibration.service.IntelligentDiagnosisQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

/** 智能诊断页面的诊断结果查询接口。 */
@RestController
@RequestMapping("/api/vibration/intelligent-diagnoses")
public class IntelligentDiagnosisController {
    private final IntelligentDiagnosisQueryService service;
    public IntelligentDiagnosisController(IntelligentDiagnosisQueryService service) { this.service = service; }
    /** 输入：设备、传感器、时间范围和分页；输出：已完成诊断记录。 */
    @GetMapping
    public IntelligentDiagnosisPage query(@RequestParam(required = false) Long equipmentId, @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.query(equipmentId, sensorId, startDate, endDate, page, size);
    }
    /** 输入：设备、传感器和日期范围；输出：诊断台数/次数圆环统计。 */
    @GetMapping("/statistics")
    public IntelligentDiagnosisStatistics statistics(@RequestParam(required = false) Long equipmentId, @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.statistics(equipmentId, sensorId, startDate, endDate);
    }
    /** 输入：无；输出：各设备的最新诊断结论；用途：设备状态页卡片展示。 */
    @GetMapping("/latest-by-equipment")
    public List<EquipmentLatestDiagnosis> latestByEquipment() { return service.latestByEquipment(); }
}
