package com.ankita.h2sdosimeter.controller;

import com.ankita.h2sdosimeter.dto.ApiResponse;
import com.ankita.h2sdosimeter.dto.ExposureSummaryResponse;
import com.ankita.h2sdosimeter.dto.ScanRecordResponse;
import com.ankita.h2sdosimeter.dto.ScanSubmitRequest;
import com.ankita.h2sdosimeter.service.ScanService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Scan record REST controller.
 *
 * Base path: /api/scans
 *
 * SAFETY NOTE: Every response includes calibrationStatus and safetyNotice.
 * Clients must display these fields prominently. estimatedPpmHr is null
 * when calibrationStatus = UNCALIBRATED.
 */
@RestController
@RequestMapping("/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    /** POST /api/scans — submit a scan result from the Android app */
    @PostMapping
    public ResponseEntity<ApiResponse<ScanRecordResponse>> submit(
            @Valid @RequestBody ScanSubmitRequest req) {
        ScanRecordResponse result = scanService.submitScan(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Scan submitted", result));
    }

    /** GET /api/scans/{id} — get one scan record by database ID */
    @GetMapping("/{id}")
    public ApiResponse<ScanRecordResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(scanService.getById(id));
    }

    /**
     * GET /api/scans/worker/{workerId}
     * Optional query params: from, to (ISO date yyyy-MM-dd)
     * Returns all scans for a worker, newest first.
     */
    @GetMapping("/worker/{workerId}")
    public ApiResponse<List<ScanRecordResponse>> getByWorker(
            @PathVariable String workerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from != null && to != null) {
            return ApiResponse.ok(scanService.getByWorkerAndDateRange(workerId, from, to));
        }
        return ApiResponse.ok(scanService.getByWorker(workerId));
    }

    /** GET /api/scans/badge/{badgeId} — get all scans for a badge */
    @GetMapping("/badge/{badgeId}")
    public ApiResponse<List<ScanRecordResponse>> getByBadge(@PathVariable String badgeId) {
        return ApiResponse.ok(scanService.getByBadge(badgeId));
    }

    /** GET /api/scans/worker/{workerId}/latest — get most recent scan for a worker */
    @GetMapping("/worker/{workerId}/latest")
    public ApiResponse<ScanRecordResponse> getLatest(@PathVariable String workerId) {
        return ApiResponse.ok(scanService.getLatest(workerId));
    }

    /**
     * GET /api/scans/worker/{workerId}/today
     * Returns today's exposure summary for a worker.
     */
    @GetMapping("/worker/{workerId}/today")
    public ApiResponse<ExposureSummaryResponse> getToday(@PathVariable String workerId) {
        return ApiResponse.ok(scanService.getDailySummary(workerId, LocalDate.now()));
    }

    /**
     * GET /api/scans/worker/{workerId}/summary?date=yyyy-MM-dd
     * Returns daily exposure summary for a specific date.
     */
    @GetMapping("/worker/{workerId}/summary")
    public ApiResponse<ExposureSummaryResponse> getDailySummary(
            @PathVariable String workerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(scanService.getDailySummary(workerId, date));
    }
}
