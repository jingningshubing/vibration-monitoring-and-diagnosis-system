package com.cq.vibration.service;

import com.cq.vibration.dto.EquipmentSummary;
import com.cq.vibration.dto.EquipmentStatusSummary;
import com.cq.vibration.dto.SensorAxisVibration;
import com.cq.vibration.dto.EquipmentVibrationStatusStatistics;
import com.cq.vibration.dto.SensorVibrationStatusStatistics;

import java.util.List;

/**
 * 设备台账查询服务。
 */
public interface EquipmentQueryService {

    /**
     * 查询系统内全部设备。
     *
     * <p>输入：无。输出：按设备 ID 升序排列的设备简要信息列表。</p>
     *
     * @return 设备列表
     */
    List<EquipmentSummary> findAll();

    /**
     * 查询设备当前状态汇总。
     *
     * <p>输入：无。输出：每台设备的风险状态、当前关注测点总振值、来源传感器及采集时间。</p>
     *
     * @return 设备状态汇总列表
     */
    List<EquipmentStatusSummary> findAllStatus();

    /**
     * 查询某台设备下全部传感器最新批次的三轴速度有效值。
     *
     * <p>输入：设备 ID。输出：该设备传感器的 X、Y、Z 三轴速度有效值列表。</p>
     *
     * @param equipmentId 设备主键
     * @return 传感器三轴振动列表
     */
    List<SensorAxisVibration> findLatestSensorVibrations(Long equipmentId);

    /**
     * 统计设备总振值状态分布。
     *
     * <p>输入：无。输出：正常、预警、报警、危险、离线及设备总数。</p>
     *
     * @return 总振值状态统计
     */
    EquipmentVibrationStatusStatistics getVibrationStatusStatistics();

    /** 输入：无；输出：全部测点的当前报警状态统计；用途：首页测点报警状态圆环。 */
    SensorVibrationStatusStatistics getSensorStatusStatistics();
}
