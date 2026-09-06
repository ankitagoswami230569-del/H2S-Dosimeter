package com.ankita.h2sdosimeter.model;

/**
 * Badge - represents an H2S colorimetric dosimeter wristband badge.
 * Frontend phase: all status values are simulated/mock.
 *
 * IMPORTANT: badge validity and sensor status in this class are
 * frontend simulations only. Real badge analysis requires validated
 * laboratory calibration.
 */
public class Badge {

    public enum BadgeStatus {
        VALID, EXPIRED, UNKNOWN
    }

    private String badgeId;
    private String manufacturingDate;
    private String expiryDate;
    private BadgeStatus status;
    private boolean sensorReady;
    private boolean referenceScaleDetected;
    private boolean expiryIndicatorValid;

    // Note: these booleans are frontend simulation flags only.
    // They do not represent real physical measurements.

    public Badge(String badgeId, String manufacturingDate, String expiryDate,
                 BadgeStatus status, boolean sensorReady,
                 boolean referenceScaleDetected, boolean expiryIndicatorValid) {
        this.badgeId = badgeId;
        this.manufacturingDate = manufacturingDate;
        this.expiryDate = expiryDate;
        this.status = status;
        this.sensorReady = sensorReady;
        this.referenceScaleDetected = referenceScaleDetected;
        this.expiryIndicatorValid = expiryIndicatorValid;
    }

    public String getBadgeId() { return badgeId; }
    public String getManufacturingDate() { return manufacturingDate; }
    public String getExpiryDate() { return expiryDate; }
    public BadgeStatus getStatus() { return status; }
    public boolean isSensorReady() { return sensorReady; }
    public boolean isReferenceScaleDetected() { return referenceScaleDetected; }
    public boolean isExpiryIndicatorValid() { return expiryIndicatorValid; }

    public void setBadgeId(String badgeId) { this.badgeId = badgeId; }
    public void setManufacturingDate(String manufacturingDate) { this.manufacturingDate = manufacturingDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public void setStatus(BadgeStatus status) { this.status = status; }
    public void setSensorReady(boolean sensorReady) { this.sensorReady = sensorReady; }
    public void setReferenceScaleDetected(boolean referenceScaleDetected) { this.referenceScaleDetected = referenceScaleDetected; }
    public void setExpiryIndicatorValid(boolean expiryIndicatorValid) { this.expiryIndicatorValid = expiryIndicatorValid; }
}
