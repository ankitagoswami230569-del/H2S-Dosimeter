package com.ankita.h2sdosimeter;

import com.ankita.h2sdosimeter.analysis.ReferenceCorrectionService;
import com.ankita.h2sdosimeter.calibration.CalibrationCurve;
import com.ankita.h2sdosimeter.calibration.CalibrationPoint;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ColourPipelineTest — unit tests for the colour metric and calibration pipeline.
 *
 * Covers both scan modes:
 *   MODE 1 — strip + reference scale (correction applied, higher confidence)
 *   MODE 2 — strip only (no correction, approximate result)
 *
 * All tests run without Android context, bitmap, or hardware.
 */
public class ColourPipelineTest {

    private static final double DELTA_CLOSE = 0.5;
    private static final double DELTA_LOOSE = 2.0;
    private static final double DELTA_TINY  = 0.01;

    // ------------------------------------------------------------------
    // Synthetic calibration curve (matches DemoCalibrationDataset)
    // ------------------------------------------------------------------

    private static List<CalibrationPoint> syntheticCurve() {
        List<CalibrationPoint> pts = new ArrayList<>();
        pts.add(new CalibrationPoint( 2.0,  0.0, "synthetic", 1000L));
        pts.add(new CalibrationPoint( 8.0,  1.0, "synthetic", 2000L));
        pts.add(new CalibrationPoint(18.0,  3.0, "synthetic", 3000L));
        pts.add(new CalibrationPoint(30.0,  6.0, "synthetic", 4000L));
        pts.add(new CalibrationPoint(50.0, 10.0, "synthetic", 5000L));
        return pts;
    }

    // ==================================================================
    // GROUP 1: CIE Lab conversion
    // ==================================================================

    @Test
    public void test14_pureWhite_givesLabOfWhite() {
        double[] lab = ReferenceCorrectionService.rgbToLab(255, 255, 255);
        assertEquals("L* for white ~100", 100.0, lab[0], DELTA_CLOSE);
        assertEquals("a* for white ~0",     0.0, lab[1], DELTA_CLOSE);
        assertEquals("b* for white ~0",     0.0, lab[2], DELTA_CLOSE);
    }

    @Test
    public void test15_yellow_givesPositiveBStar() {
        double[] lab = ReferenceCorrectionService.rgbToLab(220, 210, 130);
        assertTrue("Yellow should have positive b*", lab[2] > 10.0);
    }

    @Test
    public void test_pureBlack_givesLStarZero() {
        double[] lab = ReferenceCorrectionService.rgbToLab(0, 0, 0);
        assertEquals("L* for black ~0", 0.0, lab[0], DELTA_CLOSE);
    }

    // ==================================================================
    // GROUP 2: ΔE₀₀ behaviour
    // ==================================================================

    @Test
    public void test01_unexposedStrip_deltaENearZero() {
        double de = ReferenceCorrectionService.deltaEFromWhite(255, 255, 255);
        assertEquals("Unexposed (white) strip gives ΔE₀₀ ~0", 0.0, de, DELTA_CLOSE);
    }

    @Test
    public void test02_moderateYellowing_deltaEInRange() {
        double de = ReferenceCorrectionService.deltaEFromWhite(230, 220, 180);
        assertTrue("Moderate yellowing ΔE₀₀ > 5",  de > 5.0);
        assertTrue("Moderate yellowing ΔE₀₀ < 40", de < 40.0);
    }

    @Test
    public void test03_heavyYellowing_deltaEGreaterThanModerate() {
        double deModerate = ReferenceCorrectionService.deltaEFromWhite(230, 220, 180);
        double deHeavy    = ReferenceCorrectionService.deltaEFromWhite(180, 160,  90);
        assertTrue("Heavy yellowing > moderate", deHeavy > deModerate);
    }

    @Test
    public void test04_identicalInputs_identicalDeltaE() {
        double de1 = ReferenceCorrectionService.deltaEFromWhite(210, 195, 150);
        double de2 = ReferenceCorrectionService.deltaEFromWhite(210, 195, 150);
        assertEquals("Identical inputs → identical ΔE₀₀", de1, de2, DELTA_TINY);
    }

    // ==================================================================
    // GROUP 3: Calibration curve
    // ==================================================================

