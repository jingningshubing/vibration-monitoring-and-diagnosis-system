package com.cq.vibration.service.impl;

import com.cq.vibration.service.AlarmLevelService;
import org.springframework.stereotype.Service;

/** 按三条总振值阈值判定四档状态。 */
@Service
public class AlarmLevelServiceImpl implements AlarmLevelService {
    /** 输入：总振值和预警/报警/危险阈值；输出：NORMAL、WARNING、ALARM 或 DANGER。 */
    @Override
    public String level(double total, Double warning, Double alarm, Double danger) {
        if (danger != null && total >= danger) return "DANGER";
        if (alarm != null && total >= alarm) return "ALARM";
        if (warning != null && total >= warning) return "WARNING";
        return "NORMAL";
    }
}
