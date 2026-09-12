package com.ankita.h2sdosimeter.analysis;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * ImageQualityChecker - Phase 4 on-device image quality assessment.
 *
 * Checks two properties before the colour analysis pipeline runs:
 *
 * 1. BRIGHTNESS - mean luminance of the centre region.
 *    Rejects images that are too dark (< MIN_BRIGHTNESS) or
 *    overexposed (> MAX_BRIGHTNESS).
 *
 * 2. SHARPNESS - Laplacian variance of the greyscale image.
 *    A low variance means the image is blurry. We sample a
 *    sub-region for speed (max 200 x 200 px).
 *
 * These are heuristics, not validated scientific thresholds.
 */
public class ImageQualityChecker {

    // ------------------------------------------------------------------
    // Thresholds (tunable)
    // ------------------------------------------------------------------

    /** Minimum mean luminance (0-255) to accept the image. */
    public static final double MIN_BRIGHTNESS = 20.0;
    public static final double MAX_BRIGHTNESS = 254.0;
    public static final double MIN_SHARPNESS  = 5.0;

    // ------------------------------------------------------------------
    // Public result container
    // ------------------------------------------------------------------

    public static class QualityReport {
        public final double brightness;
        public final double sharpness;
        public final boolean acceptable;
        public final String rejectReason; // null if acceptable

        QualityReport(double brightness, double sharpness,
                      boolean acceptable, String rejectReason) {
            this.brightness   = brightness;
            this.sharpness    = sharpness;
            this.acceptable   = acceptable;
            this.rejectReason = rejectReason;
        }

        public String qualityLabel() {
            if (!acceptable) return "Poor";
            if (brightness < 80 || sharpness < 150) return "Acceptable";
            return "Good";
        }
    }

    // ------------------------------------------------------------------
    // Main entry point
    // ------------------------------------------------------------------

    /**
     * Analyses the given bitmap and returns a QualityReport.
     * Always processes a downsampled view for performance.
     *
     * @param bitmap Source bitmap (any size). Not recycled by this method.
     */
    public static QualityReport check(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return new QualityReport(0, 0, false, "Bitmap is null or recycled");
        }

        // Downsample to at most 400x400 for speed
        Bitmap sample = scaledSample(bitmap, 400);

        double brightness = computeBrightness(sample);
        double sharpness  = computeSharpness(sample);

        if (sample != bitmap) sample.recycle();

        // Evaluate
        if (brightness < MIN_BRIGHTNESS) {
            return new QualityReport(brightness, sharpness, false,
                    "Image too dark (brightness=" + String.format("%.1f", brightness)
                            + ", min=" + MIN_BRIGHTNESS + ")");
        }
        if (brightness > MAX_BRIGHTNESS) {
            return new QualityReport(brightness, sharpness, false,
                    "Image overexposed (brightness=" + String.format("%.1f", brightness)
                            + ", max=" + MAX_BRIGHTNESS + ")");
        }
        if (sharpness < MIN_SHARPNESS) {
            return new QualityReport(brightness, sharpness, false,
                    "Image too blurry (sharpness=" + String.format("%.1f", sharpness)
                            + ", min=" + MIN_SHARPNESS + ")");
        }

        return new QualityReport(brightness, sharpness, true, null);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Computes mean luminance of the centre 50% of the bitmap.
     * Luminance = 0.299R + 0.587G + 0.114B (standard Rec.601).
     */
    private static double computeBrightness(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        // Centre 50%
        int x0 = w / 4;
        int y0 = h / 4;
        int x1 = w * 3 / 4;
        int y1 = h * 3 / 4;

        double sum = 0;
        long count = 0;

        for (int y = y0; y < y1; y += 2) {         // stride 2 for speed
            for (int x = x0; x < x1; x += 2) {
                int pixel = bmp.getPixel(x, y);
                double lum = 0.299 * Color.red(pixel)
                           + 0.587 * Color.green(pixel)
                           + 0.114 * Color.blue(pixel);
                sum += lum;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    /**
     * Estimates sharpness using the variance of a discrete Laplacian
     * applied to the greyscale image.
     * High variance -> well-defined edges -> sharp.
     */
    private static double computeSharpness(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w < 3 || h < 3) return 0;

        // Build greyscale array (stride 2 for speed)
        double sum  = 0;
        double sum2 = 0;
        long count  = 0;

        for (int y = 1; y < h - 1; y += 2) {
            for (int x = 1; x < w - 1; x += 2) {
                double lap = laplacian(bmp, x, y);
                sum  += lap;
                sum2 += lap * lap;
                count++;
            }
        }

        if (count == 0) return 0;
        double mean = sum / count;
        return (sum2 / count) - (mean * mean);   // variance
    }

    /** 3x3 Laplacian kernel value at (x,y): -4*centre + 4 neighbours. */
    private static double laplacian(Bitmap bmp, int x, int y) {
        return grey(bmp, x, y - 1)
             + grey(bmp, x - 1, y)
             - 4.0 * grey(bmp, x, y)
             + grey(bmp, x + 1, y)
             + grey(bmp, x, y + 1);
    }

    private static double grey(Bitmap bmp, int x, int y) {
        int p = bmp.getPixel(x, y);
        return 0.299 * Color.red(p)
             + 0.587 * Color.green(p)
             + 0.114 * Color.blue(p);
    }

    /** Returns a scaled-down copy if larger than maxDim, else original. */
    private static Bitmap scaledSample(Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;

        float scale = (float) maxDim / Math.max(w, h);
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }
}
