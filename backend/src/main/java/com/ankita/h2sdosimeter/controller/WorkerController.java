package com.ankita.h2sdosimeter.controller;

import com.ankita.h2sdosimeter.dto.ApiResponse;
import com.ankita.h2sdosimeter.dto.WorkerRequest;
import com.ankita.h2sdosimeter.dto.WorkerResponse;
import com.ankita.h2sdosimeter.service.WorkerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Worker REST controller.
 *
 * Base path: /api/workers
 */
@RestController
@RequestMapping("/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    /** POST /api/workers — register a new worker */
    @PostMapping
    public ResponseEntity<ApiResponse<WorkerResponse>> register(
            @Valid @RequestBody WorkerRequest req) {
        WorkerResponse w = workerService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Worker registered", w));
    }

    /** GET /api/workers — list all active workers */
    @GetMapping
    public ApiResponse<List<WorkerResponse>> getAllActive() {
        return ApiResponse.ok(workerService.getAllActive());
    }

    /** GET /api/workers/{workerId} — get worker by worker ID */
    @GetMapping("/{workerId}")
    public ApiResponse<WorkerResponse> getByWorkerId(@PathVariable String workerId) {
        return ApiResponse.ok(workerService.getByWorkerId(workerId));
    }

    /** GET /api/workers/by-badge/{badgeId} — get worker assigned to a badge */
    @GetMapping("/by-badge/{badgeId}")
    public ApiResponse<WorkerResponse> getByBadgeId(@PathVariable String badgeId) {
        return ApiResponse.ok(workerService.getByBadgeId(badgeId));
    }

    /** PUT /api/workers/{workerId} — update worker details */
    @PutMapping("/{workerId}")
    public ApiResponse<WorkerResponse> update(
            @PathVariable String workerId,
            @Valid @RequestBody WorkerRequest req) {
        req.setWorkerId(workerId); // ensure path param wins
        return ApiResponse.ok("Worker updated", workerService.update(workerId, req));
    }

    /** PATCH /api/workers/{workerId}/badge — assign a badge to a worker */
    @PatchMapping("/{workerId}/badge")
    public ApiResponse<WorkerResponse> assignBadge(
            @PathVariable String workerId,
            @RequestBody Map<String, String> body) {
        String badgeId = body.get("badgeId");
        if (badgeId == null || badgeId.isBlank()) {
            throw new IllegalArgumentException("badgeId is required");
        }
        return ApiResponse.ok("Badge assigned", workerService.assignBadge(workerId, badgeId));
    }

    /** DELETE /api/workers/{workerId} — soft-delete (deactivate) a worker */
    @DeleteMapping("/{workerId}")
    public ApiResponse<Void> deactivate(@PathVariable String workerId) {
        workerService.deactivate(workerId);
        return ApiResponse.ok("Worker deactivated", null);
    }
}
