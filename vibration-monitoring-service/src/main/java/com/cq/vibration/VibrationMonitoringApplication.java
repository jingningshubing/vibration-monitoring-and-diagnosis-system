package com.cq.vibration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VibrationMonitoringApplication {
    public static void main(String[] args) { SpringApplication.run(VibrationMonitoringApplication.class, args); }
}
