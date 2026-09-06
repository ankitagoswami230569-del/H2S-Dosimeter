package com.ankita.h2sdosimeter.analysis;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * ColourAnalysisService - Phase 4 local on-device colour extraction.
 *
 * WHAT THIS DOES:
 * Given a captured JPEG bitmap it extracts two region averages:
 *
 * 1. SENSOR REGION - the central 20% x 20% of the image.
 *    For a wristband dosimeter held flat and centred in the frame,
 *    the sensor strip (the part that changes colour with H2S exposure)
 *    will occupy approximately the centre of the photo.
 *
 * 2. REFERENCE REGION - the top-right 15% x 15% corner.
 *    H2S colorimetric wristbands typically include a printed colour
 *    reference scale along one edge. Placing the badge so the reference
 *    strip is toward the top-right matches the capture guidance shown
 *    to the user. This region provides a white-balance anchor.
 *
 * LIMITATION:
 * The regions are fixed heuristic crops - NOT derived from computer
 * vision or badge-specific shape detection. If the user does not
 * follow the capture guidance precisely, the regions will pick up
 * whatever is in those positions (e.g. background, skin, shadow).
 * Future phases should add contour detection to locate the badge
 * automatically.
 *
 * This class is intentionally simple: it only averages pixel colours.
 * No scientific H2S calibration is applied here.
 */
public class ColourAnalysisService {

    // ------------------------------------------------------------------
    // Region definitions (fractions of image width/height)
    // ------------------------------------------------------------------

    /** Sensor region - centre crop */
    private static final float SENSOR_X_START  = 0.40f;
    private static final float SENSOR_X_END    = 0.60f;
    private static final float SENSOR_Y_START  = 0.40f;
    private static final float SENSOR_Y_END    = 0.60f;

    /** Reference region - top-right corner */
    private static final float REF_X_START     = 0.80f;
    private static final float REF_X_END       = 0.95f;
    private static final float REF_Y_START     = 0.05f;
    private static final float REF_Y_END       = 0.20f;

    // ------------------------------------------------------------------
    // Public result container
    // ------------------------------------------------------------------

    public static class RegionColours {
        /** Mean R, G, B of the sensor region */
        public final int sensorR, sensorG, sensorB;
        /** Mean R, G, B of the reference region */
        public final int refR, refG, refB;
        /** True if the reference region looks like a plausible reference
         *  (mean brightness > 100 - i.e. it is not black/dark). */
        public final boolean referenceDetected;

        RegionColours(int sR, int sG, int sB,
                      int rR, int rG, int rB,
                      boolean refDetected) {
            sensorR  = sR; sensorG  = sG; sensorB  = sB;
            refR     = rR; refG     = rG; refB     = rB;
            referenceDetected = refDetected;
        }
    }

    // ------------------------------------------------------------------
    // Main entry point
    // ------------------------------------------------------------------

    /**
     * Extracts the average RGB from the sensor and reference regions of
     * the provided bitmap.
     *
     * @param bitmap Source bitmap (not recycled by this method).
     * @return RegionColours or null if the bitmap is invalid.
     */
    public static RegionColours extractRegions(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] sensor    = averageRegion(bitmap, w, h,
                SENSOR_X_START, SENSOR_Y_START,
                SENSOR_X_END,   SENSOR_Y_END);

        int[] reference = averageRegion(bitmap, w, h,
                REF_X_START, REF_Y_START,
                REF_X_END,   REF_Y_END);

        // Check reference brightness: mean > 80 means it is not fully dark
        double refBrightness = 0.299 * reference[0]
                             + 0.587 * reference[1]
                             + 0.114 * reference[2];
        boolean refDetected = refBrightness > 80.0;

        return new RegionColours(
                sensor[0], sensor[1], sensor[2],
                reference[0], reference[1], reference[2],
                refDetected);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Averages the RGB values of all pixels in the specified fractional
     * region of the bitmap. Samples every other pixel for speed.
     *
     * @return int[3] = {meanR, meanG, meanB}
     */
    private static int[] averageRegion(Bitmap bmp,
                                        int imgW, int imgH,
                                        float xStart, float yStart,
                                        float xEnd,   float yEnd) {
        int x0 = clamp((int)(imgW * xStart), 0, imgW - 1);
        int y0 = clamp((int)(imgH * yStart), 0, imgH - 1);
        int x1 = clamp((int)(imgW * xEnd),   0, imgW);
        int y1 = clamp((int)(imgH * yEnd),   0, imgH);

        long sumR = 0, sumG = 0, sumB = 0;
        long count = 0;

        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int pixel = bmp.getPixel(x, y);
                sumR  += Color.red(pixel);
                sumG  += Color.green(pixel);
                sumB  += Color.blue(pixel);
                count++;
            }
        }

        if (count == 0) return new int[]{0, 0, 0};
        return new int[]{
                (int)(sumR / count),
                (int)(sumG / count),
                (int)(sumB / count)
        };
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