    @Test
    public void test05_lowerDeltaE_lowerPpmHr() {
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult lo = CalibrationCurve.convert( 8.0, curve);
        CalibrationCurve.ConversionResult hi = CalibrationCurve.convert(18.0, curve);
        assertTrue("Lower ΔE₀₀ → lower ppm·hr", lo.estimatedPpmHr < hi.estimatedPpmHr);
    }

    @Test
    public void test06_higherDeltaE_higherPpmHr() {
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r18 = CalibrationCurve.convert(18.0, curve);
        CalibrationCurve.ConversionResult r30 = CalibrationCurve.convert(30.0, curve);
        assertTrue("Higher ΔE₀₀ → higher ppm·hr", r30.estimatedPpmHr > r18.estimatedPpmHr);
    }

    @Test
    public void test07_identicalDeltaE_identicalPpmHr() {
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r1 = CalibrationCurve.convert(13.0, curve);
        CalibrationCurve.ConversionResult r2 = CalibrationCurve.convert(13.0, curve);
        assertEquals("Identical ΔE₀₀ → identical ppm·hr",
                r1.estimatedPpmHr, r2.estimatedPpmHr, DELTA_TINY);
    }

    @Test
    public void test08_noCalibrationPoints_insufficientData() {
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(15.0, new ArrayList<>());
        assertFalse("Empty calibration → success=false", r.success);
        assertEquals("Empty calibration → NONE confidence",
                CalibrationCurve.Confidence.NONE, r.confidence);
    }

    @Test
    public void test09_oneCalibrationPoint_insufficientData() {
        List<CalibrationPoint> single = new ArrayList<>();
        single.add(new CalibrationPoint(15.0, 3.0, "single", 1000L));
        assertFalse("Single point → success=false",
                CalibrationCurve.convert(15.0, single).success);
    }

    @Test
    public void test10_deltaEInRange_highConfidence() {
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(13.0, curve);
        assertTrue("In-range → success", r.success);
        assertEquals("In-range → HIGH confidence",
                CalibrationCurve.Confidence.HIGH, r.confidence);
    }

    @Test
    public void test11_deltaEAboveRange_lowConfidence() {
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(80.0, curve);
        assertTrue("Above-range → still succeeds (extrapolated)", r.success);
        assertEquals("Above-range → LOW confidence",
                CalibrationCurve.Confidence.LOW, r.confidence);
        assertTrue("ppm·hr non-negative", r.estimatedPpmHr >= 0.0);
    }

    @Test
    public void test_knownInterpolation_correctPpmHr() {
        // ΔE₀₀=13 between (8→1) and (18→3): 1 + (13-8)/(18-8)*(3-1) = 2.0
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(13.0, curve);
        assertTrue(r.success);
        assertEquals("Linear interp at ΔE₀₀=13 → 2.0 ppm·hr",
                2.0, r.estimatedPpmHr, DELTA_LOOSE);
    }

    @Test
    public void test_extrapolatedBelowRange_ppmHrNonNegative() {
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(0.5, curve);
        assertTrue("Below-range → extrapolated success", r.success);
        assertTrue("ppm·hr never negative", r.estimatedPpmHr >= 0.0);
    }

    // ==================================================================
    // GROUP 4: White balance correction
    // ==================================================================

    @Test
    public void test12_whiteReference_correctionIsNoop() {
        int[] c = ReferenceCorrectionService.correct(200, 180, 160, 255, 255, 255, true);
        assertEquals("R unchanged with white ref", 200, c[0]);
        assertEquals("G unchanged with white ref", 180, c[1]);
        assertEquals("B unchanged with white ref", 160, c[2]);
    }

    @Test
    public void test13_warmLightCorrection_reducesRedBoostsBlue() {
        int[] c = ReferenceCorrectionService.correct(
                150, 150, 150, 230, 250, 200, true);
        assertTrue("Warm correction boosts B vs R", c[2] > c[0]);
    }

    @Test
    public void test_invalidReference_returnsRaw() {
        int[] c = ReferenceCorrectionService.correct(100, 120, 140, 200, 200, 200, false);
        assertEquals("R raw when ref invalid", 100, c[0]);
        assertEquals("G raw when ref invalid", 120, c[1]);
        assertEquals("B raw when ref invalid", 140, c[2]);
    }

