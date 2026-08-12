package com.cq.vibration.service.impl;

import com.cq.vibration.service.AlarmMessageService;
import org.springframework.stereotype.Service;

/** 将报警数值格式化为预警列表展示文本。 */
@Service
public class AlarmMessageServiceImpl implements AlarmMessageService {
    /**
     * 输入：报警等级、当前总振值、该等级对应阈值。
     * 输出：例如“当前总振值：8.37 mm/s，高于预警值 67.5 %”。
     */
    @Override
    public String format(String level, double totalVibration, double threshold) {
        String thresholdName = switch (level) {
            case "WARNING" -> "预警";
            case "ALARM" -> "报警";
            case "DANGER" -> "危险";
            default -> "阈值";
        };
        double percent = (totalVibration - threshold) * 100 / threshold;
        return "当前总振值：%.2f mm/s，高于%s值 %.1f %%"
                .formatted(totalVibration, thresholdName, percent);
    }
}
