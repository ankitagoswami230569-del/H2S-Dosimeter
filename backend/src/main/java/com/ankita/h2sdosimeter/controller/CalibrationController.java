package com.ankita.h2sdosimeter.controller;

import com.ankita.h2sdosimeter.dto.ApiResponse;
import com.ankita.h2sdosimeter.dto.CalibrationPointRequest;
import com.ankita.h2sdosimeter.dto.CalibrationPointResponse;
import com.ankita.h2sdosimeter.service.CalibrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Calibration REST controller.
 *
 * Base path: /api/calibration
 *
 * Manages the server-side calibration curve used to convert
 * colour-difference values into estimated ppm.hr exposures.
 *
 * SAFETY NOTE: Calibration data entered here directly affects all
 * ppm.hr estimates. Only points derived from verified laboratory or
 * certified-detector reference measurements should be added.
 */
@RestController
@RequestMapping("/calibration")
public class CalibrationController {

    private final CalibrationService calibService;

    public CalibrationController(CalibrationService calibService) {
        this.calibService = calibService;
    }

    /**
     * POST /api/calibration/points — add a calibration point.
     * Body: { colourDifference, knownPpmHr, label, calibrationVersion, addedBy }
     */
    @PostMapping("/points")
    public ResponseEntity<ApiResponse<CalibrationPointResponse>> addPoint(
            @Valid @RequestBody CalibrationPointRequest req) {
        CalibrationPointResponse p = calibService.addPoint(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Calibration point added", p));
    }

    /**
     * GET /api/calibration/points?version=default
     * Returns active calibration points for a version (sorted by colour diff).
     */
    @GetMapping("/points")
    public ApiResponse<List<CalibrationPointResponse>> getActivePoints(
            @RequestParam(required = false, defaultValue = "default") String version) {
        return ApiResponse.ok(calibService.getActivePoints(version));
    }

    /**
     * GET /api/calibration/points/all?version=default
     * Returns all points including inactive (for management).
     */
    @GetMapping("/points/all")
    public ApiResponse<List<CalibrationPointResponse>> getAllPoints(
            @RequestParam(required = false, defaultValue = "default") String version) {
        return ApiResponse.ok(calibService.getAllPoints(version));
    }

    /**
     * PUT /api/calibration/points/{id} — update a calibration point.
     */
    @PutMapping("/points/{id}")
    public ApiResponse<CalibrationPointResponse> updatePoint(
            @PathVariable Long id,
            @Valid @RequestBody CalibrationPointRequest req) {
        return ApiResponse.ok("Calibration point updated", calibService.updatePoint(id, req));
    }

    /**
     * DELETE /api/calibration/points/{id} — permanently delete a calibration point.
     */
    @DeleteMapping("/points/{id}")
    public ApiResponse<Void> deletePoint(@PathVariable Long id) {
        calibService.deletePoint(id);
        return ApiResponse.ok("Calibration point deleted", null);
    }

    /**
     * PATCH /api/calibration/points/{id}/deactivate — soft-delete a point.
     */
    @PatchMapping("/points/{id}/deactivate")
    public ApiResponse<Void> deactivatePoint(@PathVariable Long id) {
        calibService.deactivatePoint(id);
        return ApiResponse.ok("Calibration point deactivated", null);
    }

    /**
     * GET /api/calibration/status?version=default
     * Returns whether the server has enough points to do conversions.
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus(
            @RequestParam(required = false, defaultValue = "default") String version) {
        boolean calibrated = calibService.isCalibrated(version);
        List<CalibrationPointResponse> points = calibService.getActivePoints(version);
        return ApiResponse.ok(Map.of(
                "calibrated",   calibrated,
                "version",      version,
                "pointCount",   points.size(),
                "minRequired",  CalibrationService.MIN_POINTS,
                "message",      calibrated
                        ? "Calibration active. ppm.hr conversion is available."
                        : "Calibration required. Add at least "
                          + CalibrationService.MIN_POINTS + " reference points."
        ));
    }

    /**
     * GET /api/calibration/convert?diff=42.5&version=default
     * Test endpoint: convert a colour difference to ppm.hr using server calibration.
     */
    @GetMapping("/convert")
    public ApiResponse<Map<String, Object>> convert(
            @RequestParam double diff,
            @RequestParam(required = false, defaultValue = "default") String version) {

        CalibrationService.ConversionResult result = calibService.convert(diff, version);
        return ApiResponse.ok(Map.of(
                "colourDifference",   diff,
                "calibrationStatus",  result.calibrationStatus,
                "estimatedPpmHr",     result.ppmHr != null ? result.ppmHr : "N/A",
                "safetyNotice",       result.ppmHr != null
                        ? "ESTIMATE ONLY. Not a validated measurement."
                        : "CALIBRATION REQUIRED. " + result.message
        ));
    }
}