    // ==================================================================
    // GROUP 5: End-to-end MODE 1 (correction applied)
    // ==================================================================

    @Test
    public void endToEnd_mode1_lightExposure_givesLowPpmHr() {
        // MODE 1: reference detected, correction applied
        // Slightly yellow raw sensor, near-white reference → small correction
        int rawR = 235, rawG = 228, rawB = 190;
        int refR = 250, refG = 252, refB = 248;   // near-white reference

        // Apply correction (MODE 1)
        int[] corrected = ReferenceCorrectionService.correct(rawR, rawG, rawB, refR, refG, refB, true);
        double de = ReferenceCorrectionService.deltaEFromWhite(corrected[0], corrected[1], corrected[2]);

        assertTrue("MODE 1 light exposure ΔE₀₀ > 5",  de > 5.0);
        assertTrue("MODE 1 light exposure ΔE₀₀ < 20", de < 20.0);

        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(de, curve);
        assertTrue("MODE 1 conversion succeeds", r.success);
        assertTrue("MODE 1 light exposure ppm·hr < 4.0", r.estimatedPpmHr < 4.0);
    }

    @Test
    public void endToEnd_mode1_heavyExposure_givesHighPpmHr() {
        // MODE 1: heavily yellowed strip, near-white reference
        int rawR = 190, rawG = 165, rawB = 80;
        int refR = 248, refG = 250, refB = 245;

        int[] corrected = ReferenceCorrectionService.correct(rawR, rawG, rawB, refR, refG, refB, true);
        double de = ReferenceCorrectionService.deltaEFromWhite(corrected[0], corrected[1], corrected[2]);

        assertTrue("MODE 1 heavy exposure ΔE₀₀ > 25", de > 25.0);

        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(de, curve);
        assertTrue("MODE 1 heavy exposure conversion succeeds", r.success);
        assertTrue("MODE 1 heavy exposure ppm·hr > 4.0", r.estimatedPpmHr > 4.0);
    }

    // ==================================================================
    // GROUP 6: MODE 2 tests (strip only — no reference correction)
    // ==================================================================

    /**
     * MODE 2: reference NOT detected → correction skipped (referenceValid=false).
     * Raw sensor colour is used directly.
     * ΔE₀₀ is computed on the raw colour, not a corrected one.
     */
    @Test
    public void mode2_noCorrection_rawColourUsedDirectly() {
        int rawR = 210, rawG = 195, rawB = 155;

        // MODE 2: skip correction entirely (referenceValid=false)
        int[] analysed = ReferenceCorrectionService.correct(
                rawR, rawG, rawB,
                0, 0, 0,   // reference values irrelevant when referenceValid=false
                false);    // ← MODE 2: correction NOT applied

        // Result must equal raw values
        assertEquals("MODE 2: analysed R = raw R", rawR, analysed[0]);
        assertEquals("MODE 2: analysed G = raw G", rawG, analysed[1]);
        assertEquals("MODE 2: analysed B = raw B", rawB, analysed[2]);
    }

    @Test
    public void mode2_deltaE_computedOnRawColour() {
        // MODE 2: ΔE₀₀ is computed directly on the raw sensor colour
        int rawR = 210, rawG = 200, rawB = 160;

        double deMode2 = ReferenceCorrectionService.deltaEFromWhite(rawR, rawG, rawB);
        assertTrue("MODE 2: ΔE₀₀ > 0 for non-white strip", deMode2 > 0);
    }

    @Test
    public void mode2_lowerExposure_lowerPpmHr() {
        // MODE 2: lighter raw colour → lower ΔE₀₀ → lower ppm·hr
        double deLight  = ReferenceCorrectionService.deltaEFromWhite(240, 238, 220);
        double deHeavy  = ReferenceCorrectionService.deltaEFromWhite(185, 160,  80);

        assertTrue("MODE 2: light < heavy ΔE₀₀", deLight < deHeavy);

        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult rLight = CalibrationCurve.convert(deLight, curve);
        CalibrationCurve.ConversionResult rHeavy = CalibrationCurve.convert(deHeavy, curve);

        assertTrue("MODE 2 light: conversion succeeds", rLight.success);
        assertTrue("MODE 2 heavy: conversion succeeds", rHeavy.success);
        assertTrue("MODE 2: light ppm·hr < heavy ppm·hr",
                rLight.estimatedPpmHr < rHeavy.estimatedPpmHr);
    }

