package com.ankita.h2sdosimeter.analysis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.ankita.h2sdosimeter.model.ColourAnalysisResult;

import java.io.InputStream;

/**
 * BadgeAnalysisPipeline - Phase 4 main on-device analysis coordinator.
 *
 * Runs synchronously on whatever thread it is called from.
 * Call from a background thread (ProcessingActivity uses a Handler thread).
 *
 * Pipeline steps:
 *   1. Decode the JPEG bitmap from the FileProvider URI.
 *   2. Run ImageQualityChecker - reject dark, blurry or overexposed images.
 *   3. Run ColourAnalysisService - extract sensor and reference region RGB.
 *   4. Run ReferenceCorrectionService - apply white-balance correction.
 *   5. Compute colour difference (deviation from pure white).
 *   6. Return a ColourAnalysisResult with all intermediate values.
 *
 * SAFETY NOTE:
 * This pipeline does NOT produce a validated H2S ppm.hr concentration.
 * It produces a colour-change indicator. Conversion to exposure requires
 * a laboratory-validated calibration curve that does not exist in this
 * build. The result is clearly marked "calibration required".
 */
public class BadgeAnalysisPipeline {

    private static final String TAG = "BadgeAnalysis";

    /** Maximum bitmap dimension to decode (saves memory on large JPEGs). */
    private static final int MAX_DECODE_DIM = 1200;

    /**
     * Runs the full analysis pipeline.
     *
     * @param context  Application context (for ContentResolver).
     * @param imageUri file:// or content:// URI of the captured JPEG.
     * @return ColourAnalysisResult with status and all extracted values.
     */
    public static ColourAnalysisResult analyse(Context context, Uri imageUri) {
        if (imageUri == null) {
            return ColourAnalysisResult.demo();
        }

        Log.d(TAG, "analyse() called with URI: " + imageUri + " scheme=" + imageUri.getScheme());

        // ------------------------------------------------------------------
        // Step 1: Decode bitmap
        // ------------------------------------------------------------------
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
            // ------------------------------------------------------------------
            // Step 2: Image quality check
            // ------------------------------------------------------------------
            ImageQualityChecker.QualityReport quality = ImageQualityChecker.check(bitmap);

            if (!quality.acceptable) {
                return ColourAnalysisResult.qualityRejected(
                        quality.rejectReason,
                        quality.brightness,
                        quality.sharpness);
            }

            // ------------------------------------------------------------------
            // Step 3: Extract sensor and reference regions
            // ------------------------------------------------------------------
            ColourAnalysisService.RegionColours regions =
                    ColourAnalysisService.extractRegions(bitmap);

            if (regions == null) {
                return ColourAnalysisResult.error("Colour region extraction failed");
            }

            // ------------------------------------------------------------------
            // Step 4: Apply reference correction
            // ------------------------------------------------------------------
            int[] corrected = ReferenceCorrectionService.correct(
                    regions.sensorR, regions.sensorG, regions.sensorB,
                    regions.refR,    regions.refG,    regions.refB,
                    regions.referenceDetected);

            // ------------------------------------------------------------------
            // Step 5: Colour difference
            // ------------------------------------------------------------------
            double diff = ReferenceCorrectionService.colourDifference(
                    corrected[0], corrected[1], corrected[2]);

            // ------------------------------------------------------------------
            // Step 6: Assemble result
            // ------------------------------------------------------------------
            String qualityLabel = quality.qualityLabel();

            String statusMsg = regions.referenceDetected
                    ? "Analysis complete. Reference patch detected."
                    : "Analysis complete. Reference patch NOT detected - correction not applied.";

            return new ColourAnalysisResult.Builder()
                    .status(ColourAnalysisResult.Status.SUCCESS)
                    .statusMessage(statusMsg)
                    .isRealCapture(true)
                    .rawSensor(regions.sensorR, regions.sensorG, regions.sensorB)
                    .reference(regions.refR,    regions.refG,    regions.refB)
                    .corrected(corrected[0],     corrected[1],    corrected[2])
                    .colourDifference(diff)
                    .brightness(quality.brightness)
                    .sharpness(quality.sharpness)
                    .imageQualityLabel(qualityLabel)
                    .build();

        } finally {
            bitmap.recycle();
        }
    }

    // ------------------------------------------------------------------
    // Private: efficient bitmap decode
    // ------------------------------------------------------------------

    /**
     * Decodes the JPEG at the given URI, downsampling if either dimension
     * exceeds maxDim (avoids OOM on high-resolution phone cameras).
     *
     * Supports both content:// (FileProvider) and file:// URIs.
     */
    private static Bitmap decodeSampledBitmap(Context context, Uri uri, int maxDim)
            throws Exception {

        // First pass: just read dimensions
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        if ("file".equals(uri.getScheme())) {
            // Direct file access - no ContentResolver needed
            BitmapFactory.decodeFile(uri.getPath(), opts);
        } else {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, opts);
            }
        }

        // Calculate inSampleSize
        int sampleSize = 1;
        int rawW = opts.outWidth;
        int rawH = opts.outHeight;
        if (rawW <= 0 || rawH <= 0) {
            throw new Exception("Could not read image dimensions from URI: " + uri);
        }
        while (rawW / sampleSize > maxDim || rawH / sampleSize > maxDim) {
            sampleSize *= 2;
        }

        // Second pass: decode with inSampleSize
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

        Log.d(TAG, "Decoded bitmap: " + (bmp != null ? bmp.getWidth() + "x" + bmp.getHeight() : "null")
                + " from " + uri.getScheme() + " (sample=" + sampleSize + ")");
        return bmp;
    }
}
