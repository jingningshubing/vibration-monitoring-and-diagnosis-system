package com.cq.vibration.dto;

/**
 * 设备台账的简要信息。
 *
 * <p>输出给设备状态页，用于显示设备卡片标题和后续的设备基础信息。</p>
 *
 * @param id 设备主键
 * @param equipCode 设备编码，对应数据库字段 {@code equip_code}
 * @param equipName 设备名称
 * @param equipType 设备类型，例如“泵”或“管线”
 * @param installPosition 设备安装位置
 */
public record EquipmentSummary(
        Long id,
        String equipCode,
        String equipName,
        String equipType,
        String installPosition
) {
}
