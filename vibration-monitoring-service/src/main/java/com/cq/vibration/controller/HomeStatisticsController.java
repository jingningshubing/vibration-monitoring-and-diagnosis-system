package com.cq.vibration.controller;

import com.cq.vibration.dto.HomeStatistics;
import com.cq.vibration.service.HomeStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页统计接口。 */
@RestController @RequestMapping("/api/vibration/home")
public class HomeStatisticsController {
    private final HomeStatisticsService service;
    public HomeStatisticsController(HomeStatisticsService service) { this.service=service; }
    /** 输出：最近 30 天首页统计数据。 */
    @GetMapping("/statistics") public HomeStatistics statistics(){ return service.statistics(); }
}
