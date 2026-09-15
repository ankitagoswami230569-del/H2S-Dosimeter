package com.ankita.h2sdosimeter.model;

import java.io.Serializable;

/**
 * ColourAnalysisResult — every output of the sensor analysis pipeline.
 *
 * A SUCCESS result is ONLY produced when:
 *   1. The sensor strip was geometrically detected.
 *   2. The 3-patch reference scale was detected.
 *   3. Colour was extracted from the DETECTED strip ROI (not a fixed crop).
 *   4. Colour difference was computed from the corrected sensor colour.
 *
 * All other outcomes (no sensor, quality failure, OpenCV unavailable, etc.)
 * produce a non-SUCCESS status and the colourDifference / ppm fields are 0.
 */
public class ColourAnalysisResult implements Serializable {

    public enum Status {
        /** Full pipeline completed with verified sensor detection. */
        SUCCESS,
        /** Image quality too low (dark/blurry/overexposed). */
        QUALITY_REJECTED,
        /** Sensor strip or reference scale not detected. */
        REGION_NOT_FOUND,
        /** Demo path — no real image analysed. */
        DEMO,
        /** Unexpected error. */
        ERROR
    }

    private final Status  status;
    private final String  statusMessage;
    private final boolean isRealCapture;

    private final int rawSensorR, rawSensorG, rawSensorB;
    private final int referenceR, referenceG, referenceB;
    private final int correctedSensorR, correctedSensorG, correctedSensorB;

    private final double colourDifference;   // Euclidean RGB dist from white (0–441), display only
    private final double deltaE;             // CIEDE2000 from white, lighting-corrected: calibration input
    private final double sensorL, sensorA, sensorBLab; // CIE Lab of the corrected strip colour

    private final boolean referenceScaleDetected;
    private final double  brightness;
    private final double  sharpness;
    private final String  imageQualityLabel;

    /**
     * Human-readable debug string from MarkedBadgeDetector.
     * Format: "stripDetected=YES|NO confidence=XX% roi=x,y,w,h reason=..."
     * Shown in the debug overlay during development.
     */
    private final String detectionDebug;

