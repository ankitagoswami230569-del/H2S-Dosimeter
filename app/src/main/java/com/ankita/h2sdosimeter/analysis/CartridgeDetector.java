package com.ankita.h2sdosimeter.analysis;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * CartridgeDetector — detects a bordered H2S dosimeter cartridge in a photo
 * and returns the four internal ROIs (strip, white patch, grey patch, black patch).
 *
 * PIPELINE:
 *   1. Convert to HSV — colour-range detection works better in HSV under varying lighting.
 *   2. Threshold for the border colour (e.g. magenta/neon). The Hue range is deliberately
 *      wide (±15° around the target hue) to tolerate lighting variation.
 *   3. Find contours in the mask. Filter by:
 *        - Area: must be large enough to be a real cartridge, not noise.
 *        - Shape: approximate to a quadrilateral (4 corners).
 *        - Aspect ratio: must match the expected cartridge shape within tolerance.
 *      If nothing passes → FAILURE (retake).
 *   4. Perspective-warp the best candidate quad to a canonical top-down rectangle
 *      (CANONICAL_W × CANONICAL_H pixels). This corrects angle and distance.
 *   5. Within the normalised rectangle, extract four fixed ROIs based on known
 *      relative positions inside the cartridge design:
 *        - STRIP    — left portion of the interior
 *        - WHITE    — top of the right reference column
 *        - GREY     — middle of the right reference column
 *        - BLACK    — bottom of the right reference column
 *
 * FAILURE MODES (all return DetectionResult.failure()):
 *   - No mask pixels found (border colour not present)
 *   - No contour large enough
 *   - No quadrilateral contour found
 *   - Aspect ratio of detected quad does not match expected cartridge shape
 *   - Multiple candidates found and none clearly best-matches the shape
 *
 * DOES NOT perform colour correction or ppm calculation. Returns raw pixel Rects only.
 */
public class CartridgeDetector {

    private static final String TAG = "CartridgeDetector";

    // ------------------------------------------------------------------
    // Cartridge border colour — HSV range (magenta/neon example)
    // Adjust HUE_CENTER for your actual border colour:
    //   Red/magenta ≈ 150–170°, Green ≈ 60–80°, Blue ≈ 100–120°, Yellow ≈ 25–35°
    // The ±HUE_TOLERANCE gives a wide enough band for lighting variation.
    // ------------------------------------------------------------------
    private static final double HUE_CENTER    = 155.0; // magenta/pink in OpenCV (0–180 scale)
    private static final double HUE_TOLERANCE = 18.0;  // ±18° band
    private static final double SAT_MIN       = 100.0; // reject desaturated (grey) pixels
    private static final double VAL_MIN       =  50.0; // reject very dark pixels

    // ------------------------------------------------------------------
    // Shape / size constraints
    // ------------------------------------------------------------------

    /** Minimum fraction of image area the cartridge contour must cover. */
    private static final double MIN_AREA_FRACTION = 0.02;  // at least 2% of image

    /** Maximum fraction — if it fills >95% something is wrong (too close / edge cut). */
    private static final double MAX_AREA_FRACTION = 0.95;

    /**
     * Expected cartridge aspect ratio (width / height).
     * Adjust this to match your actual cartridge dimensions.
     * Example: a card 85mm wide × 55mm tall → ratio ≈ 1.55
     */
    private static final double EXPECTED_ASPECT   = 0.6;   // taller than wide (portrait)
    private static final double ASPECT_TOLERANCE  = 0.35;  // ±35% tolerance

    /**
     * Polygon approximation epsilon as a fraction of arc length.
     * Smaller = tighter fit (more corners), larger = coarser (fewer corners).
     * 0.04 typically gives 4 corners for a rectangle even when slightly curved.
     */
    private static final double POLY_EPSILON_FRACTION = 0.04;