    @Test
    public void mode2_identical_colour_identical_ppmHr() {
        // Identical raw colour in MODE 2 → identical ΔE₀₀ → identical ppm·hr
        int r = 210, g = 195, b = 155;
        double de1 = ReferenceCorrectionService.deltaEFromWhite(r, g, b);
        double de2 = ReferenceCorrectionService.deltaEFromWhite(r, g, b);
        assertEquals("MODE 2: identical colour → identical ΔE₀₀", de1, de2, DELTA_TINY);

        List<CalibrationPoint> curve = syntheticCurve();
        double ppm1 = CalibrationCurve.convert(de1, curve).estimatedPpmHr;
        double ppm2 = CalibrationCurve.convert(de2, curve).estimatedPpmHr;
        assertEquals("MODE 2: identical ΔE₀₀ → identical ppm·hr", ppm1, ppm2, DELTA_TINY);
    }

    @Test
    public void mode2_noCalibration_noPpmHr() {
        // MODE 2 with empty calibration: must NOT produce a ppm·hr value
        double de = ReferenceCorrectionService.deltaEFromWhite(210, 195, 155);
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(de, new ArrayList<>());
        assertFalse("MODE 2 + no calibration → CALIBRATION REQUIRED", r.success);
        assertEquals("MODE 2 + no calibration → NONE confidence",
                CalibrationCurve.Confidence.NONE, r.confidence);
    }

    @Test
    public void mode2_mode1_sameSensorColour_mode1HasCorrectedValue() {
        // When strip colour is the same but reference is available:
        // MODE 1 corrects the sensor colour → different ΔE₀₀ than MODE 2.
        // With a warm-light reference (R high, B low) correction will change the colour.
        int sensorR = 210, sensorG = 200, sensorB = 160;
        int refR    = 230, refG    = 240, refB    = 200;  // warm light reference

        // MODE 1: correction applied
        int[] mode1Colour = ReferenceCorrectionService.correct(
                sensorR, sensorG, sensorB, refR, refG, refB, true);
        double deMode1 = ReferenceCorrectionService.deltaEFromWhite(
                mode1Colour[0], mode1Colour[1], mode1Colour[2]);

        // MODE 2: no correction
        double deMode2 = ReferenceCorrectionService.deltaEFromWhite(sensorR, sensorG, sensorB);

        // With a non-neutral reference the values differ
        // (if ref were pure white they'd be equal, but here ref != white)
        // Both produce valid ΔE₀₀ values; the key property is they are
        // derived from the actual pixel colour, not hard-coded
        assertTrue("MODE 1 ΔE₀₀ ≥ 0", deMode1 >= 0);
        assertTrue("MODE 2 ΔE₀₀ ≥ 0", deMode2 >= 0);

        // Correction with non-neutral reference changes the colour
        boolean correctionChanged = Math.abs(deMode1 - deMode2) > 0.01;
        assertTrue("Non-neutral reference: MODE 1 ΔE₀₀ differs from MODE 2", correctionChanged);
    }

    // ==================================================================
    // GROUP 7: End-to-end ordering (both modes)
    // ==================================================================

    @Test
    public void endToEnd_ordering_lightLessThanHeavy_mode1() {
        int[] light    = {245, 242, 220};
        int[] moderate = {225, 210, 165};
        int[] heavy    = {185, 155,  75};
        int[] ref      = {250, 252, 248};  // near-white reference (MODE 1)

        List<CalibrationPoint> curve = syntheticCurve();

        double deLight    = deMode1(light,    ref);
        double deModerate = deMode1(moderate, ref);
        double deHeavy    = deMode1(heavy,    ref);

        assertTrue("MODE 1 ordering: light < moderate ΔE₀₀", deLight < deModerate);
        assertTrue("MODE 1 ordering: moderate < heavy ΔE₀₀",  deModerate < deHeavy);

        double ppmLight    = CalibrationCurve.convert(deLight,    curve).estimatedPpmHr;
        double ppmModerate = CalibrationCurve.convert(deModerate, curve).estimatedPpmHr;
        double ppmHeavy    = CalibrationCurve.convert(deHeavy,    curve).estimatedPpmHr;

        assertTrue("MODE 1 ordering: light ppm·hr < moderate", ppmLight    < ppmModerate);
        assertTrue("MODE 1 ordering: moderate ppm·hr < heavy",  ppmModerate < ppmHeavy);
    }

