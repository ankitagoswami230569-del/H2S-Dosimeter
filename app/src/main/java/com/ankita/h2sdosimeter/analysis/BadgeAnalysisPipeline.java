package com.ankita.h2sdosimeter.analysis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.ankita.h2sdosimeter.model.ColourAnalysisResult;

import java.io.InputStream;
import java.util.Locale;

/**
 * BadgeAnalysisPipeline — marked-badge pipeline.
 *
 *   1. Decode bitmap.
 *   2. Image quality check.
 *   3. MarkedBadgeDetector: the 4 red corner marks locate the strip; the
 *      WHITE / LIGHT / DARK reference card is found next to it.
 *      Missing marks or card → REGION_NOT_FOUND (no colour, no ppm·hr).
 *   4. StripColourMeasurement: two-point lighting correction using the card's
 *      white and dark patches → CIE Lab → ΔE₀₀ from white.
 *   5. SUCCESS. ΔE₀₀ is the value CalibrationCurve converts to ppm·hr.
 *
 * DEMO: imageUri == null returns the demo result only.
 */
public class BadgeAnalysisPipeline {

    private static final String TAG = "BadgeAnalysis";
    private static final int MAX_DECODE_DIM = 1200;

    /** Strip luma variation above this is reported as uneven colour. */
    private static final double UNEVEN_STRIP_VARIATION = 0.12;

    public static ColourAnalysisResult analyse(Context context, Uri imageUri) {

        if (imageUri == null) {
            return ColourAnalysisResult.demo();
        }

        Log.d(TAG, "analyse() URI=" + imageUri);

        // ── Step 1: Decode ────────────────────────────────────────────────
        Bitmap bitmap;
        try {
            bitmap = decodeSampledBitmap(context, imageUri, MAX_DECODE_DIM);
        } catch (Exception e) {
            Log.e(TAG, "Bitmap decode failed", e);
            return ColourAnalysisResult.error("Could not load image: " + e.getMessage());
        }
        if (bitmap == null) {
            return ColourAnalysisResult.error("Image file could not be decoded");
        }

        try {
            // ── Step 2: Image quality ─────────────────────────────────────
            ImageQualityChecker.QualityReport quality = ImageQualityChecker.check(bitmap);
            if (!quality.acceptable) {
                return ColourAnalysisResult.qualityRejected(
                        quality.rejectReason, quality.brightness, quality.sharpness);
            }

            // ── Step 3: Strip + reference card detection ──────────────────
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            MarkedBadgeDetector.Result detection = MarkedBadgeDetector.detect(pixels, width, height);
            Log.i(TAG, "[DETECT] " + detection.debug);
            if (!detection.detected) {
                return ColourAnalysisResult.dosimeterNotDetected(detection.message, detection.debug);
            }

            // ── Step 4: Lighting-corrected colour measurement ─────────────
            StripColourMeasurement m = StripColourMeasurement.measure(detection);
            int[] raw       = round(detection.stripRgb);
            int[] white     = round(detection.whiteRgb);
            int[] corrected = round(m.correctedRgb);

            String msg = "Strip and reference card detected. Lighting corrected with the card.";
            if (detection.stripVariation > UNEVEN_STRIP_VARIATION) {
                msg += " Strip colour is uneven — value is the average of the strip centre.";
            }
            String debug = detection.debug + String.format(Locale.US,
                    " | dE00=%.2f dL=%.2f correctedLight=(%d,%d,%d)",
                    m.deltaE00, m.deltaL,
                    Math.round(m.correctedLightRgb[0]),
                    Math.round(m.correctedLightRgb[1]),
                    Math.round(m.correctedLightRgb[2]));
            Log.i(TAG, "[MEASURE] " + debug);

            // ── Step 5: Result ────────────────────────────────────────────
            return new ColourAnalysisResult.Builder()
                    .status(ColourAnalysisResult.Status.SUCCESS)
                    .statusMessage(msg)
                    .isRealCapture(true)
                    .rawSensor(raw[0], raw[1], raw[2])
                    .reference(white[0], white[1], white[2])
                    .corrected(corrected[0], corrected[1], corrected[2])
                    .colourDifference(m.rgbDistance)
                    .deltaE(m.deltaE00)
                    .lab(m.lab[0], m.lab[1], m.lab[2])
                    .referenceScaleDetected(true)
                    .brightness(quality.brightness)
                    .sharpness(quality.sharpness)
                    .imageQualityLabel(quality.qualityLabel())
                    .detectionDebug(debug)
                    .build();

        } finally {
            bitmap.recycle();
        }
    }

    private static int[] round(double[] rgb) {
        return new int[]{
                (int) Math.round(rgb[0]), (int) Math.round(rgb[1]), (int) Math.round(rgb[2])};
    }

    // ─────────────────────────────────────────────────────────────────────
    // Bitmap decode
    // ─────────────────────────────────────────────────────────────────────

    private static Bitmap decodeSampledBitmap(Context context, Uri uri, int maxDim)
            throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        if ("file".equals(uri.getScheme())) {
            BitmapFactory.decodeFile(uri.getPath(), opts);
        } else {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, opts);
            }
        }
        int sampleSize = 1;
        int rawW = opts.outWidth, rawH = opts.outHeight;
        if (rawW <= 0 || rawH <= 0) {
            throw new Exception("Could not read image dimensions from URI: " + uri);
        }
        while (rawW / sampleSize > maxDim || rawH / sampleSize > maxDim) sampleSize *= 2;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize       = sampleSize;
        opts.inPreferredConfig  = Bitmap.Config.ARGB_8888;
        Bitmap bmp;
        if ("file".equals(uri.getScheme())) {
            bmp = BitmapFactory.decodeFile(uri.getPath(), opts);
        } else {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
        }
        return bmp;
    }
}
