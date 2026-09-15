package com.ankita.h2sdosimeter.calibration;

import java.io.Serializable;

/**
 * CalibrationPoint - one reference measurement that links a measured
 * colour-difference value to a known H2S exposure.
 *
 * These are entered by the user after exposing a badge to a known
 * concentration for a known duration under controlled conditions, then
 * scanning it in this application.
 *
 * colourDifference: the lighting-corrected ΔE₀₀ (CIEDE2000 from white)
 *                   shown on the scan result for this badge.
 * knownPpmHr:       the known H2S exposure (ppm × hours) for this badge,
 *                   verified by laboratory or certified detector measurement.
 * label:            optional user description (e.g. "1 ppm × 4 hr lab test").
 * timestampMs:      epoch ms when this point was recorded.
 *
 * SAFETY NOTE: A calibration point is only as reliable as the reference
 * measurement used to create it. Points derived from estimated or
 * unverified exposures will produce unreliable results.
 */
public class CalibrationPoint implements Serializable {

    private final double colourDifference;
    private final double knownPpmHr;
    private final String label;
    private final long timestampMs;

    public CalibrationPoint(double colourDifference, double knownPpmHr,
                             String label, long timestampMs) {
        this.colourDifference = colourDifference;
        this.knownPpmHr       = knownPpmHr;
        this.label            = label != null ? label : "";
        this.timestampMs      = timestampMs;
    }

    public double getColourDifference() { return colourDifference; }
    public double getKnownPpmHr()       { return knownPpmHr; }
    public String getLabel()            { return label; }
    public long   getTimestampMs()      { return timestampMs; }

    @Override
    public String toString() {
        return "CalibrationPoint{diff=" + String.format("%.2f", colourDifference)
                + ", ppm.hr=" + String.format("%.3f", knownPpmHr)
                + ", label='" + label + "'}";
    }
}
