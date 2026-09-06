package com.ankita.h2sdosimeter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health / status endpoint.
 * GET /api/health — used to verify the server is running.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status",      "UP",
                "service",     "H2S Dosimeter Backend",
                "version",     "1.0.0",
                "timestamp",   LocalDateTime.now().toString(),
                "safetyNotice","All ppm.hr estimates are indicative only. " +
                               "They are not validated occupational-health measurements."
        );
    }
}
