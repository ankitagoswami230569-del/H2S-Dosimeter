package com.ankita.h2sdosimeter.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Badge entity — an H2S colorimetric dosimeter wristband.
 *
 * badgeId is the human-readable identifier printed on the badge
 * (e.g. H2S-BDG-001).
 */
@Entity
@Table(name = "badges",
       indexes = {
           @Index(name = "idx_badge_badge_id", columnList = "badge_id", unique = true)
       })
public class Badge {

    public enum BadgeStatus { VALID, EXPIRED, UNKNOWN, DECOMMISSIONED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "badge_id", nullable = false, unique = true, length = 50)
    @NotBlank
    @Size(max = 50)
    private String badgeId;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BadgeStatus status = BadgeStatus.UNKNOWN;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    @Column(name = "badge_model", length = 100)
    private String badgeModel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Auto-derive status from expiry date
        deriveStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        deriveStatus();
    }

    /** Automatically set status based on expiry date relative to today. */
    public void deriveStatus() {
        if (expiryDate == null) {
            status = BadgeStatus.UNKNOWN;
            return;
        }
        if (status == BadgeStatus.DECOMMISSIONED) return; // never auto-promote
        status = LocalDate.now().isAfter(expiryDate)
                ? BadgeStatus.EXPIRED
                : BadgeStatus.VALID;
    }

    // -------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------

    public Badge() {}

    public Badge(String badgeId, LocalDate manufacturingDate,
                 LocalDate expiryDate, String batchNumber, String badgeModel) {
        this.badgeId           = badgeId;
        this.manufacturingDate = manufacturingDate;
        this.expiryDate        = expiryDate;
        this.batchNumber       = batchNumber;
        this.badgeModel        = badgeModel;
    }

    // -------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------

    public Long getId()                          { return id; }
    public String getBadgeId()                   { return badgeId; }
    public void setBadgeId(String v)             { this.badgeId = v; }
    public LocalDate getManufacturingDate()      { return manufacturingDate; }
    public void setManufacturingDate(LocalDate v){ this.manufacturingDate = v; }
    public LocalDate getExpiryDate()             { return expiryDate; }
    public void setExpiryDate(LocalDate v)       { this.expiryDate = v; }
    public BadgeStatus getStatus()               { return status; }
    public void setStatus(BadgeStatus v)         { this.status = v; }
    public String getBatchNumber()               { return batchNumber; }
    public void setBatchNumber(String v)         { this.batchNumber = v; }
    public String getBadgeModel()                { return badgeModel; }
    public void setBadgeModel(String v)          { this.badgeModel = v; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
}
