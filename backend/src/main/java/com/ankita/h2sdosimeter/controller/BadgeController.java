package com.ankita.h2sdosimeter.controller;

import com.ankita.h2sdosimeter.dto.ApiResponse;
import com.ankita.h2sdosimeter.entity.Badge;
import com.ankita.h2sdosimeter.service.BadgeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Badge REST controller.
 *
 * Base path: /api/badges
 */
@RestController
@RequestMapping("/badges")
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    /** POST /api/badges — register a new badge */
    @PostMapping
    public ResponseEntity<ApiResponse<Badge>> register(@RequestBody Map<String, String> body) {
        String badgeId   = body.get("badgeId");
        String mfgStr    = body.get("manufacturingDate");
        String expStr    = body.get("expiryDate");
        String batch     = body.get("batchNumber");
        String model     = body.get("badgeModel");

        if (badgeId == null || badgeId.isBlank()) {
            throw new IllegalArgumentException("badgeId is required");
        }

        LocalDate mfg = mfgStr != null ? LocalDate.parse(mfgStr) : null;
        LocalDate exp = expStr != null ? LocalDate.parse(expStr) : null;

        Badge b = badgeService.register(badgeId, mfg, exp, batch, model);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Badge registered", b));
    }

    /** GET /api/badges — list all badges */
    @GetMapping
    public ApiResponse<List<Badge>> getAll() {
        return ApiResponse.ok(badgeService.getAll());
    }

    /** GET /api/badges/{badgeId} — get badge details */
    @GetMapping("/{badgeId}")
    public ApiResponse<Badge> getByBadgeId(@PathVariable String badgeId) {
        return ApiResponse.ok(badgeService.getByBadgeId(badgeId));
    }

    /** GET /api/badges/{badgeId}/validate — validate a badge */
    @GetMapping("/{badgeId}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable String badgeId) {
        return ApiResponse.ok(badgeService.validate(badgeId));
    }
}
