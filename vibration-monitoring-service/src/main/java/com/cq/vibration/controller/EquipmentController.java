package com.cq.vibration.controller;

import com.cq.vibration.dto.EquipmentSummary;
import com.cq.vibration.dto.EquipmentStatusSummary;
import com.cq.vibration.dto.SensorAxisVibration;
import com.cq.vibration.dto.EquipmentVibrationStatusStatistics;
import com.cq.vibration.dto.SensorVibrationStatusStatistics;
import com.cq.vibration.service.EquipmentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 设备台账的只读接口。
 */
@RestController
@RequestMapping("/api/vibration/equipment")
public class EquipmentController {

    private final EquipmentQueryService equipmentQueryService;

    public EquipmentController(EquipmentQueryService equipmentQueryService) {
        this.equipmentQueryService = equipmentQueryService;
    }

    /**
     * 获取全部设备。
     *
     * <p>请求：{@code GET /api/vibration/equipment}。</p>
     * <p>响应：设备 ID、设备编码、名称、类型及安装位置组成的 JSON 数组。</p>
     *
     * @return 设备简要信息列表
     */
    @GetMapping
    public List<EquipmentSummary> getAllEquipment() {
        return equipmentQueryService.findAll();
    }

    /**
     * 获取设备的当前状态汇总。
     *
     * <p>请求：{@code GET /api/vibration/equipment/status}。</p>
     * <p>响应：每台设备的状态、总振值、状态来源传感器和采集时间。</p>
     *
     * @return 设备状态汇总列表
     */
    @GetMapping("/status")
    public List<EquipmentStatusSummary> getAllEquipmentStatus() {
        return equipmentQueryService.findAllStatus();
    }

    /**
     * 获取设备总振值状态的分布统计。
     *
     * <p>请求：{@code GET /api/vibration/equipment/status/statistics}。</p>
     * <p>响应：正常、预警、报警、危险、离线及设备总数。</p>
     *
     * @return 总振值状态统计
     */
    @GetMapping("/status/statistics")
    public EquipmentVibrationStatusStatistics getVibrationStatusStatistics() {
        return equipmentQueryService.getVibrationStatusStatistics();
    }

    /** 请求：GET /api/vibration/equipment/sensor-status/statistics；响应：首页测点当前报警状态统计。 */
    @GetMapping("/sensor-status/statistics")
    public SensorVibrationStatusStatistics getSensorStatusStatistics() { return equipmentQueryService.getSensorStatusStatistics(); }

    /**
     * 获取指定设备全部传感器的最新三轴速度有效值。
     *
     * <p>请求：{@code GET /api/vibration/equipment/{equipmentId}/sensor-vibrations}。</p>
     * <p>响应：传感器名称、安装位置、状态及 X/Y/Z 三轴 mm/s RMS 数值。</p>
     *
     * @param equipmentId 设备主键
     * @return 传感器三轴振动列表
     */
    @GetMapping("/{equipmentId}/sensor-vibrations")
    public List<SensorAxisVibration> getLatestSensorVibrations(
            @PathVariable Long equipmentId
    ) {
        return equipmentQueryService.findLatestSensorVibrations(equipmentId);
    }
}
