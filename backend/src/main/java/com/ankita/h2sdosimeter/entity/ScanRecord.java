package com.ankita.h2sdosimeter.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ScanRecord entity — one badge scan event.
 *
 * Stores the full colour analysis pipeline output (raw RGB, reference RGB,
 * corrected RGB, colour difference, brightness, sharpness, image quality),
 * plus calibration-derived ppm.hr estimate if available.
 *
 * SAFETY NOTE:
 * estimatedPpmHr is populated only when calibrationStatus = CALIBRATED.
 * When calibrationStatus = UNCALIBRATED the ppmHr field is null and must
 * never be presented as a real H2S measurement.
 */
@Entity
@Table(name = "scan_records",
       indexes = {
           @Index(name = "idx_scan_worker_id",  columnList = "worker_id"),
           @Index(name = "idx_scan_badge_id",   columnList = "badge_id"),
           @Index(name = "idx_scan_scan_date",  columnList = "scan_date"),
           @Index(name = "idx_scan_shift_name", columnList = "shift_name"),
           @Index(name = "idx_scan_worker_date", columnList = "worker_id,scan_date")
       })
public class ScanRecord {

    // ------------------------------------------------------------------
    // Enum types
    // ------------------------------------------------------------------

    public enum AnalysisStatus {
        SUCCESS, QUALITY_REJECTED, REGION_NOT_FOUND, CARD_NOT_DETECTED, DEMO, ERROR
    }

    public enum CalibrationStatus {
        CALIBRATED,        // ppmHr estimate available
        UNCALIBRATED,      // no calibration data — ppmHr is null
        EXTRAPOLATED       // ppmHr derived outside calibration range (lower confidence)
    }

    public enum ExposureCategory { LOW, ELEVATED, HIGH, UNKNOWN }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------------------------------------------
    // Worker / Badge / Shift context
    // ------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "badge_id", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String badgeId;

    @Column(name = "shift_name", length = 100)
    private String shiftName;

    @Column(name = "shift_id", length = 50)
    private String shiftId;

    @Column(name = "scan_date", nullable = false)
    private LocalDate scanDate;

    @Column(name = "scan_timestamp", nullable = false)
    private LocalDateTime scanTimestamp;

    // ------------------------------------------------------------------
    // Image capture reference
    // ------------------------------------------------------------------

    /** Optional reference to the captured image filename / path / URL. */
    @Column(name = "image_ref", length = 500)
    private String imageRef;

