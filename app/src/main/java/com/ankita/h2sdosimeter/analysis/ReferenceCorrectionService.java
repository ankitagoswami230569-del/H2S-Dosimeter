package com.ankita.h2sdosimeter.analysis;

/**
 * ReferenceCorrectionService - illumination correction and colour metrics.
 *
 * LIGHTING CORRECTION (two-point, per channel):
 *   The reference card next to the strip has a WHITE patch and a DARK patch.
 *   Both are photographed under exactly the same light as the strip, so any
 *   colour cast, brightness change or flare/haze affects all three equally.
 *   Per channel C:
 *
 *     corrected_C = DARK_TARGET + (raw_C - dark_C) * (WHITE_TARGET - DARK_TARGET)
 *                                                  / (white_C - dark_C)
 *
 *   A gain (colour cast / brightness) AND an offset (flare, haze) are removed.
 *   The legacy white-only correction {@link #correct} is kept for compatibility.
 *
 * COLOUR METRIC:
 *   The corrected strip colour is converted to CIE L*a*b* (sRGB, D65) and
 *   compared with reference white (L*=100, a*=0, b*=0) using CIEDE2000 (ΔE₀₀).
 *   ΔE₀₀ is the value the calibration curve converts to ppm·hr.
 */
public class ReferenceCorrectionService {

    /** Corrected value the WHITE reference patch is mapped to. */
    public static final double WHITE_TARGET = 255.0;

    /**
     * Corrected value the DARK reference patch is mapped to.
     * Measured once from a well-lit photo of the printed card (the dark patch
     * reads ~120 after white-balancing). Re-measure if the card is reprinted.
     */
    public static final double DARK_TARGET = 120.0;

    /** White/dark difference below this (per channel) is too small to correct safely. */
    private static final double MIN_REFERENCE_SPAN = 20.0;

    private static final double[] LAB_WHITE = {100.0, 0.0, 0.0};

