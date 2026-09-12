package com.ankita.h2sdosimeter.model;

import java.io.Serializable;

/**
 * ColourAnalysisResult - carries every output produced by the local
 * on-device colorimetric image analysis pipeline.
 */
public class ColourAnalysisResult implements Serializable {

    public enum Status {
        SUCCESS,
        QUALITY_REJECTED,
        REGION_NOT_FOUND,
        DEMO,
        ERROR
    }

    private final Status status;
    private final String statusMessage;
    private final boolean isRealCapture;

    private final int rawSensorR;
    private final int rawSensorG;
    private final int rawSensorB;

    private final int referenceR;
    private final int referenceG;
    private final int referenceB;

    private final int correctedSensorR;
    private final int correctedSensorG;
    private final int correctedSensorB;

    /** Legacy Euclidean RGB distance from white (0–441). */
    private final double colourDifference;

    /**
     * ΔE₀₀ (CIEDE2000) between corrected sensor colour and pure white.
     * Primary calibration input. Higher = more H2S exposure.
     */
    private final double deltaE;

    /** CIE L*a*b* of corrected sensor region. */
    private final double sensorL;
    private final double sensorA;
    private final double sensorBLab;

    /** True when reference scale was detected and correction applied (MODE 1). */
    private final boolean referenceScaleDetected;

    private final double brightness;
    private final double sharpness;
    private final String imageQualityLabel;

    private ColourAnalysisResult(Builder b) {
        this.status               = b.status;
        this.statusMessage        = b.statusMessage;
        this.isRealCapture        = b.isRealCapture;
        this.rawSensorR           = b.rawSensorR;
        this.rawSensorG           = b.rawSensorG;
        this.rawSensorB           = b.rawSensorB;
        this.referenceR           = b.referenceR;
        this.referenceG           = b.referenceG;
        this.referenceB           = b.referenceB;
        this.correctedSensorR     = b.correctedSensorR;
        this.correctedSensorG     = b.correctedSensorG;
        this.correctedSensorB     = b.correctedSensorB;
        this.colourDifference     = b.colourDifference;
        this.deltaE               = b.deltaE;
        this.sensorL              = b.sensorL;
        this.sensorA              = b.sensorA;
        this.sensorBLab           = b.sensorBLab;
        this.referenceScaleDetected = b.referenceScaleDetected;
        this.brightness           = b.brightness;
        this.sharpness            = b.sharpness;
        this.imageQualityLabel    = b.imageQualityLabel;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public Status  getStatus()             { return status; }
    public String  getStatusMessage()      { return statusMessage; }
    public boolean isRealCapture()         { return isRealCapture; }
    public int     getRawSensorR()         { return rawSensorR; }
    public int     getRawSensorG()         { return rawSensorG; }
    public int     getRawSensorB()         { return rawSensorB; }
    public int     getReferenceR()         { return referenceR; }
    public int     getReferenceG()         { return referenceG; }
    public int     getReferenceB()         { return referenceB; }
    public int     getCorrectedSensorR()   { return correctedSensorR; }
    public int     getCorrectedSensorG()   { return correctedSensorG; }
    public int     getCorrectedSensorB()   { return correctedSensorB; }
    public double  getColourDifference()   { return colourDifference; }
    public double  getDeltaE()             { return deltaE; }
    public double  getSensorL()            { return sensorL; }
    public double  getSensorA()            { return sensorA; }
    public double  getSensorBLab()         { return sensorBLab; }
    public boolean isReferenceScaleDetected() { return referenceScaleDetected; }
    public double  getBrightness()         { return brightness; }
    public double  getSharpness()          { return sharpness; }
    public String  getImageQualityLabel()  { return imageQualityLabel; }

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

    public static ColourAnalysisResult dosimeterNotDetected(String reason) {
        return new Builder()
                .status(Status.REGION_NOT_FOUND)
                .statusMessage(reason)
                .isRealCapture(true)
                .imageQualityLabel("Unknown")
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
        double deltaE;
        double sensorL = 100.0, sensorA = 0.0, sensorBLab = 0.0;
        boolean referenceScaleDetected = false;
        double brightness;
        double sharpness;
        String imageQualityLabel = "Unknown";

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

        public ColourAnalysisResult build() { return new ColourAnalysisResult(this); }
    }
}
