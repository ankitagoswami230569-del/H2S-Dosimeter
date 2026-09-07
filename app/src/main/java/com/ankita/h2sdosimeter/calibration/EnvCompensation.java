package com.ankita.h2sdosimeter.calibration;

/**
 * EnvCompensation - temperature / humidity compensation for the H2S
 * colorimetric reaction rate.
 *
 * WHY THIS EXISTS:
 * The lead-acetate (or equivalent) reaction that darkens the sensor strip
 * proceeds faster in heat and humidity. A raw reading taken at a
 * temperature/humidity far from the printed scale's reference condition
 * therefore reads "high" even at the same true H2S dose. This class
 * normalises a raw reading back to what it would have read AT the
 * reference condition, using a simple linear (first-order) model:
 *
 *   factor            = 1 + K_TEMP * (T - REF_TEMP_C) + K_RH * (RH - REF_RH_PCT)
 *   normalisedReading = rawReading / factor
 *
 * At the reference condition (25 C, 50% RH) factor == 1, so a scan with no
 * environmental sensor data - i.e. T = REF_TEMP_C and RH = REF_RH_PCT
 * passed in, or the single-argument {@link #normalise(double)} overload -
 * is a no-op. Missing environmental data is therefore always SAFE: it
 * degrades gracefully to "uncompensated", not to an error or a corrupted
 * value.
 *
 * PLACEHOLDER COEFFICIENTS:
 * K_TEMP and K_RH are documented placeholders, not derived from controlled
 * chamber testing of the actual badge chemistry. Replace them once real
 * accelerated-ageing / environmental-chamber data is available for the
 * specific badge model in use.
 *
 * No Android or OpenCV imports - safe for plain JVM unit tests.
 */
public final class EnvCompensation {

    private EnvCompensation() { /* static helpers only */ }

    /** Reference temperature (deg C) the printed scale was calibrated at. */
    public static final double REF_TEMP_C = 25.0;

    /** Reference relative humidity (%) the printed scale was calibrated at. */
    public static final double REF_RH_PCT = 50.0;

    /**
     * PLACEHOLDER: fractional change in reaction rate per degree C above
     * REF_TEMP_C. Not derived from chamber testing - replace once real
     * environmental-chamber data exists for the deployed badge chemistry.
     */
    public static final double K_TEMP = 0.030;

    /**
     * PLACEHOLDER: fractional change in reaction rate per percentage point
     * of relative humidity above REF_RH_PCT. Not derived from chamber testing.
     */
    public static final double K_RH = 0.005;

    /**
     * Floor below which the compensation factor is clamped, to avoid a
     * divide-by-zero or a sign flip at extreme (physically unrealistic)
     * temperature/humidity inputs.
     */
    private static final double MIN_FACTOR = 0.10;

    /**
     * Computes the reaction-rate compensation factor for the given
     * environmental condition. Always &gt;= {@link #MIN_FACTOR}.
     */
    public static double computeFactor(double temperatureC, double relativeHumidityPct) {
        double factor = 1.0
                + K_TEMP * (temperatureC - REF_TEMP_C)
                + K_RH   * (relativeHumidityPct - REF_RH_PCT);
        return Math.max(MIN_FACTOR, factor);
    }

    /**
     * Normalises a raw reading (colour difference, delta-E, or scale
     * position - any monotonically increasing "reaction extent" quantity)
     * back to the reference condition.
     *
     * @param rawReading          measured value at the actual scan condition
     * @param temperatureC        measured/estimated temperature at scan time
     * @param relativeHumidityPct measured/estimated relative humidity at scan time
     * @return the reading normalised as if measured at REF_TEMP_C / REF_RH_PCT
     */
    public static double normalise(double rawReading, double temperatureC,
                                    double relativeHumidityPct) {
        double factor = computeFactor(temperatureC, relativeHumidityPct);
        return rawReading / factor;
    }

    /**
     * Convenience overload for when no environmental sensor data is
     * available. Uses the reference condition, so the reading passes
     * through unchanged (factor == 1) - a safe default.
     */
    public static double normalise(double rawReading) {
        return normalise(rawReading, REF_TEMP_C, REF_RH_PCT);
    }
}
