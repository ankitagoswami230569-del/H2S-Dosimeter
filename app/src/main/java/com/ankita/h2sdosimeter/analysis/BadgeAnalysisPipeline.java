package com.ankita.h2sdosimeter.analysis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.ankita.h2sdosimeter.model.ColourAnalysisResult;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * BadgeAnalysisPipeline - Phase 4/5 main on-device analysis coordinator.
 *
 * Runs synchronously on whatever thread it is called from.
 * Call from a background thread (ProcessingActivity uses a Handler thread).
 *
 * Pipeline steps:
 *   1. Decode the JPEG bitmap from the FileProvider URI.
 *   2. Run ImageQualityChecker - reject dark, blurry or overexposed images.
 *   3. Run CardDetector - locate the printed reference card's ArUco markers
 *      and rectify the photo to a fixed-size, fronto-parallel card image.
 *      This is the ONLY lighting-DEPENDENT-camera-angle step; if it fails,
 *      the result status is CARD_NOT_DETECTED (no further analysis).
 *   4. Run RegionSampler - sample the reaction strip, the reference-scale
 *      swatches and the expiry patch from the rectified card.
 *   5. Run ColorCalibrator - fit a per-photo affine colour correction from
 *      the captured swatches vs. their known printed colours. This cancels
 *      THIS photo's lighting/white-balance bias, which is what makes the
 *      reading lighting-independent.
 *   6. Run ScaleReader - match the corrected strip colour against the
 *      corrected scale swatches (via CIELab / CIE76 delta-E) to get a
 *      continuous scale position.
 *   7. Return a ColourAnalysisResult with all intermediate values.
 *
 * SAFETY NOTE:
 * This pipeline does NOT produce a validated H2S ppm.hr concentration.
 * It produces a scale-position / colour-change indicator. Conversion to
 * exposure requires calibration points supplied via CalibrationActivity -
 * see CalibrationCurve. Until calibrated, results are clearly marked
 * "calibration required".
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
            // Step 3: Detect the reference card and rectify to a fixed layout
            // ------------------------------------------------------------------
            CardDetector.Detection detection = CardDetector.detect(bitmap);
            if (detection == null) {
                return ColourAnalysisResult.cardNotDetected(
                        "Could not detect all " + ReferenceCardSpec.MARKER_COUNT
                                + " ArUco reference-card markers, or the perspective "
                                + "correction was degenerate.",
                        quality.brightness,
                        quality.sharpness,
                        quality.qualityLabel());
            }

            Bitmap rectified = detection.rectified;
            try {
                // --------------------------------------------------------------
                // Step 4: Sample the reaction strip, scale swatches and expiry patch
                // --------------------------------------------------------------
                int[] stripRaw = RegionSampler.sampleRegion(rectified, ReferenceCardSpec.REACTION_STRIP_REGION);
                int[] expiryRaw = RegionSampler.sampleRegion(rectified, ReferenceCardSpec.EXPIRY_PATCH_REGION);

                List<ReferenceCardSpec.Swatch> swatchSpecs = ReferenceCardSpec.swatchList();
                int[][] capturedSwatches  = new int[swatchSpecs.size()][];
                int[][] referenceSwatches = new int[swatchSpecs.size()][];
                for (int i = 0; i < swatchSpecs.size(); i++) {
                    ReferenceCardSpec.Swatch spec = swatchSpecs.get(i);
                    capturedSwatches[i]  = RegionSampler.sampleRegion(rectified, spec.region);
                    referenceSwatches[i] = spec.rgb;
                }

                // --------------------------------------------------------------
                // Step 5: Fit this photo's colour correction from the swatches
                // --------------------------------------------------------------
                ColorCalibrator calibrator = ColorCalibrator.fit(capturedSwatches, referenceSwatches);
                if (calibrator == null) {
                    return ColourAnalysisResult.error(
                            "Colour calibration fit failed - need at least "
                                    + ColorCalibrator.MIN_SWATCHES + " valid reference swatches");
                }

                int[] correctedStrip  = calibrator.apply(stripRaw[0], stripRaw[1], stripRaw[2]);
                int[] correctedExpiry = calibrator.apply(expiryRaw[0], expiryRaw[1], expiryRaw[2]);

                List<ScaleReader.SwatchSample> correctedSwatches = new ArrayList<>();
                for (int i = 0; i < swatchSpecs.size(); i++) {
                    int[] correctedSwatch = calibrator.apply(
                            capturedSwatches[i][0], capturedSwatches[i][1], capturedSwatches[i][2]);
                    correctedSwatches.add(new ScaleReader.SwatchSample(
                            swatchSpecs.get(i).doseIndex, correctedSwatch));
                }

                // --------------------------------------------------------------
                // Step 6: Match the corrected strip colour against the scale
                // --------------------------------------------------------------
                ScaleReader.Reading reading = ScaleReader.read(correctedStrip, correctedSwatches);
                if (reading == null) {
                    return ColourAnalysisResult.error("Scale reading failed - insufficient swatches");
                }

                // Legacy colour-difference indicator, retained for backward-compatible display.
                double colourDiff = ReferenceCorrectionService.colourDifference(
                        correctedStrip[0], correctedStrip[1], correctedStrip[2]);

                // --------------------------------------------------------------
                // Step 7: Assemble result
                // --------------------------------------------------------------
                return new ColourAnalysisResult.Builder()
                        .status(ColourAnalysisResult.Status.SUCCESS)
                        .statusMessage("Analysis complete. Reference card detected and rectified.")
                        .isRealCapture(true)
                        .cardDetected(true)
                        .detectionConfidence(detection.confidence)
                        .rawSensor(stripRaw[0], stripRaw[1], stripRaw[2])
                        .reference(referenceSwatches[0][0], referenceSwatches[0][1], referenceSwatches[0][2])
                        .corrected(correctedStrip[0], correctedStrip[1], correctedStrip[2])
                        .correctedStrip(correctedStrip[0], correctedStrip[1], correctedStrip[2])
                        .colourDifference(colourDiff)
                        .scalePosition(reading.scalePosition)
                        .nearestSwatchDeltaE(reading.nearestDeltaE)
                        .expiryRgb(correctedExpiry[0], correctedExpiry[1], correctedExpiry[2])
                        .brightness(quality.brightness)
                        .sharpness(quality.sharpness)
                        .imageQualityLabel(quality.qualityLabel())
                        .build();

            } finally {
                rectified.recycle();
            }

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