    // ------------------------------------------------------------------
    // Canonical (warped) output size in pixels
    // ------------------------------------------------------------------
    private static final int CANONICAL_W = 400;
    private static final int CANONICAL_H = 650;

    // ------------------------------------------------------------------
    // Fixed relative ROI positions inside the canonical cartridge
    // These fractions are relative to CANONICAL_W / CANONICAL_H.
    // Tune these to match your actual cartridge layout.
    //
    //  ┌─────────────────────────────┐
    //  │  [STRIP area — left 60%]    │
    //  │                   [WHITE ]  │  top-right reference column
    //  │                   [GREY  ]  │  mid-right reference column
    //  │                   [BLACK ]  │  bot-right reference column
    //  └─────────────────────────────┘
    //
    // ------------------------------------------------------------------

    // Strip ROI — left 55% width, vertically centred (10%–90% height)
    private static final double STRIP_X      = 0.05;
    private static final double STRIP_Y      = 0.10;
    private static final double STRIP_W      = 0.55;
    private static final double STRIP_H      = 0.80;

    // Reference column — right side (63%–93% x), each patch 20% height
    private static final double REF_X        = 0.63;
    private static final double REF_W        = 0.30;
    private static final double WHITE_Y      = 0.08;
    private static final double WHITE_H      = 0.22;
    private static final double GREY_Y       = 0.38;
    private static final double GREY_H       = 0.22;
    private static final double BLACK_Y      = 0.68;
    private static final double BLACK_H      = 0.22;

    // ------------------------------------------------------------------
    // Public result
    // ------------------------------------------------------------------

    public static class DetectionResult {

        public final boolean success;
        public final String  failureReason;  // non-null on failure

        /**
         * The four ROIs, in pixels within the CANONICAL warped image.
         * Only valid when success == true.
         */
        public final Rect stripRoi;
        public final Rect whiteRoi;
        public final Rect greyRoi;
        public final Rect blackRoi;

        /**
         * The warped canonical bitmap (CANONICAL_W × CANONICAL_H).
         * Useful for debug visualisation. Only valid when success == true.
         */
        public final Bitmap warpedBitmap;

        private DetectionResult(String reason) {
            this.success       = false;
            this.failureReason = reason;
            this.stripRoi      = null;
            this.whiteRoi      = null;
            this.greyRoi       = null;
            this.blackRoi      = null;
            this.warpedBitmap  = null;
        }

        private DetectionResult(Rect strip, Rect white, Rect grey, Rect black,
                                 Bitmap warped) {
            this.success       = true;
            this.failureReason = null;
            this.stripRoi      = strip;
            this.whiteRoi      = white;
            this.greyRoi       = grey;
            this.blackRoi      = black;
            this.warpedBitmap  = warped;
        }

        public static DetectionResult failure(String reason) {
            Log.w(TAG, "[DETECT FAIL] " + reason);
            return new DetectionResult(reason);
        }

        public static DetectionResult success(Rect strip, Rect white, Rect grey,
                                               Rect black, Bitmap warped) {
            return new DetectionResult(strip, white, grey, black, warped);
        }
    }

    // ------------------------------------------------------------------
    // Main entry point
    // ------------------------------------------------------------------

    /**
     * Detects the cartridge in the given bitmap and returns the four ROIs.
     *
     * @param input  The captured photo as a Bitmap (any resolution).
     * @return DetectionResult — check result.success before using ROIs.
     */
    public static DetectionResult detect(Bitmap input) {
        if (input == null || input.isRecycled()) {
            return DetectionResult.failure("Input bitmap is null or recycled");
        }

        // ── Step 1: Convert Bitmap → OpenCV Mat ─────────────────────────
        Mat bgr = new Mat();
        Utils.bitmapToMat(input, bgr);  // produces BGRA by default
        Imgproc.cvtColor(bgr, bgr, Imgproc.COLOR_BGRA2BGR);

        int imgArea = bgr.rows() * bgr.cols();
        Log.d(TAG, "Input size: " + bgr.cols() + "×" + bgr.rows());

        // ── Step 2: Convert to HSV ───────────────────────────────────────
        // HSV separates hue (colour identity) from brightness, making colour
        // detection far more robust under different lighting conditions.
        Mat hsv = new Mat();
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);

