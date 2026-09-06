package com.ankita.h2sdosimeter.dto;

import java.time.LocalDate;

/**
 * Daily or shift exposure summary for a worker.
 * Aggregates multiple scans into a total estimated ppm.hr.
 *
 * SAFETY NOTE: totalEstimatedPpmHr is the sum of calibrated estimates
 * from individual scans. It is an estimate only.
 */
public class ExposureSummaryResponse {

    private String    workerId;
    private LocalDate date;
    private String    shiftName;     // null for daily summary
    private int       scanCount;
    private Double    totalEstimatedPpmHr;  // null if no calibrated scans
    private String    calibrationStatus;   // CALIBRATED / UNCALIBRATED / PARTIAL
    private String    safetyNotice;

    public ExposureSummaryResponse() {}

    public ExposureSummaryResponse(String workerId, LocalDate date,
                                    String shiftName, int scanCount,
                                    Double totalEstimatedPpmHr,
                                    String calibrationStatus) {
        this.workerId            = workerId;
        this.date                = date;
        this.shiftName           = shiftName;
        this.scanCount           = scanCount;
        this.totalEstimatedPpmHr = totalEstimatedPpmHr;
        this.calibrationStatus   = calibrationStatus;
        this.safetyNotice        = buildNotice(calibrationStatus);
    }

    private static String buildNotice(String status) {
        if ("UNCALIBRATED".equals(status)) {
            return "CALIBRATION REQUIRED: No ppm.hr estimate available. Add calibration " +
                   "reference points to enable exposure summaries.";
        }
        if ("PARTIAL".equals(status)) {
            return "PARTIAL CALIBRATION: Some scans in this period were uncalibrated. " +
                   "The total shown is a partial estimate only.";
        }
        return "ESTIMATE: Total is the sum of calibrated estimates from individual scans. " +
               "This is not a validated occupational-health measurement.";
    }

    public String  getWorkerId()             { return workerId; }
    public void setWorkerId(String v)        { this.workerId = v; }
    public LocalDate getDate()               { return date; }
    public void setDate(LocalDate v)         { this.date = v; }
    public String  getShiftName()            { return shiftName; }
    public void setShiftName(String v)       { this.shiftName = v; }
    public int     getScanCount()            { return scanCount; }
    public void setScanCount(int v)          { this.scanCount = v; }
    public Double  getTotalEstimatedPpmHr()  { return totalEstimatedPpmHr; }
    public void setTotalEstimatedPpmHr(Double v){ this.totalEstimatedPpmHr = v; }
    public String  getCalibrationStatus()    { return calibrationStatus; }
    public void setCalibrationStatus(String v){ this.calibrationStatus = v; }
    public String  getSafetyNotice()         { return safetyNotice; }
    public void setSafetyNotice(String v)    { this.safetyNotice = v; }
}