    private ColourAnalysisResult(Builder b) {
        status               = b.status;
        statusMessage        = b.statusMessage;
        isRealCapture        = b.isRealCapture;
        rawSensorR           = b.rawSensorR;
        rawSensorG           = b.rawSensorG;
        rawSensorB           = b.rawSensorB;
        referenceR           = b.referenceR;
        referenceG           = b.referenceG;
        referenceB           = b.referenceB;
        correctedSensorR     = b.correctedSensorR;
        correctedSensorG     = b.correctedSensorG;
        correctedSensorB     = b.correctedSensorB;
        colourDifference     = b.colourDifference;
        deltaE               = b.deltaE;
        sensorL              = b.sensorL;
        sensorA              = b.sensorA;
        sensorBLab           = b.sensorBLab;
        referenceScaleDetected = b.referenceScaleDetected;
        brightness           = b.brightness;
        sharpness            = b.sharpness;
        imageQualityLabel    = b.imageQualityLabel;
        detectionDebug       = b.detectionDebug;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public Status  getStatus()                { return status; }
    public String  getStatusMessage()         { return statusMessage; }
    public boolean isRealCapture()            { return isRealCapture; }
    public int     getRawSensorR()            { return rawSensorR; }
    public int     getRawSensorG()            { return rawSensorG; }
    public int     getRawSensorB()            { return rawSensorB; }
    public int     getReferenceR()            { return referenceR; }
    public int     getReferenceG()            { return referenceG; }
    public int     getReferenceB()            { return referenceB; }
    public int     getCorrectedSensorR()      { return correctedSensorR; }
    public int     getCorrectedSensorG()      { return correctedSensorG; }
    public int     getCorrectedSensorB()      { return correctedSensorB; }
    public double  getColourDifference()      { return colourDifference; }
    public double  getDeltaE()                { return deltaE; }
    public double  getSensorL()               { return sensorL; }
    public double  getSensorA()               { return sensorA; }
    public double  getSensorBLab()            { return sensorBLab; }
    public boolean isReferenceScaleDetected() { return referenceScaleDetected; }
    public double  getBrightness()            { return brightness; }
    public double  getSharpness()             { return sharpness; }
    public String  getImageQualityLabel()     { return imageQualityLabel; }
    public String  getDetectionDebug()        { return detectionDebug != null ? detectionDebug : ""; }

    public String getCorrectedRgbString() {
        return "rgb(" + correctedSensorR + ", " + correctedSensorG + ", " + correctedSensorB + ")";
    }
    public String getRawRgbString() {
        return "rgb(" + rawSensorR + ", " + rawSensorG + ", " + rawSensorB + ")";
    }
    public String getReferenceRgbString() {
        return "rgb(" + referenceR + ", " + referenceG + ", " + referenceB + ")";
    }
    public String getLabString() {
        return String.format("L*=%.1f  a*=%.1f  b*=%.1f", sensorL, sensorA, sensorBLab);
    }

    // ── Static factories ─────────────────────────────────────────────────

    public static ColourAnalysisResult demo() {
        return new Builder()
                .status(Status.DEMO)
                .statusMessage("Demo flow - no image analysed")
                .isRealCapture(false)
                .imageQualityLabel("N/A (Demo)")
                .detectionDebug("DEMO mode")
                .build();
    }

    public static ColourAnalysisResult error(String reason) {
        return new Builder()
                .status(Status.ERROR)
                .statusMessage(reason)
                .isRealCapture(true)
                .imageQualityLabel("Unknown")
                .detectionDebug("ERROR: " + reason)
                .build();
    }

    public static ColourAnalysisResult qualityRejected(String reason,
                                                        double brightness,
                                                        double sharpness) {
        return new Builder()
                .status(Status.QUALITY_REJECTED)
                .statusMessage(reason)
                .isRealCapture(true)
                .brightness(brightness)
                .sharpness(sharpness)
                .imageQualityLabel("Poor")
                .detectionDebug("QUALITY REJECTED: " + reason)
                .build();
    }

    public static ColourAnalysisResult dosimeterNotDetected(String reason) {
        return dosimeterNotDetected(reason, "SENSOR NOT DETECTED: " + reason);
    }

    public static ColourAnalysisResult dosimeterNotDetected(String reason, String debug) {
        return new Builder()
                .status(Status.REGION_NOT_FOUND)
                .statusMessage(reason)
                .isRealCapture(true)
                .imageQualityLabel("Unknown")
                .detectionDebug(debug)
                .build();
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static class Builder {
        Status  status           = Status.ERROR;
        String  statusMessage    = "";
        boolean isRealCapture    = false;
        int rawSensorR, rawSensorG, rawSensorB;
        int referenceR = 255, referenceG = 255, referenceB = 255;
        int correctedSensorR, correctedSensorG, correctedSensorB;
        double  colourDifference;
        double  deltaE;
        double  sensorL = 100.0, sensorA = 0.0, sensorBLab = 0.0;
        boolean referenceScaleDetected = false;
        double  brightness;
        double  sharpness;
        String  imageQualityLabel = "Unknown";
        String  detectionDebug    = "";

        public Builder status(Status s)                  { this.status = s; return this; }
        public Builder statusMessage(String m)           { this.statusMessage = m; return this; }
        public Builder isRealCapture(boolean r)          { this.isRealCapture = r; return this; }
        public Builder rawSensor(int r, int g, int b)    { rawSensorR=r; rawSensorG=g; rawSensorB=b; return this; }
        public Builder reference(int r, int g, int b)    { referenceR=r; referenceG=g; referenceB=b; return this; }
        public Builder corrected(int r, int g, int b)    { correctedSensorR=r; correctedSensorG=g; correctedSensorB=b; return this; }
        public Builder colourDifference(double d)        { this.colourDifference = d; return this; }
        public Builder deltaE(double de)                 { this.deltaE = de; return this; }
        public Builder lab(double L, double a, double b) { sensorL=L; sensorA=a; sensorBLab=b; return this; }
        public Builder referenceScaleDetected(boolean v) { this.referenceScaleDetected = v; return this; }
        public Builder brightness(double v)              { this.brightness = v; return this; }
        public Builder sharpness(double v)               { this.sharpness = v; return this; }
        public Builder imageQualityLabel(String l)       { this.imageQualityLabel = l; return this; }
        public Builder detectionDebug(String d)          { this.detectionDebug = d; return this; }

        public ColourAnalysisResult build() { return new ColourAnalysisResult(this); }
    }
}
