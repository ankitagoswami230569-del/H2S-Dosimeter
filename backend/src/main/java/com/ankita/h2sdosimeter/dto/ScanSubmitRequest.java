package com.ankita.h2sdosimeter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for submitting one scan result from the Android app.
 *
 * The Android app performs all image analysis on-device and sends
 * the pipeline outputs here for persistence.
 */
public class ScanSubmitRequest {

    // ---------------------------------------------------------------
    // Worker / badge context
    // ---------------------------------------------------------------

    @NotBlank(message = "workerId is required")
    @Size(max = 50)
    private String workerId;

    @NotBlank(message = "badgeId is required")
    @Size(max = 50)
    private String badgeId;

    @Size(max = 100)
    private String shiftName;

    @Size(max = 50)
    private String shiftId;

    /** ISO-8601 timestamp string from the Android device clock. */
    private String scanTimestamp;

    /** Optional image reference (filename, path, or upload URL). */
    @Size(max = 500)
    private String imageRef;

    // ---------------------------------------------------------------
    // Analysis pipeline output
    // ---------------------------------------------------------------

    /**
     * Analysis pipeline status: SUCCESS | QUALITY_REJECTED |
     * REGION_NOT_FOUND | DEMO | ERROR
     */
    @NotBlank(message = "analysisStatus is required")
    private String analysisStatus;

    @Size(max = 500)
    private String analysisStatusMessage;

    // Raw sensor RGB
    private Integer rawSensorR;
    private Integer rawSensorG;
    private Integer rawSensorB;

    // Reference region RGB
    private Integer referenceR;
    private Integer referenceG;
    private Integer referenceB;

    // Corrected sensor RGB
    private Integer correctedSensorR;
    private Integer correctedSensorG;
    private Integer correctedSensorB;

    /** Colour difference (0-441). Required when analysisStatus = SUCCESS. */
    private Double colourDifference;

    private Double brightness;
    private Double sharpness;

    @Size(max = 30)
    private String imageQualityLabel;

    // ---------------------------------------------------------------
    // Calibration / ppm.hr
    // ---------------------------------------------------------------

    /**
     * CALIBRATED | UNCALIBRATED | EXTRAPOLATED
     * Sent by the Android app based on its local calibration state.
     */
    private String calibrationStatus;

    /**
     * Estimated ppm.hr from Android on-device calibration.
     * Null when calibrationStatus = UNCALIBRATED.
     * MUST be labelled as estimate — not a validated measurement.
     */
    private Double estimatedPpmHr;

    /**
     * Version tag of the calibration used on-device.
     * "local-device" if using Android SharedPreferences calibration.
     */
    @Size(max = 50)
    private String calibrationVersion;

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public String getWorkerId()              { return workerId; }
    public void setWorkerId(String v)        { this.workerId = v; }
    public String getBadgeId()               { return badgeId; }
    public void setBadgeId(String v)         { this.badgeId = v; }
    public String getShiftName()             { return shiftName; }
    public void setShiftName(String v)       { this.shiftName = v; }
    public String getShiftId()               { return shiftId; }
    public void setShiftId(String v)         { this.shiftId = v; }
    public String getScanTimestamp()         { return scanTimestamp; }
    public void setScanTimestamp(String v)   { this.scanTimestamp = v; }
    public String getImageRef()              { return imageRef; }
    public void setImageRef(String v)        { this.imageRef = v; }
    public String getAnalysisStatus()        { return analysisStatus; }
    public void setAnalysisStatus(String v)  { this.analysisStatus = v; }
    public String getAnalysisStatusMessage() { return analysisStatusMessage; }
    public void setAnalysisStatusMessage(String v) { this.analysisStatusMessage = v; }
    public Integer getRawSensorR()           { return rawSensorR; }
    public void setRawSensorR(Integer v)     { this.rawSensorR = v; }
    public Integer getRawSensorG()           { return rawSensorG; }
    public void setRawSensorG(Integer v)     { this.rawSensorG = v; }
    public Integer getRawSensorB()           { return rawSensorB; }
    public void setRawSensorB(Integer v)     { this.rawSensorB = v; }
    public Integer getReferenceR()           { return referenceR; }
    public void setReferenceR(Integer v)     { this.referenceR = v; }
    public Integer getReferenceG()           { return referenceG; }
    public void setReferenceG(Integer v)     { this.referenceG = v; }
    public Integer getReferenceB()           { return referenceB; }
    public void setReferenceB(Integer v)     { this.referenceB = v; }
    public Integer getCorrectedSensorR()     { return correctedSensorR; }
    public void setCorrectedSensorR(Integer v){ this.correctedSensorR = v; }
    public Integer getCorrectedSensorG()     { return correctedSensorG; }
    public void setCorrectedSensorG(Integer v){ this.correctedSensorG = v; }
    public Integer getCorrectedSensorB()     { return correctedSensorB; }
    public void setCorrectedSensorB(Integer v){ this.correctedSensorB = v; }
    public Double getColourDifference()      { return colourDifference; }
    public void setColourDifference(Double v){ this.colourDifference = v; }
    public Double getBrightness()            { return brightness; }
    public void setBrightness(Double v)      { this.brightness = v; }
    public Double getSharpness()             { return sharpness; }
    public void setSharpness(Double v)       { this.sharpness = v; }
    public String getImageQualityLabel()     { return imageQualityLabel; }
    public void setImageQualityLabel(String v){ this.imageQualityLabel = v; }
    public String getCalibrationStatus()     { return calibrationStatus; }
    public void setCalibrationStatus(String v){ this.calibrationStatus = v; }
    public Double getEstimatedPpmHr()        { return estimatedPpmHr; }
    public void setEstimatedPpmHr(Double v)  { this.estimatedPpmHr = v; }
    public String getCalibrationVersion()    { return calibrationVersion; }
    public void setCalibrationVersion(String v){ this.calibrationVersion = v; }
}
