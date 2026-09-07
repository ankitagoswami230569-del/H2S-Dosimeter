package com.ankita.h2sdosimeter.calibration;

import java.io.Serializable;

/**
 * CalibrationPoint - one reference measurement that links a measured
 * colour-difference value (and, optionally, a scale-position value) to a
 * known H2S exposure.
 *
 * These are entered by the user after exposing a badge to a known
 * concentration for a known duration under controlled conditions, then
 * scanning it in this application.
 *
 * colourDifference: the Euclidean RGB distance from white (0-441)
 *                   produced by the colour analysis pipeline for this badge.
 * scalePosition:    the lighting-independent scale-position value (dose-
 *                   index units) produced by ScaleReader when the printed
 *                   reference card was detected for this scan. May be
 *                   absent (NO_SCALE_POSITION) for points recorded before
 *                   the ArUco card pipeline existed, or from a scan where
 *                   the card was not detected.
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

    /** Sentinel meaning "no scale-position was recorded for this point". */
    public static final double NO_SCALE_POSITION = Double.NaN;

    private final double colourDifference;
    private final double scalePosition;
    private final double knownPpmHr;
    private final String label;
    private final long timestampMs;

    /**
     * Legacy constructor - colour-difference-only calibration point
     * (no scale position recorded).
     */
    public CalibrationPoint(double colourDifference, double knownPpmHr,
                             String label, long timestampMs) {
        this(colourDifference, NO_SCALE_POSITION, knownPpmHr, label, timestampMs);
    }

    public CalibrationPoint(double colourDifference, double scalePosition, double knownPpmHr,
                             String label, long timestampMs) {
        this.colourDifference = colourDifference;
        this.scalePosition    = scalePosition;
        this.knownPpmHr       = knownPpmHr;
        this.label            = label != null ? label : "";
        this.timestampMs      = timestampMs;
    }

    public double getColourDifference() { return colourDifference; }
    public double getScalePosition()    { return scalePosition; }
    public boolean hasScalePosition()   { return !Double.isNaN(scalePosition); }
    public double getKnownPpmHr()       { return knownPpmHr; }
    public String getLabel()            { return label; }
    public long   getTimestampMs()      { return timestampMs; }

    @Override
    public String toString() {
        return "CalibrationPoint{diff=" + String.format("%.2f", colourDifference)
                + (hasScalePosition() ? ", scalePos=" + String.format("%.2f", scalePosition) : "")
                + ", ppm.hr=" + String.format("%.3f", knownPpmHr)
                + ", label='" + label + "'}";
    }
}