    // ------------------------------------------------------------------
    // Raw analysis pipeline output
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 30)
    private AnalysisStatus analysisStatus;

    @Column(name = "analysis_status_message", length = 500)
    private String analysisStatusMessage;

    // Raw sensor RGB
    @Column(name = "raw_sensor_r") private Integer rawSensorR;
    @Column(name = "raw_sensor_g") private Integer rawSensorG;
    @Column(name = "raw_sensor_b") private Integer rawSensorB;

    // Reference region RGB
    @Column(name = "reference_r") private Integer referenceR;
    @Column(name = "reference_g") private Integer referenceG;
    @Column(name = "reference_b") private Integer referenceB;

    // Corrected (white-balanced) sensor RGB
    @Column(name = "corrected_sensor_r") private Integer correctedSensorR;
    @Column(name = "corrected_sensor_g") private Integer correctedSensorG;
    @Column(name = "corrected_sensor_b") private Integer correctedSensorB;

    /** Euclidean RGB distance from white (0-441). Core colour-change indicator. */
    @Column(name = "colour_difference", columnDefinition = "DOUBLE")
    private Double colourDifference;

    @Column(name = "brightness", columnDefinition = "DOUBLE")
    private Double brightness;

    @Column(name = "sharpness", columnDefinition = "DOUBLE")
    private Double sharpness;

    @Column(name = "image_quality_label", length = 30)
    private String imageQualityLabel;

    // ------------------------------------------------------------------
    // ArUco reference-card detection + reference-scale reading
    // ------------------------------------------------------------------

    /** True when CardDetector located all reference-card markers on-device. */
    @Column(name = "card_detected")
    private Boolean cardDetected;

    /** Card detection confidence, 0.0-1.0, as reported by the Android app. */
    @Column(name = "detection_confidence", columnDefinition = "DOUBLE")
    private Double detectionConfidence;

    /**
     * Interpolated position along the printed dose scale (dose-index
     * units), from the Android app's ScaleReader. Scale POSITION, NOT a
     * ppm.hr value - see CalibrationService / CalibrationCurve on the
     * Android side for the conversion.
     */
    @Column(name = "scale_position", columnDefinition = "DOUBLE")
    private Double scalePosition;

    /** CIE76 delta-E from the corrected strip colour to the nearest swatch. */
    @Column(name = "nearest_swatch_delta_e", columnDefinition = "DOUBLE")
    private Double nearestSwatchDeltaE;

    // Expiry indicator patch colour (after per-photo colour correction)
    @Column(name = "expiry_r") private Integer expiryR;
    @Column(name = "expiry_g") private Integer expiryG;
    @Column(name = "expiry_b") private Integer expiryB;

    // ------------------------------------------------------------------
    // Calibration / ppm.hr estimate
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "calibration_status", nullable = false, length = 20)
    private CalibrationStatus calibrationStatus = CalibrationStatus.UNCALIBRATED;

    /**
     * Estimated H2S exposure in ppm.hr — populated ONLY when
     * calibrationStatus = CALIBRATED or EXTRAPOLATED.
     * NULL when calibrationStatus = UNCALIBRATED.
     * MUST NOT be used for safety decisions without independent validation.
     */
    @Column(name = "estimated_ppm_hr", columnDefinition = "DOUBLE")
    private Double estimatedPpmHr;

    /** Identifier of the calibration version used for conversion. */
    @Column(name = "calibration_version", length = 50)
    private String calibrationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "exposure_category", length = 20)
    private ExposureCategory exposureCategory = ExposureCategory.UNKNOWN;

    // ------------------------------------------------------------------
    // Audit
    // ------------------------------------------------------------------

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (scanTimestamp == null) scanTimestamp = LocalDateTime.now();
        if (scanDate == null) scanDate = scanTimestamp.toLocalDate();
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    public ScanRecord() {}

    // ------------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------------

    public Long getId()                              { return id; }
    public Worker getWorker()                        { return worker; }
    public void setWorker(Worker v)                  { this.worker = v; }
    public String getBadgeId()                       { return badgeId; }
    public void setBadgeId(String v)                 { this.badgeId = v; }
    public String getShiftName()                     { return shiftName; }
    public void setShiftName(String v)               { this.shiftName = v; }
    public String getShiftId()                       { return shiftId; }
    public void setShiftId(String v)                 { this.shiftId = v; }
    public LocalDate getScanDate()                   { return scanDate; }
    public void setScanDate(LocalDate v)             { this.scanDate = v; }
    public LocalDateTime getScanTimestamp()          { return scanTimestamp; }
    public void setScanTimestamp(LocalDateTime v)    { this.scanTimestamp = v; if (scanDate == null) scanDate = v.toLocalDate(); }
    public String getImageRef()                      { return imageRef; }
    public void setImageRef(String v)                { this.imageRef = v; }
    public AnalysisStatus getAnalysisStatus()        { return analysisStatus; }
    public void setAnalysisStatus(AnalysisStatus v)  { this.analysisStatus = v; }
    public String getAnalysisStatusMessage()         { return analysisStatusMessage; }
    public void setAnalysisStatusMessage(String v)   { this.analysisStatusMessage = v; }
    public Integer getRawSensorR()                   { return rawSensorR; }
    public void setRawSensorR(Integer v)             { this.rawSensorR = v; }
    public Integer getRawSensorG()                   { return rawSensorG; }
    public void setRawSensorG(Integer v)             { this.rawSensorG = v; }
    public Integer getRawSensorB()                   { return rawSensorB; }
    public void setRawSensorB(Integer v)             { this.rawSensorB = v; }
    public Integer getReferenceR()                   { return referenceR; }
    public void setReferenceR(Integer v)             { this.referenceR = v; }
    public Integer getReferenceG()                   { return referenceG; }
    public void setReferenceG(Integer v)             { this.referenceG = v; }
    public Integer getReferenceB()                   { return referenceB; }
    public void setReferenceB(Integer v)             { this.referenceB = v; }
    public Integer getCorrectedSensorR()             { return correctedSensorR; }
    public void setCorrectedSensorR(Integer v)       { this.correctedSensorR = v; }
    public Integer getCorrectedSensorG()             { return correctedSensorG; }
    public void setCorrectedSensorG(Integer v)       { this.correctedSensorG = v; }
    public Integer getCorrectedSensorB()             { return correctedSensorB; }
    public void setCorrectedSensorB(Integer v)       { this.correctedSensorB = v; }
    public Double getColourDifference()              { return colourDifference; }
    public void setColourDifference(Double v)        { this.colourDifference = v; }
    public Double getBrightness()                    { return brightness; }
    public void setBrightness(Double v)              { this.brightness = v; }
    public Double getSharpness()                     { return sharpness; }
    public void setSharpness(Double v)               { this.sharpness = v; }
    public String getImageQualityLabel()             { return imageQualityLabel; }
    public void setImageQualityLabel(String v)       { this.imageQualityLabel = v; }
    public Boolean getCardDetected()                 { return cardDetected; }
    public void setCardDetected(Boolean v)           { this.cardDetected = v; }
    public Double getDetectionConfidence()           { return detectionConfidence; }
    public void setDetectionConfidence(Double v)     { this.detectionConfidence = v; }
    public Double getScalePosition()                 { return scalePosition; }
    public void setScalePosition(Double v)           { this.scalePosition = v; }
    public Double getNearestSwatchDeltaE()            { return nearestSwatchDeltaE; }
    public void setNearestSwatchDeltaE(Double v)     { this.nearestSwatchDeltaE = v; }
    public Integer getExpiryR()                       { return expiryR; }
    public void setExpiryR(Integer v)                { this.expiryR = v; }
    public Integer getExpiryG()                       { return expiryG; }
    public void setExpiryG(Integer v)                { this.expiryG = v; }
    public Integer getExpiryB()                       { return expiryB; }
    public void setExpiryB(Integer v)                { this.expiryB = v; }
    public CalibrationStatus getCalibrationStatus()  { return calibrationStatus; }
    public void setCalibrationStatus(CalibrationStatus v){ this.calibrationStatus = v; }
    public Double getEstimatedPpmHr()                { return estimatedPpmHr; }
    public void setEstimatedPpmHr(Double v)          { this.estimatedPpmHr = v; }
    public String getCalibrationVersion()            { return calibrationVersion; }
    public void setCalibrationVersion(String v)      { this.calibrationVersion = v; }
    public ExposureCategory getExposureCategory()    { return exposureCategory; }
    public void setExposureCategory(ExposureCategory v){ this.exposureCategory = v; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
}
