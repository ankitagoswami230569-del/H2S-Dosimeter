package com.ankita.h2sdosimeter.calibration;

import java.util.List;

/**
 * CalibrationCurve - converts a measured colourDifference value into an
 * estimated H2S exposure (ppm.hr) using stored calibration points.
 *
 * ALGORITHM CHOICE - Linear Interpolation / Extrapolation:
 * With only a small number of calibration points (typically 2-5), fitting
 * a polynomial or exponential curve carries a high risk of overfitting and
 * producing wildly incorrect extrapolations.  A piecewise linear approach
 * (connect-the-dots between sorted calibration points) is the most
 * conservative and transparent option:
 *   - With ≥ 2 points: interpolate between the two nearest, or linearly
 *     extrapolate beyond the endpoints.
 *   - With exactly 1 point: cannot interpolate - returns INSUFFICIENT_DATA.
 *   - With 0 points: returns INSUFFICIENT_DATA.
 *
 * CONFIDENCE:
 * A simple confidence indicator is returned:
 *   HIGH   - queried value is within the range of calibration points.
 *   LOW    - queried value is outside the calibration range (extrapolation).
 *   NONE   - not enough calibration points.
 *
 * SAFETY WARNING:
 * The output of this class is an ESTIMATE. Accuracy depends entirely on the
 * quality and number of reference calibration points. This estimate must NOT
 * be used for occupational-health decisions without independent validation.
 */
public class CalibrationCurve {

    public enum Confidence {
        HIGH,   // Interpolated - within calibration range
        LOW,    // Extrapolated - outside calibration range
        NONE    // Insufficient calibration data
    }

    public static class ConversionResult {
        public final boolean success;
        public final double  estimatedPpmHr;
        public final Confidence confidence;
        public final String  message;

        private ConversionResult(boolean success, double ppm,
                                  Confidence conf, String msg) {
            this.success         = success;
            this.estimatedPpmHr  = ppm;
            this.confidence      = conf;
            this.message         = msg;
        }

        public static ConversionResult insufficient(String reason) {
            return new ConversionResult(false, 0.0, Confidence.NONE, reason);
        }

        public static ConversionResult interpolated(double ppm) {
            return new ConversionResult(true, ppm, Confidence.HIGH,
                    "Interpolated from calibration curve");
        }

        public static ConversionResult extrapolated(double ppm) {
            return new ConversionResult(true, ppm, Confidence.LOW,
                    "Extrapolated beyond calibration range - lower confidence");
        }
    }

    /**
     * Converts a colourDifference value to an estimated ppm.hr.
     *
     * @param colourDifference  Value from ReferenceCorrectionService (0-441).
     * @param calibrationPoints Loaded list (must be sorted by colourDifference).
     * @return ConversionResult with ppm.hr estimate and confidence level.
     */
    public static ConversionResult convert(double colourDifference,
                                            List<CalibrationPoint> calibrationPoints) {

        if (calibrationPoints == null || calibrationPoints.size() < 2) {
            int count = calibrationPoints == null ? 0 : calibrationPoints.size();
            return ConversionResult.insufficient(
                    "Need at least 2 calibration points (have " + count + ")");
        }

        // Points are assumed sorted ascending by colourDifference (CalibrationStore sorts them)
        int n = calibrationPoints.size();
        double dMin = calibrationPoints.get(0).getColourDifference();
        double dMax = calibrationPoints.get(n - 1).getColourDifference();

        // Find the two bracketing points
        if (colourDifference <= dMin) {
            // Extrapolate below the lowest calibration point
            CalibrationPoint p0 = calibrationPoints.get(0);
            CalibrationPoint p1 = calibrationPoints.get(1);
            double ppm = linearInterpolate(
                    p0.getColourDifference(), p0.getKnownPpmHr(),
                    p1.getColourDifference(), p1.getKnownPpmHr(),
                    colourDifference);
            ppm = Math.max(0.0, ppm); // clamp to non-negative
            return ConversionResult.extrapolated(ppm);
        }

        if (colourDifference >= dMax) {
            // Extrapolate above the highest calibration point
            CalibrationPoint p0 = calibrationPoints.get(n - 2);
            CalibrationPoint p1 = calibrationPoints.get(n - 1);
            double ppm = linearInterpolate(
                    p0.getColourDifference(), p0.getKnownPpmHr(),
                    p1.getColourDifference(), p1.getKnownPpmHr(),
                    colourDifference);
            ppm = Math.max(0.0, ppm);
            return ConversionResult.extrapolated(ppm);
        }

        // Interpolate between the two surrounding points
        for (int i = 0; i < n - 1; i++) {
            CalibrationPoint lo = calibrationPoints.get(i);
            CalibrationPoint hi = calibrationPoints.get(i + 1);
            if (colourDifference >= lo.getColourDifference()
                    && colourDifference <= hi.getColourDifference()) {
                double ppm = linearInterpolate(
                        lo.getColourDifference(), lo.getKnownPpmHr(),
                        hi.getColourDifference(), hi.getKnownPpmHr(),
                        colourDifference);
                ppm = Math.max(0.0, ppm);
                return ConversionResult.interpolated(ppm);
            }
        }

        // Fallback (should not reach here)
        return ConversionResult.insufficient("Unexpected calibration state");
    }

