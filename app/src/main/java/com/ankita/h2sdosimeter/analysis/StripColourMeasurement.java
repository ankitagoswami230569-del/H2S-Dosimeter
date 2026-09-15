package com.ankita.h2sdosimeter.analysis;

/**
 * StripColourMeasurement — turns a detected strip + reference card into the
 * lighting-independent value that is calibrated to ppm·hr.
 *
 *   captured strip RGB ──two-point correction (white + dark patch)──► corrected RGB
 *   corrected RGB ──► CIE L*a*b* ──CIEDE2000 vs reference white──► ΔE₀₀
 *
 * ΔE₀₀ is the ONLY value used for calibration (app, calibration screen, backend).
 */
public final class StripColourMeasurement {

    /** An unexposed strip measures ≈5 ΔE₀₀ against reference white. */
    public static final double MINIMAL_CHANGE_MAX_DE  = 8.0;
    public static final double MODERATE_CHANGE_MAX_DE = 20.0;

    /** Corrected strip colour {R,G,B}, 0-255. */
    public final double[] correctedRgb;
    /** Corrected light reference patch {R,G,B} — a consistency check. */
    public final double[] correctedLightRgb;
    /** CIE L*a*b* of the corrected strip. */
    public final double[] lab;
    /** CIEDE2000 from reference white: the calibration input. */
    public final double deltaE00;
    /** Lightness loss (100 − L*). H2S strips darken as they react. */
    public final double deltaL;
    /** Euclidean RGB distance from white (0-441) — display only. */
    public final double rgbDistance;

    private StripColourMeasurement(double[] correctedRgb, double[] correctedLightRgb) {
        this.correctedRgb      = correctedRgb;
        this.correctedLightRgb = correctedLightRgb;
        this.lab               = ReferenceCorrectionService.rgbToLab(
                correctedRgb[0], correctedRgb[1], correctedRgb[2]);
        this.deltaE00          = ReferenceCorrectionService.deltaEFromWhite(correctedRgb);
        this.deltaL            = 100.0 - lab[0];
        this.rgbDistance       = ReferenceCorrectionService.colourDifference(
                (int) Math.round(correctedRgb[0]),
                (int) Math.round(correctedRgb[1]),
                (int) Math.round(correctedRgb[2]));
    }

    public static StripColourMeasurement measure(MarkedBadgeDetector.Result detection) {
        if (detection == null || !detection.detected) {
            throw new IllegalArgumentException("Detection did not succeed");
        }
        double[] strip = ReferenceCorrectionService.correctTwoPoint(
                detection.stripRgb, detection.whiteRgb, detection.darkRgb);
        double[] light = ReferenceCorrectionService.correctTwoPoint(
                detection.lightRgb, detection.whiteRgb, detection.darkRgb);
        return new StripColourMeasurement(strip, light);
    }
}