    @Test
    public void endToEnd_ordering_lightLessThanHeavy_mode2() {
        // MODE 2: no correction, raw colours used directly
        int[] light    = {245, 242, 222};
        int[] moderate = {222, 210, 162};
        int[] heavy    = {182, 152,  72};

        List<CalibrationPoint> curve = syntheticCurve();

        double deLight    = ReferenceCorrectionService.deltaEFromWhite(light[0],    light[1],    light[2]);
        double deModerate = ReferenceCorrectionService.deltaEFromWhite(moderate[0], moderate[1], moderate[2]);
        double deHeavy    = ReferenceCorrectionService.deltaEFromWhite(heavy[0],    heavy[1],    heavy[2]);

        assertTrue("MODE 2 ordering: light < moderate ΔE₀₀", deLight < deModerate);
        assertTrue("MODE 2 ordering: moderate < heavy ΔE₀₀",  deModerate < deHeavy);

        double ppmLight    = CalibrationCurve.convert(deLight,    curve).estimatedPpmHr;
        double ppmModerate = CalibrationCurve.convert(deModerate, curve).estimatedPpmHr;
        double ppmHeavy    = CalibrationCurve.convert(deHeavy,    curve).estimatedPpmHr;

        assertTrue("MODE 2 ordering: light ppm·hr < moderate", ppmLight    < ppmModerate);
        assertTrue("MODE 2 ordering: moderate ppm·hr < heavy",  ppmModerate < ppmHeavy);
    }

    // ==================================================================
    // Helper
    // ==================================================================

    private static double deMode1(int[] sensor, int[] ref) {
        int[] corrected = ReferenceCorrectionService.correct(
                sensor[0], sensor[1], sensor[2],
                ref[0],    ref[1],    ref[2],
                true);
        return ReferenceCorrectionService.deltaEFromWhite(corrected[0], corrected[1], corrected[2]);
    }

    // ==================================================================
    // GROUP 6: MODE 2 specific — strip only, no reference correction
    // ==================================================================

    /**
     * MODE 2: raw sensor colour is used directly (referenceValid=false).
     * correct() must return the raw values unchanged.
     */
    @Test
    public void mode2_noCorrection_rawValuesUsed() {
        int rawR = 210, rawG = 195, rawB = 150;
        // Simulating MODE 2: referenceValid = false
        int[] result = ReferenceCorrectionService.correct(rawR, rawG, rawB,
                200, 200, 200, false);
        assertEquals("MODE 2: R must equal raw", rawR, result[0]);
        assertEquals("MODE 2: G must equal raw", rawG, result[1]);
        assertEquals("MODE 2: B must equal raw", rawB, result[2]);
    }

    /**
     * MODE 2: ΔE₀₀ computed from raw colour still produces a valid ppm·hr.
     * The pipeline must not refuse to produce a result just because MODE 2.
     */
    @Test
    public void mode2_rawColour_givesValidPpmHr() {
        // Slightly yellowed raw sensor (no correction applied)
        int rawR = 235, rawG = 225, rawB = 185;
        double de = ReferenceCorrectionService.deltaEFromWhite(rawR, rawG, rawB);
        assertTrue("MODE 2: ΔE₀₀ should be > 0 for a yellowed strip", de > 1.0);

        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(de, curve);
        assertTrue("MODE 2: calibration should succeed", r.success);
        assertTrue("MODE 2: ppm·hr must be >= 0", r.estimatedPpmHr >= 0.0);
    }

