package com.ankita.h2sdosimeter.dto;

import com.ankita.h2sdosimeter.entity.ScanRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for a ScanRecord.
 * Always includes calibrationStatus so the client knows whether
 * estimatedPpmHr is valid or not.
 */
public class ScanRecordResponse {

    private Long          id;
    private String        workerId;
    private String        badgeId;
    private String        shiftName;
    private LocalDate     scanDate;
    private LocalDateTime scanTimestamp;
    private String        imageRef;

    // Analysis pipeline output
    private String  analysisStatus;
    private String  analysisStatusMessage;
    private Integer rawSensorR;
    private Integer rawSensorG;
    private Integer rawSensorB;
    private Integer referenceR;
    private Integer referenceG;
    private Integer referenceB;
    private Integer correctedSensorR;
    private Integer correctedSensorG;
    private Integer correctedSensorB;
    private Double  colourDifference;
    private Double  brightness;
    private Double  sharpness;
    private String  imageQualityLabel;

    // ArUco reference-card detection + reference-scale reading
    private Boolean cardDetected;
    private Double  detectionConfidence;
    private Double  scalePosition;
    private Double  nearestSwatchDeltaE;
    private Integer expiryR;
    private Integer expiryG;
    private Integer expiryB;

    // Calibration
    private String  calibrationStatus;
    private Double  estimatedPpmHr;        // null when UNCALIBRATED
    private String  calibrationVersion;
    private String  exposureCategory;

    // Safety notice always returned
    private String safetyNotice;

    public static ScanRecordResponse from(ScanRecord s) {
        ScanRecordResponse r = new ScanRecordResponse();
        r.id                   = s.getId();
        r.workerId             = s.getWorker().getWorkerId();
        r.badgeId              = s.getBadgeId();
        r.shiftName            = s.getShiftName();
        r.scanDate             = s.getScanDate();
        r.scanTimestamp        = s.getScanTimestamp();
        r.imageRef             = s.getImageRef();
        r.analysisStatus       = s.getAnalysisStatus() != null ? s.getAnalysisStatus().name() : null;
        r.analysisStatusMessage = s.getAnalysisStatusMessage();
        r.rawSensorR           = s.getRawSensorR();
        r.rawSensorG           = s.getRawSensorG();
        r.rawSensorB           = s.getRawSensorB();
        r.referenceR           = s.getReferenceR();
        r.referenceG           = s.getReferenceG();
        r.referenceB           = s.getReferenceB();
        r.correctedSensorR     = s.getCorrectedSensorR();
        r.correctedSensorG     = s.getCorrectedSensorG();
        r.correctedSensorB     = s.getCorrectedSensorB();
        r.colourDifference     = s.getColourDifference();
        r.brightness           = s.getBrightness();
        r.sharpness            = s.getSharpness();
        r.imageQualityLabel    = s.getImageQualityLabel();
        r.cardDetected         = s.getCardDetected();
        r.detectionConfidence  = s.getDetectionConfidence();
        r.scalePosition        = s.getScalePosition();
        r.nearestSwatchDeltaE  = s.getNearestSwatchDeltaE();
        r.expiryR              = s.getExpiryR();
        r.expiryG              = s.getExpiryG();
        r.expiryB              = s.getExpiryB();
        r.calibrationStatus    = s.getCalibrationStatus() != null
                ? s.getCalibrationStatus().name() : null;
        r.estimatedPpmHr       = s.getEstimatedPpmHr();
        r.calibrationVersion   = s.getCalibrationVersion();
        r.exposureCategory     = s.getExposureCategory() != null
                ? s.getExposureCategory().name() : null;
        r.safetyNotice         = buildSafetyNotice(s);
        return r;
    }

    private static String buildSafetyNotice(ScanRecord s) {
        if (s.getCalibrationStatus() == ScanRecord.CalibrationStatus.UNCALIBRATED) {
            return "CALIBRATION REQUIRED: The colour difference value cannot be converted " +
                   "to ppm.hr. Add calibration reference points before using this value " +
                   "for any occupational-health assessment.";
        }
        if (s.getCalibrationStatus() == ScanRecord.CalibrationStatus.EXTRAPOLATED) {
            return "ESTIMATE (extrapolated): The measured colour difference is outside " +
                   "the calibration range. Confidence is lower. This value must not be " +
                   "used for safety-critical decisions without independent validation.";
        }
        return "ESTIMATE: This value is derived from a calibration curve and is not a " +
               "direct, validated measurement of H2S absorption. It must not be used as " +
               "the sole basis for occupational-health decisions.";
    }

    // Getters
    public Long          getId()                   { return id; }
    public String        getWorkerId()             { return workerId; }
    public String        getBadgeId()              { return badgeId; }
    public String        getShiftName()            { return shiftName; }
    public LocalDate     getScanDate()             { return scanDate; }
    public LocalDateTime getScanTimestamp()        { return scanTimestamp; }
    public String        getImageRef()             { return imageRef; }
    public String        getAnalysisStatus()       { return analysisStatus; }
    public String        getAnalysisStatusMessage(){ return analysisStatusMessage; }
    public Integer       getRawSensorR()           { return rawSensorR; }
    public Integer       getRawSensorG()           { return rawSensorG; }
    public Integer       getRawSensorB()           { return rawSensorB; }
    public Integer       getReferenceR()           { return referenceR; }
    public Integer       getReferenceG()           { return referenceG; }
    public Integer       getReferenceB()           { return referenceB; }
    public Integer       getCorrectedSensorR()     { return correctedSensorR; }
    public Integer       getCorrectedSensorG()     { return correctedSensorG; }
    public Integer       getCorrectedSensorB()     { return correctedSensorB; }
    public Double        getColourDifference()     { return colourDifference; }
    public Double        getBrightness()           { return brightness; }
    public Double        getSharpness()            { return sharpness; }
    public String        getImageQualityLabel()    { return imageQualityLabel; }
    public Boolean       getCardDetected()         { return cardDetected; }
    public Double        getDetectionConfidence()  { return detectionConfidence; }
    public Double        getScalePosition()        { return scalePosition; }
    public Double        getNearestSwatchDeltaE()  { return nearestSwatchDeltaE; }
    public Integer       getExpiryR()              { return expiryR; }
    public Integer       getExpiryG()              { return expiryG; }
    public Integer       getExpiryB()              { return expiryB; }
    public String        getCalibrationStatus()    { return calibrationStatus; }
    public Double        getEstimatedPpmHr()       { return estimatedPpmHr; }
    public String        getCalibrationVersion()   { return calibrationVersion; }
    public String        getExposureCategory()     { return exposureCategory; }
    public String        getSafetyNotice()         { return safetyNotice; }
}
