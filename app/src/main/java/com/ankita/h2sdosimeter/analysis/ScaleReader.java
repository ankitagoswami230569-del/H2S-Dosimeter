package com.ankita.h2sdosimeter.analysis;

import java.util.List;

/**
 * ScaleReader - pure-Java scale-position determination.
 *
 * Converts the (already colour-corrected) reaction-strip colour and the
 * (already colour-corrected) reference-scale swatch colours into CIELab,
 * then finds where along the printed dose scale the strip colour falls by
 * comparing CIE76 delta-E to each swatch.
 *
 * OUTPUT: a continuous "scale position" in dose-index units (e.g. 2.35
 * meaning "between swatch 2 and swatch 3, closer to swatch 2"). This is
 * scale POSITION, NOT a ppm.hr value - {@code CalibrationCurve
 * .convertByScalePosition(...)} maps this position to an estimated dose
 * using user-entered calibration points.
 *
 * Because both the strip and the swatches were corrected by the SAME
 * per-photo {@link ColorCalibrator} transform, this comparison is
 * lighting-independent: whatever colour cast the ambient light imposed on
 * the strip, it imposed (approximately) the same cast on the swatches, and
 * the correction cancels it out for both.
 *
 * No Android or OpenCV imports - safe for plain JVM unit tests.
 */
public final class ScaleReader {

    private ScaleReader() { /* static entry point only */ }

    /** One corrected swatch sample paired with its known dose-scale index. */
    public static final class SwatchSample {
        public final int doseIndex;
        public final int[] correctedRgb; // {r, g, b}, after ColorCalibrator.apply()

        public SwatchSample(int doseIndex, int[] correctedRgb) {
            this.doseIndex = doseIndex;
            this.correctedRgb = correctedRgb;
        }
    }

    /** Result of matching a strip colour against the reference scale. */
    public static final class Reading {
        /** Interpolated position along the dose scale (dose-index units). */
        public final double scalePosition;
        /** CIE76 delta-E from the strip colour to the single nearest swatch. */
        public final double nearestDeltaE;
        /** Dose index of the single nearest swatch. */
        public final int nearestIndex;
        /** The corrected strip colour converted to CIELab (for diagnostics). */
        public final ColorMath.Lab stripLab;

        public Reading(double scalePosition, double nearestDeltaE,
                        int nearestIndex, ColorMath.Lab stripLab) {
            this.scalePosition = scalePosition;
            this.nearestDeltaE = nearestDeltaE;
            this.nearestIndex = nearestIndex;
            this.stripLab = stripLab;
        }
    }

    /**
     * Reads the strip's position on the reference scale.
     *
     * @param correctedStripRgb corrected strip colour {r, g, b}
     * @param swatches          corrected swatch samples (at least 2 needed
     *                          to interpolate a continuous position)
     * @return a Reading, or {@code null} if the inputs are malformed or
     *         fewer than 2 swatches are supplied.
     */
    public static Reading read(int[] correctedStripRgb, List<SwatchSample> swatches) {
        if (correctedStripRgb == null || correctedStripRgb.length < 3
                || swatches == null || swatches.size() < 2) {
            return null;
        }

        ColorMath.Lab stripLab = ColorMath.rgbToLab(
                correctedStripRgb[0], correctedStripRgb[1], correctedStripRgb[2]);

        int n = swatches.size();
        double[] deltaEs = new double[n];

        int nearest = 0;
        double nearestDeltaE = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            SwatchSample s = swatches.get(i);
            if (s.correctedRgb == null || s.correctedRgb.length < 3) return null;
            ColorMath.Lab swatchLab = ColorMath.rgbToLab(
                    s.correctedRgb[0], s.correctedRgb[1], s.correctedRgb[2]);
            deltaEs[i] = ColorMath.deltaE76(stripLab, swatchLab);
            if (deltaEs[i] < nearestDeltaE) {
                nearestDeltaE = deltaEs[i];
                nearest = i;
            }
        }

        int nearestDoseIndex = swatches.get(nearest).doseIndex;

        // Pick whichever immediate neighbour (in list order) is the better
        // (smaller delta-E) partner to interpolate a continuous position
        // with. List order is assumed to follow the printed scale order.
        Integer partner = null;
        if (nearest > 0) partner = nearest - 1;
        if (nearest < n - 1
                && (partner == null || deltaEs[nearest + 1] < deltaEs[partner])) {
            partner = nearest + 1;
        }

        double scalePosition;
        if (partner == null) {
            scalePosition = nearestDoseIndex;
        } else {
            double dNear = deltaEs[nearest];
            double dPartner = deltaEs[partner];
            int idxNear = nearestDoseIndex;
            int idxPartner = swatches.get(partner).doseIndex;
            double sum = dNear + dPartner;
            // As dNear -> 0, t -> 0, position -> idxNear (exact match).
            // As dNear == dPartner, t -> 0.5, position -> midpoint.
            double t = (sum < 1e-9) ? 0.0 : dNear / sum;
            scalePosition = idxNear + t * (idxPartner - idxNear);
        }

        return new Reading(scalePosition, nearestDeltaE, nearestDoseIndex, stripLab);
    }
}
