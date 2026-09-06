package com.ankita.h2sdosimeter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * H2S Dosimeter Backend - Spring Boot entry point.
 *
 * Provides REST APIs for the H2S Dosimeter Android application:
 *   - Worker registration and lookup
 *   - Badge validation
 *   - Scan result submission and retrieval
 *   - Exposure history (by worker/badge/date/shift)
 *   - Calibration point management
 *
 * SAFETY NOTE: All scan result endpoints return calibration status and
 * confidence with every analysis result. Uncalibrated readings are never
 * presented as validated H2S concentration measurements.
 */
@SpringBootApplication
public class H2sDosimeterApplication {
    public static void main(String[] args) {
        SpringApplication.run(H2sDosimeterApplication.class, args);
    }
}