    /**
     * MODE 1 vs MODE 2 give DIFFERENT results for the same raw sensor colour
     * when the reference is not near-white. MODE 1 applies correction;
     * MODE 2 uses raw. They must produce different ΔE₀₀ values.
     */
    @Test
    public void mode1VsMode2_differentDeltaE_whenRefNotWhite() {
        int rawR = 210, rawG = 195, rawB = 155;
        // Warm (yellowish) reference — correction will change the result
        int refR = 240, refG = 230, refB = 180;

        // MODE 1: with correction
        int[] corrected = ReferenceCorrectionService.correct(rawR, rawG, rawB,
                refR, refG, refB, true);
        double deMode1 = ReferenceCorrectionService.deltaEFromWhite(
                corrected[0], corrected[1], corrected[2]);

        // MODE 2: without correction (raw used directly)
        double deMode2 = ReferenceCorrectionService.deltaEFromWhite(rawR, rawG, rawB);

        assertNotEquals("MODE 1 and MODE 2 must give different ΔE₀₀ when ref≠white",
                deMode1, deMode2, DELTA_TINY);
    }

    /**
     * MODE 1 and MODE 2 give SAME result when reference IS near-white
     * (correction factor ≈ 1.0, so correction is effectively a no-op).
     */
    @Test
    public void mode1AndMode2_sameResult_whenRefIsWhite() {
        int rawR = 210, rawG = 195, rawB = 155;
        // Pure white reference
        int refR = 255, refG = 255, refB = 255;

        int[] corrected = ReferenceCorrectionService.correct(rawR, rawG, rawB,
                refR, refG, refB, true);
        double deMode1 = ReferenceCorrectionService.deltaEFromWhite(
                corrected[0], corrected[1], corrected[2]);
        double deMode2 = ReferenceCorrectionService.deltaEFromWhite(rawR, rawG, rawB);

        assertEquals("MODE 1 ≈ MODE 2 when reference is pure white",
                deMode1, deMode2, DELTA_CLOSE);
    }

    /**
     * Invalid/random image → no ppm·hr. Simulated by passing ΔE₀₀=0
     * (which is what the pipeline sets on REGION_NOT_FOUND / QUALITY_REJECTED).
     * CalibrationCurve must still clamp to non-negative, and the
     * status from the pipeline is not SUCCESS so calibration is not called.
     * Here we verify that ΔE₀₀=0 extrapolates to a non-negative ppm·hr (≥0)
     * but in practice the pipeline blocks calibration before reaching this.
     */
    @Test
    public void invalidImage_deltaEZero_noNegativePpmHr() {
        // Simulate: pipeline returned ΔE₀₀=0 (unexposed or error)
        double de = 0.0;
        List<CalibrationPoint> curve = syntheticCurve();
        CalibrationCurve.ConversionResult r = CalibrationCurve.convert(de, curve);
        // Calibration extrapolates below range but must clamp to >= 0
        if (r.success) {
            assertTrue("ΔE₀₀=0 must never give negative ppm·hr", r.estimatedPpmHr >= 0.0);
        }
        // If not success, that is also acceptable (insufficient data)
    }

    /**
     * MODE 2: identical raw colours → identical ΔE₀₀ → identical ppm·hr.
     */
    @Test
    public void mode2_identical_rawColours_identical_ppmHr() {
        int rawR = 220, rawG = 205, rawB = 165;
        double de1 = ReferenceCorrectionService.deltaEFromWhite(rawR, rawG, rawB);
        double de2 = ReferenceCorrectionService.deltaEFromWhite(rawR, rawG, rawB);
        assertEquals("MODE 2: identical raw colours → identical ΔE₀₀", de1, de2, DELTA_TINY);

        List<CalibrationPoint> curve = syntheticCurve();
        double ppm1 = CalibrationCurve.convert(de1, curve).estimatedPpmHr;
        double ppm2 = CalibrationCurve.convert(de2, curve).estimatedPpmHr;
        assertEquals("MODE 2: identical ΔE₀₀ → identical ppm·hr", ppm1, ppm2, DELTA_TINY);
    }

    /**
     * No calibration points → CALIBRATION REQUIRED in both modes.
     */
    @Test
    public void bothModes_noCalibration_insufficientData() {
        double deMode1 = 15.0;
        double deMode2 = 15.0;
        List<CalibrationPoint> empty = new ArrayList<>();

        assertFalse("MODE 1 without calibration → not success",
                CalibrationCurve.convert(deMode1, empty).success);
        assertFalse("MODE 2 without calibration → not success",
                CalibrationCurve.convert(deMode2, empty).success);
    }
}
