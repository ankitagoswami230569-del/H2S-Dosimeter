package com.ankita.h2sdosimeter.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * CalibrationPoint entity — one reference data point mapping a measured
 * colour-difference value to a verified H2S exposure (ppm.hr).
 *
 * These points define the calibration curve used to convert raw sensor
 * colour-change readings into estimated ppm.hr values.
 *
 * SAFETY NOTE: Each point must be derived from a badge exposed to a
 * KNOWN H2S concentration for a KNOWN duration, verified by a calibrated
 * industrial detector or accredited laboratory analysis. Points entered
 * from estimated or unverified exposures will degrade accuracy.
 */
@Entity
@Table(name = "calibration_points",
       indexes = {
           @Index(name = "idx_cal_version", columnList = "calibration_version"),
           @Index(name = "idx_cal_active",  columnList = "active"),
           @Index(name = "idx_cal_diff",    columnList = "colour_difference")
       })
public class CalibrationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Euclidean RGB distance from white measured for a badge exposed to
     * the known concentration. Range 0-441.
     */
    @Column(name = "colour_difference", nullable = false, columnDefinition = "DOUBLE")
    @DecimalMin("0.0") @DecimalMax("441.0")
    private Double colourDifference;

    /**
     * Known H2S exposure = concentration (ppm) × duration (hr).
     * Must be verified by laboratory or calibrated detector.
     */
    @Column(name = "known_ppm_hr", nullable = false, columnDefinition = "DOUBLE")
    @PositiveOrZero
    private Double knownPpmHr;

    /** Optional human-readable description of this reference measurement. */
    @Column(name = "label", length = 300)
    @Size(max = 300)
    private String label;

    /**
     * Version tag grouping calibration points into a named set.
     * Clients can request a specific version. "default" is used if none is specified.
     */
    @Column(name = "calibration_version", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String calibrationVersion = "default";

    /** Only active points are used for conversion. Soft-delete support. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Who added this calibration point (worker ID or system). */
    @Column(name = "added_by", length = 100)
    private String addedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------

    public CalibrationPoint() {}

    public CalibrationPoint(Double colourDifference, Double knownPpmHr,
                             String label, String calibrationVersion, String addedBy) {
        this.colourDifference   = colourDifference;
        this.knownPpmHr         = knownPpmHr;
        this.label              = label;
        this.calibrationVersion = calibrationVersion != null ? calibrationVersion : "default";
        this.addedBy            = addedBy;
    }

    // -------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------

    public Long getId()                           { return id; }
    public Double getColourDifference()           { return colourDifference; }
    public void setColourDifference(Double v)     { this.colourDifference = v; }
    public Double getKnownPpmHr()                 { return knownPpmHr; }
    public void setKnownPpmHr(Double v)           { this.knownPpmHr = v; }
    public String getLabel()                      { return label; }
    public void setLabel(String v)                { this.label = v; }
    public String getCalibrationVersion()         { return calibrationVersion; }
    public void setCalibrationVersion(String v)   { this.calibrationVersion = v; }
    public boolean isActive()                     { return active; }
    public void setActive(boolean v)              { this.active = v; }
    public String getAddedBy()                    { return addedBy; }
    public void setAddedBy(String v)              { this.addedBy = v; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public LocalDateTime getUpdatedAt()           { return updatedAt; }
}