        // ── Step 3: Threshold for border colour ──────────────────────────
        // Build a binary mask: white = border colour pixels, black = everything else.
        // Using inRange with an HSV band centred on the border hue.
        Mat mask = buildColourMask(hsv);

        // Check that we found a meaningful amount of border colour
        int maskPixels = Core.countNonZero(mask);
        if (maskPixels < imgArea * 0.001) {
            // Less than 0.1% of image is border colour — border not visible
            bgr.release(); hsv.release(); mask.release();
            return DetectionResult.failure(
                    "Cartridge border colour not detected in image. "
                    + "Ensure the border is visible and retake the photo.");
        }
        Log.d(TAG, "Border mask pixels: " + maskPixels);

        // ── Step 4: Morphological cleanup ───────────────────────────────
        // Remove small noise spots and connect broken border segments.
        Mat kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT, new Size(7, 7));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,  kernel);
        kernel.release();

        // ── Step 5: Find contours ────────────────────────────────────────
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        if (contours.isEmpty()) {
            bgr.release(); hsv.release(); mask.release();
            return DetectionResult.failure(
                    "No contours found in border colour mask. Retake photo.");
        }

        // ── Step 6: Find best cartridge candidate ────────────────────────
        MatOfPoint2f bestQuad = null;
        double bestScore = Double.MAX_VALUE;
        int candidates = 0;

        for (MatOfPoint contour : contours) {
            // Filter by area — must be large enough to be the cartridge border
            double area = Imgproc.contourArea(contour);
            double areaFrac = area / imgArea;
            if (areaFrac < MIN_AREA_FRACTION || areaFrac > MAX_AREA_FRACTION) {
                continue;
            }

            // Approximate contour to a polygon
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double arcLen = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx,
                    POLY_EPSILON_FRACTION * arcLen, true);
            contour2f.release();

            // Must be a quadrilateral (4 corners) to be a rectangle-bordered cartridge
            if (approx.rows() != 4) {
                approx.release();
                continue;
            }

            // Check convexity — a proper rectangle-like border should be convex
            MatOfPoint approxInt = new MatOfPoint(approx.toArray());
            if (!Imgproc.isContourConvex(approxInt)) {
                approxInt.release();
                approx.release();
                continue;
            }
            approxInt.release();

            // Check aspect ratio of the bounding rectangle
            Rect bRect = Imgproc.boundingRect(new MatOfPoint(approx.toArray()));
            if (bRect.height == 0) { approx.release(); continue; }
            double aspectRatio = (double) bRect.width / bRect.height;
            double aspectError = Math.abs(aspectRatio - EXPECTED_ASPECT) / EXPECTED_ASPECT;

            if (aspectError > ASPECT_TOLERANCE) {
                Log.d(TAG, "Candidate rejected: aspectRatio=" +
                        String.format("%.2f", aspectRatio) +
                        " expected=" + EXPECTED_ASPECT +
                        " error=" + String.format("%.2f", aspectError));
                approx.release();
                continue;
            }

            // Score = aspect error (lower is better)
            candidates++;
            if (aspectError < bestScore) {
                if (bestQuad != null) bestQuad.release();
                bestQuad = approx;
                bestScore = aspectError;
            } else {
                approx.release();
            }
        }

        // Release contour memory
        for (MatOfPoint c : contours) c.release();
        bgr.release(); hsv.release(); mask.release();

        if (bestQuad == null) {
            return DetectionResult.failure(
                    "No valid cartridge shape found. "
                    + "Ensure the full bordered cartridge is visible. "
                    + "Candidates found with wrong shape: " + candidates);
        }

        Log.d(TAG, "Best candidate: aspectError=" + String.format("%.3f", bestScore)
                + " from " + candidates + " shape-valid candidate(s)");

        // ── Step 7: Re-decode for warp (need fresh BGR Mat) ─────────────
        Mat bgrFresh = new Mat();
        Utils.bitmapToMat(input, bgrFresh);
        Imgproc.cvtColor(bgrFresh, bgrFresh, Imgproc.COLOR_BGRA2BGR);

        // ── Step 8: Perspective warp ─────────────────────────────────────
        // Order the 4 corners: top-left, top-right, bottom-right, bottom-left.
        Point[] orderedCorners = orderCorners(bestQuad.toArray());
        bestQuad.release();

        MatOfPoint2f src = new MatOfPoint2f(orderedCorners);
        MatOfPoint2f dst = new MatOfPoint2f(
                new Point(0,            0),
                new Point(CANONICAL_W - 1, 0),
                new Point(CANONICAL_W - 1, CANONICAL_H - 1),
                new Point(0,            CANONICAL_H - 1)
        );

        Mat warpMatrix = Imgproc.getPerspectiveTransform(src, dst);
        Mat warped = new Mat();
        Imgproc.warpPerspective(bgrFresh, warped, warpMatrix,
                new Size(CANONICAL_W, CANONICAL_H));
        warpMatrix.release();
        bgrFresh.release();
        src.release();
        dst.release();

        // Convert warped Mat → Bitmap for return / debug
        Bitmap warpedBitmap = Bitmap.createBitmap(
                CANONICAL_W, CANONICAL_H, Bitmap.Config.ARGB_8888);
        Mat warpedRgba = new Mat();
        Imgproc.cvtColor(warped, warpedRgba, Imgproc.COLOR_BGR2BGRA);
        Utils.matToBitmap(warpedRgba, warpedBitmap);
        warped.release();
        warpedRgba.release();

        // ── Step 9: Compute fixed ROIs within canonical image ────────────
        Rect stripRoi = roiFromFractions(STRIP_X,  STRIP_Y,  STRIP_W,  STRIP_H);
        Rect whiteRoi = roiFromFractions(REF_X,    WHITE_Y,  REF_W,    WHITE_H);
        Rect greyRoi  = roiFromFractions(REF_X,    GREY_Y,   REF_W,    GREY_H);
        Rect blackRoi = roiFromFractions(REF_X,    BLACK_Y,  REF_W,    BLACK_H);

        Log.i(TAG, "Cartridge detected successfully.");
        Log.d(TAG, "StripROI=" + stripRoi + " WhiteROI=" + whiteRoi
                + " GreyROI=" + greyRoi + " BlackROI=" + blackRoi);

        return DetectionResult.success(stripRoi, whiteRoi, greyRoi, blackRoi, warpedBitmap);
    }

    // ------------------------------------------------------------------
    // Build the border-colour HSV mask
    // ------------------------------------------------------------------

    /**
     * Creates a binary mask highlighting pixels that fall within the
     * border colour's HSV range.
     *
     * Handles hue wrap-around (e.g. red spans both ends of the 0–180 scale):
     * if the hue band crosses 0 or 180, it splits into two inRange calls and OR's them.
     */
    private static Mat buildColourMask(Mat hsv) {
        double hLow  = HUE_CENTER - HUE_TOLERANCE;
        double hHigh = HUE_CENTER + HUE_TOLERANCE;

        Mat mask = new Mat();

        if (hLow < 0) {
            // Wrap-around: split into [180+hLow, 180] ∪ [0, hHigh]
            Mat mask1 = new Mat(), mask2 = new Mat();
            Core.inRange(hsv,
                    new Scalar(180 + hLow, SAT_MIN, VAL_MIN),
                    new Scalar(180,        255,      255), mask1);
            Core.inRange(hsv,
                    new Scalar(0,    SAT_MIN, VAL_MIN),
                    new Scalar(hHigh, 255,    255), mask2);
            Core.bitwise_or(mask1, mask2, mask);
            mask1.release(); mask2.release();
        } else if (hHigh > 180) {
            // Wrap-around: split into [hLow, 180] ∪ [0, hHigh-180]
            Mat mask1 = new Mat(), mask2 = new Mat();
            Core.inRange(hsv,
                    new Scalar(hLow, SAT_MIN, VAL_MIN),
                    new Scalar(180,  255,     255), mask1);
            Core.inRange(hsv,
                    new Scalar(0,           SAT_MIN, VAL_MIN),
                    new Scalar(hHigh - 180, 255,     255), mask2);
            Core.bitwise_or(mask1, mask2, mask);
            mask1.release(); mask2.release();
        } else {
            // Normal case — no wrap
            Core.inRange(hsv,
                    new Scalar(hLow,  SAT_MIN, VAL_MIN),
                    new Scalar(hHigh, 255,     255), mask);
        }

        return mask;
    }

    // ------------------------------------------------------------------
    // Order quad corners: top-left, top-right, bottom-right, bottom-left
    // ------------------------------------------------------------------

    /**
     * Given 4 unordered corner points, returns them in the order:
     * [0] top-left, [1] top-right, [2] bottom-right, [3] bottom-left.
     *
     * Method: sum (x+y) is smallest for top-left, largest for bottom-right.
     *         difference (x-y) is smallest for top-right, largest for bottom-left.
     */
    private static Point[] orderCorners(Point[] pts) {
        Point tl = pts[0], tr = pts[0], br = pts[0], bl = pts[0];
        double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE;
        double minDiff = Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE;

        for (Point p : pts) {
            double s = p.x + p.y;
            double d = p.x - p.y;
            if (s < minSum) { minSum = s; tl = p; }
            if (s > maxSum) { maxSum = s; br = p; }
            if (d < minDiff) { minDiff = d; bl = p; }  // note: small x-y → left side
            if (d > maxDiff) { maxDiff = d; tr = p; }
        }

        // Resolve: tl=min sum, br=max sum, tr=max diff, bl=min diff
        // Re-derive to be safe (avoid reusing the same point for two roles)
        double[] sums  = {pts[0].x+pts[0].y, pts[1].x+pts[1].y,
                          pts[2].x+pts[2].y, pts[3].x+pts[3].y};
        double[] diffs = {pts[0].x-pts[0].y, pts[1].x-pts[1].y,
                          pts[2].x-pts[2].y, pts[3].x-pts[3].y};

        int tlIdx = argMin(sums),  brIdx = argMax(sums);
        int trIdx = argMax(diffs), blIdx = argMin(diffs);

        return new Point[]{ pts[tlIdx], pts[trIdx], pts[brIdx], pts[blIdx] };
    }

    private static int argMin(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) if (arr[i] < arr[idx]) idx = i;
        return idx;
    }

    private static int argMax(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) if (arr[i] > arr[idx]) idx = i;
        return idx;
    }

    // ------------------------------------------------------------------
    // Helper: convert fractional position to pixel Rect in canonical image
    // ------------------------------------------------------------------

    private static Rect roiFromFractions(double xFrac, double yFrac,
                                          double wFrac, double hFrac) {
        int x = (int)(xFrac * CANONICAL_W);
        int y = (int)(yFrac * CANONICAL_H);
        int w = (int)(wFrac * CANONICAL_W);
        int h = (int)(hFrac * CANONICAL_H);
        // Clamp to image bounds
        x = Math.max(0, Math.min(x, CANONICAL_W - 1));
        y = Math.max(0, Math.min(y, CANONICAL_H - 1));
        w = Math.max(1, Math.min(w, CANONICAL_W - x));
        h = Math.max(1, Math.min(h, CANONICAL_H - y));
        return new Rect(x, y, w, h);
    }
}
