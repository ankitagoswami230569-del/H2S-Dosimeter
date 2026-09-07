package com.ankita.h2sdosimeter.analysis;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * RegionSampler - samples the average RGB of a normalised rectangular
 * region ({@link ReferenceCardSpec.NormRect}) from a RECTIFIED card bitmap
 * (i.e. the bitmap already produced by {@link CardDetector}).
 *
 * This class only reads pixels from an Android {@link Bitmap} - it has no
 * OpenCV dependency.
 */
public final class RegionSampler {

    private RegionSampler() { /* static entry point only */ }

    /**
     * Averages the RGB values of all pixels inside {@code region} (a
     * normalised rectangle in [0,1] card coordinates).
     *
     * @param rectifiedCard the rectified card bitmap (from CardDetector)
     * @param region        normalised region to sample
     * @return int[3] = {meanR, meanG, meanB}, or {0,0,0} if the bitmap /
     *         region is invalid or empty.
     */
    public static int[] sampleRegion(Bitmap rectifiedCard, ReferenceCardSpec.NormRect region) {
        if (rectifiedCard == null || rectifiedCard.isRecycled() || region == null) {
            return new int[]{0, 0, 0};
        }

        int w = rectifiedCard.getWidth();
        int h = rectifiedCard.getHeight();

        int x0 = clamp((int) Math.round(w * region.x), 0, w);
        int y0 = clamp((int) Math.round(h * region.y), 0, h);
        int x1 = clamp((int) Math.round(w * (region.x + region.width)), 0, w);
        int y1 = clamp((int) Math.round(h * (region.y + region.height)), 0, h);

        long sumR = 0, sumG = 0, sumB = 0, count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int pixel = rectifiedCard.getPixel(x, y);
                sumR += Color.red(pixel);
                sumG += Color.green(pixel);
                sumB += Color.blue(pixel);
                count++;
            }
        }

        if (count == 0) return new int[]{0, 0, 0};
        return new int[]{
                (int) (sumR / count),
                (int) (sumG / count),
                (int) (sumB / count)
        };
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
