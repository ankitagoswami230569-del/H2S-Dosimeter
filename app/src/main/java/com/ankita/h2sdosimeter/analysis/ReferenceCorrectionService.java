package com.ankita.h2sdosimeter.analysis;

/**
 * ReferenceCorrectionService - Phase 4 white-balance / illumination correction.
 *
 * WHAT THIS DOES:
 * Applies a per-channel multiplicative correction to the sensor region's
 * raw RGB using the reference region as a white anchor.
 *
 * Assumption: the reference region is a printed neutral-white patch on the
 * wristband badge (brightness close to 255, 255, 255 under ideal lighting).
 * Any deviation of the captured reference from pure white is attributed to
 * the ambient lighting colour. We scale the sensor channels by the inverse
 * of that deviation.
 *
 * Correction formula per channel C:
 *   corrected_C = clamp(raw_C * (255 / reference_C), 0, 255)
 *
 * LIMITATION:
 * This is a simple grey-world-style correction, not a full colour profile
 * transformation. It only compensates for multiplicative colour cast, not
 * additive offsets or vignetting. It requires the reference region to
 * actually capture a neutral patch - if the user positions the badge
 * incorrectly, correction will make results worse, not better.
 *
 * No H2S calibration is performed in this class.
 */
public class ReferenceCorrectionService {

    /**
     * Applies reference-white correction to a raw sensor RGB.
     *
     * @param rawR          Raw sensor red channel (0-255)
     * @param rawG          Raw sensor green channel (0-255)
     * @param rawB          Raw sensor blue channel (0-255)
     * @param referenceR    Captured reference red channel (0-255)
     * @param referenceG    Captured reference green channel (0-255)
     * @param referenceB    Captured reference blue channel (0-255)
     * @param referenceValid Whether the reference region was detected as valid.
     *                      If false, raw values are returned unchanged.
     * @return int[3] = {correctedR, correctedG, correctedB}
     */
    public static int[] correct(int rawR, int rawG, int rawB,
                                 int referenceR, int referenceG, int referenceB,
                                 boolean referenceValid) {
        if (!referenceValid) {
            // No valid reference - return raw values unchanged
            return new int[]{rawR, rawG, rawB};
        }

        // Avoid division by zero; treat 0 reference channel as 1
        int refR = Math.max(1, referenceR);
        int refG = Math.max(1, referenceG);
        int refB = Math.max(1, referenceB);

        int corrR = clamp(Math.round(rawR * 255f / refR), 0, 255);
        int corrG = clamp(Math.round(rawG * 255f / refG), 0, 255);
        int corrB = clamp(Math.round(rawB * 255f / refB), 0, 255);

        return new int[]{corrR, corrG, corrB};
    }

    /**
     * Computes Euclidean distance in RGB space between the corrected sensor
     * colour and pure white (255, 255, 255).
     *
     * A higher value means the badge has shifted further from white,
     * which on an H2S colorimetric badge correlates with higher exposure.
     * Max possible distance: sqrt(255^2 * 3) ~= 441.
     *
     * IMPORTANT: This is a colour-change indicator, NOT a calibrated
     * ppm.hr value. Converting this distance to an H2S concentration
     * requires a validated calibration curve specific to the badge model.
     */
    public static double colourDifference(int corrR, int corrG, int corrB) {
        double dR = 255 - corrR;
        double dG = 255 - corrG;
        double dB = 255 - corrB;
        return Math.sqrt(dR * dR + dG * dG + dB * dB);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