    /**
     * Two-point (white + dark) illumination correction.
     *
     * @param raw   strip colour {R, G, B} as captured
     * @param white white reference patch {R, G, B} as captured
     * @param dark  dark reference patch {R, G, B} as captured
     * @return corrected {R, G, B}, clamped to 0..255
     */
    public static double[] correctTwoPoint(double[] raw, double[] white, double[] dark) {
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) {
            double span = white[c] - dark[c];
            double v;
            if (span < MIN_REFERENCE_SPAN) {
                // Degenerate reference: fall back to white-only gain.
                v = raw[c] * WHITE_TARGET / Math.max(1.0, white[c]);
            } else {
                v = DARK_TARGET + (raw[c] - dark[c]) * (WHITE_TARGET - DARK_TARGET) / span;
            }
            out[c] = Math.max(0.0, Math.min(255.0, v));
        }
        return out;
    }

    /**
     * Legacy white-only correction.
     * corrected_C = clamp(raw_C * 255 / reference_C, 0, 255)
     */
    public static int[] correct(int rawR, int rawG, int rawB,
                                 int referenceR, int referenceG, int referenceB,
                                 boolean referenceValid) {
        if (!referenceValid) {
            return new int[]{rawR, rawG, rawB};
        }
        int refR = Math.max(1, referenceR);
        int refG = Math.max(1, referenceG);
        int refB = Math.max(1, referenceB);

        int corrR = clamp(Math.round(rawR * 255f / refR), 0, 255);
        int corrG = clamp(Math.round(rawG * 255f / refG), 0, 255);
        int corrB = clamp(Math.round(rawB * 255f / refB), 0, 255);
        return new int[]{corrR, corrG, corrB};
    }

    /**
     * Euclidean RGB distance from pure white (0-441). Kept for display and
     * backward compatibility only; calibration uses {@link #deltaEFromWhite}.
     */
    public static double colourDifference(int corrR, int corrG, int corrB) {
        double dR = 255 - corrR;
        double dG = 255 - corrG;
        double dB = 255 - corrB;
        return Math.sqrt(dR * dR + dG * dG + dB * dB);
    }

    // ------------------------------------------------------------------
    // CIE Lab / CIEDE2000
    // ------------------------------------------------------------------

    public static double[] rgbToLab(int r, int g, int b) {
        return rgbToLab((double) r, (double) g, (double) b);
    }

    /** sRGB (0-255, D65) to CIE L*a*b*. */
    public static double[] rgbToLab(double r, double g, double b) {
        double rl = srgbToLinear(r / 255.0);
        double gl = srgbToLinear(g / 255.0);
        double bl = srgbToLinear(b / 255.0);

        double x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / 0.95047;
        double y = (0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl);
        double z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / 1.08883;

        double fx = labF(x), fy = labF(y), fz = labF(z);
        return new double[]{116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }

    /** CIEDE2000 colour difference from reference white. */
    public static double deltaEFromWhite(int r, int g, int b) {
        return deltaE2000(rgbToLab(r, g, b), LAB_WHITE);
    }

    /** CIEDE2000 colour difference from reference white. */
    public static double deltaEFromWhite(double[] rgb) {
        return deltaE2000(rgbToLab(rgb[0], rgb[1], rgb[2]), LAB_WHITE);
    }

    /** CIEDE2000 (kL = kC = kH = 1). */
    public static double deltaE2000(double[] lab1, double[] lab2) {
        double l1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
        double l2 = lab2[0], a2 = lab2[1], b2 = lab2[2];

        double c1 = Math.hypot(a1, b1);
        double c2 = Math.hypot(a2, b2);
        double cBar7 = Math.pow((c1 + c2) / 2.0, 7);
        double g = 0.5 * (1.0 - Math.sqrt(cBar7 / (cBar7 + Math.pow(25.0, 7))));

        double a1p = (1.0 + g) * a1;
        double a2p = (1.0 + g) * a2;
        double c1p = Math.hypot(a1p, b1);
        double c2p = Math.hypot(a2p, b2);
        double h1p = hueDegrees(b1, a1p);
        double h2p = hueDegrees(b2, a2p);

        double dLp = l2 - l1;
        double dCp = c2p - c1p;
        double dhp;
        if (c1p * c2p == 0) {
            dhp = 0;
        } else {
            dhp = h2p - h1p;
            if (dhp > 180) dhp -= 360;
            else if (dhp < -180) dhp += 360;
        }
        double dHp = 2.0 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dhp / 2.0));

        double lBarP = (l1 + l2) / 2.0;
        double cBarP = (c1p + c2p) / 2.0;
        double hBarP;
        if (c1p * c2p == 0) {
            hBarP = h1p + h2p;
        } else if (Math.abs(h1p - h2p) <= 180) {
            hBarP = (h1p + h2p) / 2.0;
        } else if (h1p + h2p < 360) {
            hBarP = (h1p + h2p + 360) / 2.0;
        } else {
            hBarP = (h1p + h2p - 360) / 2.0;
        }

        double t = 1.0
                - 0.17 * Math.cos(Math.toRadians(hBarP - 30))
                + 0.24 * Math.cos(Math.toRadians(2 * hBarP))
                + 0.32 * Math.cos(Math.toRadians(3 * hBarP + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * hBarP - 63));
        double dTheta = 30.0 * Math.exp(-Math.pow((hBarP - 275.0) / 25.0, 2));
        double cBarP7 = Math.pow(cBarP, 7);
        double rc = 2.0 * Math.sqrt(cBarP7 / (cBarP7 + Math.pow(25.0, 7)));
        double lMinus50Sq = Math.pow(lBarP - 50.0, 2);
        double sl = 1.0 + 0.015 * lMinus50Sq / Math.sqrt(20.0 + lMinus50Sq);
        double sc = 1.0 + 0.045 * cBarP;
        double sh = 1.0 + 0.015 * cBarP * t;
        double rt = -Math.sin(Math.toRadians(2.0 * dTheta)) * rc;

        double tl = dLp / sl, tc = dCp / sc, th = dHp / sh;
        return Math.sqrt(tl * tl + tc * tc + th * th + rt * tc * th);
    }

    private static double hueDegrees(double b, double aPrime) {
        if (b == 0 && aPrime == 0) return 0;
        double h = Math.toDegrees(Math.atan2(b, aPrime));
        return h < 0 ? h + 360 : h;
    }

    private static double srgbToLinear(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
