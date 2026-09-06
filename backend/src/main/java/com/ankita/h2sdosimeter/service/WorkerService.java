package com.ankita.h2sdosimeter.service;

import com.ankita.h2sdosimeter.dto.WorkerRequest;
import com.ankita.h2sdosimeter.dto.WorkerResponse;
import com.ankita.h2sdosimeter.entity.Worker;
import com.ankita.h2sdosimeter.exception.ConflictException;
import com.ankita.h2sdosimeter.exception.ResourceNotFoundException;
import com.ankita.h2sdosimeter.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkerService {

    private final WorkerRepository workerRepo;

    public WorkerService(WorkerRepository workerRepo) {
        this.workerRepo = workerRepo;
    }

    // -------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------

    public WorkerResponse register(WorkerRequest req) {
        if (workerRepo.existsByWorkerId(req.getWorkerId())) {
            throw new ConflictException("Worker ID already exists: " + req.getWorkerId());
        }
        if (workerRepo.existsByEmail(req.getEmail())) {
            throw new ConflictException("Email already registered: " + req.getEmail());
        }

        Worker w = new Worker();
        mapRequest(req, w);
        return WorkerResponse.from(workerRepo.save(w));
    }

    // -------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WorkerResponse getByWorkerId(String workerId) {
        return WorkerResponse.from(findWorker(workerId));
    }

    @Transactional(readOnly = true)
    public WorkerResponse getByBadgeId(String badgeId) {
        Worker w = workerRepo.findByBadgeId(badgeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active worker found with badge: " + badgeId));
        return WorkerResponse.from(w);
    }

    @Transactional(readOnly = true)
    public List<WorkerResponse> getAllActive() {
        return workerRepo.findByActiveTrue().stream()
                .map(WorkerResponse::from)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------

    public WorkerResponse update(String workerId, WorkerRequest req) {
        Worker w = findWorker(workerId);

        // Allow email change only if not taken by a different worker
        if (!w.getEmail().equals(req.getEmail())
                && workerRepo.existsByEmail(req.getEmail())) {
            throw new ConflictException("Email already in use: " + req.getEmail());
        }
        mapRequest(req, w);
        return WorkerResponse.from(workerRepo.save(w));
    }

    /** Assign a badge to a worker. Removes the badge from any other worker first. */
    public WorkerResponse assignBadge(String workerId, String badgeId) {
        // Unassign from current holder if different
        workerRepo.findByBadgeId(badgeId).ifPresent(existing -> {
            if (!existing.getWorkerId().equals(workerId)) {
                existing.setBadgeId(null);
                workerRepo.save(existing);
            }
        });

        Worker w = findWorker(workerId);
        w.setBadgeId(badgeId);
        return WorkerResponse.from(workerRepo.save(w));
    }

    /** Soft-delete a worker. */
    public void deactivate(String workerId) {
        Worker w = findWorker(workerId);
        w.setActive(false);
        workerRepo.save(w);
    }

    // -------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------

    Worker findWorker(String workerId) {
        return workerRepo.findByWorkerId(workerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Worker not found: " + workerId));
    }

    private void mapRequest(WorkerRequest req, Worker w) {
        w.setWorkerId(req.getWorkerId());
        w.setName(req.getName());
        w.setEmail(req.getEmail());
        w.setDepartment(req.getDepartment());
        w.setAssignedSite(req.getAssignedSite());
        w.setContactNumber(req.getContactNumber());
        w.setBadgeId(req.getBadgeId());
        if (req.getRole() != null) {
            try {
                w.setRole(Worker.Role.valueOf(req.getRole().toUpperCase()));
            } catch (IllegalArgumentException e) {
                w.setRole(Worker.Role.WORKER);
            }
        }
    }
}