    /**
     * Converts a scale-position value (from ScaleReader, via the ArUco
     * reference-card pipeline) to an estimated ppm.hr.
     *
     * This is the PREFERRED conversion path: scale position is derived by
     * comparing the reaction strip to printed reference swatches after
     * per-photo colour correction, so - unlike colourDifference - it does
     * not depend on the reference-white-patch heuristic and is
     * lighting-independent. Use {@link #convert(double, List)} as a
     * fallback when no scale-position calibration points are available
     * (e.g. the reference card was not detected for this scan, or the
     * device has only legacy colour-difference calibration points).
     *
     * Uses the same conservative piecewise-linear interpolation /
     * extrapolation strategy as {@link #convert(double, List)} - see that
     * method's class-level ALGORITHM CHOICE notes.
     *
     * @param scalePosition Value from ScaleReader.Reading.scalePosition.
     * @param scalePoints   Calibration points that carry a scale position
     *                      (see CalibrationStore.loadPointsByScale), sorted
     *                      ascending by scalePosition.
     * @return ConversionResult with ppm.hr estimate and confidence level.
     */
    public static ConversionResult convertByScalePosition(double scalePosition,
                                                           List<CalibrationPoint> scalePoints) {

        if (scalePoints == null || scalePoints.size() < 2) {
            int count = scalePoints == null ? 0 : scalePoints.size();
            return ConversionResult.insufficient(
                    "Need at least 2 scale-position calibration points (have " + count + ")");
        }

        int n = scalePoints.size();
        double posMin = scalePoints.get(0).getScalePosition();
        double posMax = scalePoints.get(n - 1).getScalePosition();

        if (scalePosition <= posMin) {
            CalibrationPoint p0 = scalePoints.get(0);
            CalibrationPoint p1 = scalePoints.get(1);
            double ppm = linearInterpolate(
                    p0.getScalePosition(), p0.getKnownPpmHr(),
                    p1.getScalePosition(), p1.getKnownPpmHr(),
                    scalePosition);
            return ConversionResult.extrapolated(Math.max(0.0, ppm));
        }

        if (scalePosition >= posMax) {
            CalibrationPoint p0 = scalePoints.get(n - 2);
            CalibrationPoint p1 = scalePoints.get(n - 1);
            double ppm = linearInterpolate(
                    p0.getScalePosition(), p0.getKnownPpmHr(),
                    p1.getScalePosition(), p1.getKnownPpmHr(),
                    scalePosition);
            return ConversionResult.extrapolated(Math.max(0.0, ppm));
        }

        for (int i = 0; i < n - 1; i++) {
            CalibrationPoint lo = scalePoints.get(i);
            CalibrationPoint hi = scalePoints.get(i + 1);
            if (scalePosition >= lo.getScalePosition() && scalePosition <= hi.getScalePosition()) {
                double ppm = linearInterpolate(
                        lo.getScalePosition(), lo.getKnownPpmHr(),
                        hi.getScalePosition(), hi.getKnownPpmHr(),
                        scalePosition);
                return ConversionResult.interpolated(Math.max(0.0, ppm));
            }
        }

        return ConversionResult.insufficient("Unexpected calibration state");
    }

    /**
     * Linear interpolation / extrapolation.
     *   y = y0 + (x - x0) * (y1 - y0) / (x1 - x0)
     */
    private static double linearInterpolate(double x0, double y0,
                                             double x1, double y1,
                                             double x) {
        if (Math.abs(x1 - x0) < 1e-9) return y0; // coincident points
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    /**
     * Returns a summary string describing the current calibration range.
     * Useful for displaying to the user in the calibration UI.
     */
    public static String rangeSummary(List<CalibrationPoint> points) {
        if (points == null || points.isEmpty()) return "No calibration points";
        if (points.size() == 1) {
            return "1 point: diff=" + String.format("%.1f", points.get(0).getColourDifference())
                    + " → " + String.format("%.3f", points.get(0).getKnownPpmHr()) + " ppm.hr";
        }
        CalibrationPoint lo = points.get(0);
        CalibrationPoint hi = points.get(points.size() - 1);
        return points.size() + " points: diff "
                + String.format("%.1f", lo.getColourDifference())
                + "–" + String.format("%.1f", hi.getColourDifference())
                + " → "
                + String.format("%.3f", lo.getKnownPpmHr())
                + "–" + String.format("%.3f", hi.getKnownPpmHr())
                + " ppm.hr";
    }
}
