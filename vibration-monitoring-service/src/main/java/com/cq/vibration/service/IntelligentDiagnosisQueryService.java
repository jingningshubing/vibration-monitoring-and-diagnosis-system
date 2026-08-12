package com.cq.vibration.service;

import com.cq.vibration.dto.IntelligentDiagnosisPage;
import com.cq.vibration.dto.IntelligentDiagnosisStatistics;
import com.cq.vibration.dto.EquipmentLatestDiagnosis;
import java.time.LocalDate;
import java.util.List;

/** 已完成智能诊断结果的页面查询服务。 */
public interface IntelligentDiagnosisQueryService {
    /** 输入：设备、传感器、日期范围及分页；输出：智能诊断表格记录；用途：智能诊断页面列表展示。 */
    IntelligentDiagnosisPage query(Long equipmentId, Long sensorId, LocalDate startDate, LocalDate endDate, Integer page, Integer size);

    /** 输入：设备、传感器和日期范围；输出：诊断设备去重数量与诊断记录次数的分类统计。 */
    IntelligentDiagnosisStatistics statistics(Long equipmentId, Long sensorId, LocalDate startDate, LocalDate endDate);

    /** 输入：无；输出：每台已有诊断设备的最新诊断结论；用途：设备状态卡片的辅助信息展示。 */
    List<EquipmentLatestDiagnosis> latestByEquipment();
}
