package com.ankita.h2sdosimeter.model;

import java.io.Serializable;

/**
 * ColourAnalysisResult - carries every output produced by the local
 * on-device colorimetric image analysis pipeline (Phase 4).
 *
 * SAFETY NOTICE:
 * This result is produced by image-processing heuristics running on a
 * consumer smartphone camera. It is NOT a validated occupational-health
 * measurement. The exposure conversion is marked "calibration required"
 * because no laboratory-validated calibration curve exists in this build.
 * Do not use any value in this class for safety-critical decisions.
 */
public class ColourAnalysisResult implements Serializable {

    // ------------------------------------------------------------------
    // Analysis status
    // ------------------------------------------------------------------

    public enum Status {
        /** Full pipeline completed - RGB values are from the real image. */
        SUCCESS,
        /** Image quality too low to analyse (too dark, blurry, etc.). */
        QUALITY_REJECTED,
        /** Sensor or reference region could not be located in the image. */
        REGION_NOT_FOUND,
        /** Demo flow - no real image analysed. */
        DEMO,
        /** Unexpected error during processing. */
        ERROR
    }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    private final Status status;

    /** Human-readable reason for the status (especially on failure). */
    private final String statusMessage;

    /** True when this result came from a real camera capture. */
    private final boolean isRealCapture;

    // Raw pixel values from the sensor region (centre crop of the image)
    private final int rawSensorR;
    private final int rawSensorG;
    private final int rawSensorB;

    // Pixel values from the reference white/neutral region (top-right corner)
    private final int referenceR;
    private final int referenceG;
    private final int referenceB;

    // Sensor values after reference correction (white-balance normalisation)
    private final int correctedSensorR;
    private final int correctedSensorG;
    private final int correctedSensorB;

    /**
     * Euclidean distance in RGB space between the corrected sensor colour
     * and a pure white reference (255, 255, 255). A higher value means the
     * badge has changed colour more - correlated with higher H2S exposure.
     * Range 0-441 (sqrt(255^2 * 3)).
     */
    private final double colourDifference;

    // Image quality metrics
    private final double brightness;      // 0.0 - 255.0
    private final double sharpness;       // Laplacian variance (higher = sharper)
    private final String imageQualityLabel; // "Good" / "Acceptable" / "Poor"

    // ------------------------------------------------------------------
    // Constructor (use Builder)
    // ------------------------------------------------------------------

    private ColourAnalysisResult(Builder b) {
        this.status            = b.status;
        this.statusMessage     = b.statusMessage;
        this.isRealCapture     = b.isRealCapture;
        this.rawSensorR        = b.rawSensorR;
        this.rawSensorG        = b.rawSensorG;
        this.rawSensorB        = b.rawSensorB;
        this.referenceR        = b.referenceR;
        this.referenceG        = b.referenceG;
        this.referenceB        = b.referenceB;
        this.correctedSensorR  = b.correctedSensorR;
        this.correctedSensorG  = b.correctedSensorG;
        this.correctedSensorB  = b.correctedSensorB;
        this.colourDifference  = b.colourDifference;
        this.brightness        = b.brightness;
        this.sharpness         = b.sharpness;
        this.imageQualityLabel = b.imageQualityLabel;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public Status getStatus()            { return status; }
    public String getStatusMessage()     { return statusMessage; }
    public boolean isRealCapture()       { return isRealCapture; }
    public int getRawSensorR()           { return rawSensorR; }
    public int getRawSensorG()           { return rawSensorG; }
    public int getRawSensorB()           { return rawSensorB; }
    public int getReferenceR()           { return referenceR; }
    public int getReferenceG()           { return referenceG; }
    public int getReferenceB()           { return referenceB; }
    public int getCorrectedSensorR()     { return correctedSensorR; }
    public int getCorrectedSensorG()     { return correctedSensorG; }
    public int getCorrectedSensorB()     { return correctedSensorB; }
    public double getColourDifference()  { return colourDifference; }
    public double getBrightness()        { return brightness; }
    public double getSharpness()         { return sharpness; }
    public String getImageQualityLabel() { return imageQualityLabel; }

    /**
     * Returns the corrected sensor colour as a formatted RGB string,
     * e.g. "rgb(210, 195, 180)".
     */
    public String getCorrectedRgbString() {
        return "rgb(" + correctedSensorR + ", "
                      + correctedSensorG + ", "
                      + correctedSensorB + ")";
    }

    public String getRawRgbString() {
        return "rgb(" + rawSensorR + ", "
                      + rawSensorG + ", "
                      + rawSensorB + ")";
    }

    public String getReferenceRgbString() {
        return "rgb(" + referenceR + ", "
                      + referenceG + ", "
                      + referenceB + ")";
    }

    // ------------------------------------------------------------------
    // Static factory helpers
    // ------------------------------------------------------------------

    public static ColourAnalysisResult demo() {
        return new Builder()
                .status(Status.DEMO)
                .statusMessage("Demo flow - no image analysed")
                .isRealCapture(false)
                .imageQualityLabel("N/A (Demo)")
                .build();
    }

    public static ColourAnalysisResult error(String reason) {
        return new Builder()
                .status(Status.ERROR)
                .statusMessage(reason)
                .isRealCapture(true)
                .imageQualityLabel("Unknown")
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
                .build();
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static class Builder {
        Status status = Status.ERROR;
        String statusMessage = "";
        boolean isRealCapture = false;
        int rawSensorR, rawSensorG, rawSensorB;
        int referenceR = 255, referenceG = 255, referenceB = 255;
        int correctedSensorR, correctedSensorG, correctedSensorB;
        double colourDifference;
        double brightness;
        double sharpness;
        String imageQualityLabel = "Unknown";

        public Builder status(Status s)                { this.status = s; return this; }
        public Builder statusMessage(String m)         { this.statusMessage = m; return this; }
        public Builder isRealCapture(boolean r)        { this.isRealCapture = r; return this; }
        public Builder rawSensor(int r, int g, int b)  { rawSensorR=r; rawSensorG=g; rawSensorB=b; return this; }
        public Builder reference(int r, int g, int b)  { referenceR=r; referenceG=g; referenceB=b; return this; }
        public Builder corrected(int r, int g, int b)  { correctedSensorR=r; correctedSensorG=g; correctedSensorB=b; return this; }
        public Builder colourDifference(double d)      { this.colourDifference = d; return this; }
        public Builder brightness(double v)            { this.brightness = v; return this; }
        public Builder sharpness(double v)             { this.sharpness = v; return this; }
        public Builder imageQualityLabel(String l)     { this.imageQualityLabel = l; return this; }

        public ColourAnalysisResult build() { return new ColourAnalysisResult(this); }
    }
}
