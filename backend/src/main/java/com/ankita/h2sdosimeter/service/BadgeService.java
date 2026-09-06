package com.ankita.h2sdosimeter.service;

import com.ankita.h2sdosimeter.entity.Badge;
import com.ankita.h2sdosimeter.exception.ConflictException;
import com.ankita.h2sdosimeter.exception.ResourceNotFoundException;
import com.ankita.h2sdosimeter.repository.BadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class BadgeService {

    private final BadgeRepository badgeRepo;

    public BadgeService(BadgeRepository badgeRepo) {
        this.badgeRepo = badgeRepo;
    }

    public Badge register(String badgeId, LocalDate manufacturingDate,
                           LocalDate expiryDate, String batchNumber, String model) {
        if (badgeRepo.existsByBadgeId(badgeId)) {
            throw new ConflictException("Badge already registered: " + badgeId);
        }
        Badge b = new Badge(badgeId, manufacturingDate, expiryDate, batchNumber, model);
        b.deriveStatus();
        return badgeRepo.save(b);
    }

    @Transactional(readOnly = true)
    public Badge getByBadgeId(String badgeId) {
        return badgeRepo.findByBadgeId(badgeId)
                .orElseThrow(() -> new ResourceNotFoundException("Badge not found: " + badgeId));
    }

    /**
     * Returns a validation result map. Used by the /badges/{id}/validate endpoint.
     * Refreshes the derived status before returning.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validate(String badgeId) {
        Badge b = getByBadgeId(badgeId);
        b.deriveStatus();

        boolean valid = b.getStatus() == Badge.BadgeStatus.VALID;
        return Map.of(
                "badgeId",      b.getBadgeId(),
                "status",       b.getStatus().name(),
                "valid",        valid,
                "expiryDate",   b.getExpiryDate() != null ? b.getExpiryDate().toString() : "N/A",
                "badgeModel",   b.getBadgeModel() != null ? b.getBadgeModel() : "Unknown",
                "message",      valid
                        ? "Badge is valid and ready for use."
                        : "Badge is " + b.getStatus().name().toLowerCase() + " and cannot be used."
        );
    }

    @Transactional(readOnly = true)
    public List<Badge> getAll() {
        return badgeRepo.findAll();
    }
}
