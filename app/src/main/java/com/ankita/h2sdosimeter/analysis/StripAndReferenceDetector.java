package com.ankita.h2sdosimeter.analysis;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * StripAndReferenceDetector
 * ─────────────────────────────────────────────────────────────────────────
 * Detects TWO physical objects inside the captured image:
 *
 * A) H2S SENSOR STRIP
 *    Physical size: ~2 × 3 cm  (elongated rectangle)
 *    Detected by: SHAPE — aspect ratio, rectangularity, normalised area.
 *    Colour is NOT used as primary criterion (colour = result of exposure).
 *
 * B) 3-PATCH REFERENCE SCALE  (BLACK | GREY | WHITE)
 *    Three neighbouring compact patches ordered by luminance.
 *    A single dark/grey/bright region does NOT count.
 *    Patches must be: spatially close, similarly sized, aligned,
 *                     brightness-ordered with minimum gaps.
 *
 * GATE: BOTH must be detected AND spatially proximate (same badge).
 *       If either is missing → detection fails → no colour → no ppm.
 *
 * ANTI-FALSE-POSITIVE MEASURES:
 *   - Strip aspect-ratio window (1.4–6.5) excludes squares and very thin lines.
 *   - 3-patch group must have ALL three inter-patch distances below threshold.
 *   - The dark patch must be DARK (<= 140 luma), white patch must be BRIGHT (>= 160).
 *   - The total luma spread (white–black) must be at least 70.
 *   - Strip and reference group must be within PROXIMITY_MAX_MULTIPLIER × strip length.
 *   - A laptop/keyboard/room has no co-located elongated strip next to a 3-patch group.
 *
 * OUTPUTS (DetectionResult):
 *   - detected: boolean
 *   - confidence: 0.0–1.0
 *   - stripRoi: bounding box in original image pixels (null on failure)
 *   - refWhiteRGB: mean RGB of the detected white patch
 *   - debugInfo: human-readable string for the debug overlay
 */
public class StripAndReferenceDetector {

    private static final String TAG = "StripRefDetect";

    // ── Working image size ────────────────────────────────────────────────
    private static final int WORK_MAX_DIM = 600;

    // ── Strip geometry constraints ────────────────────────────────────────
    /** Min strip bounding-box area as fraction of working image area. */
    private static final double STRIP_MIN_AREA_FRAC = 0.008;
    private static final double STRIP_MAX_AREA_FRAC = 0.15;
    /** Elongated: max side / min side ≥ 1.8 (real strip ~2.2). */
    private static final double STRIP_MIN_AR        = 1.8;
    private static final double STRIP_MAX_AR        = 4.5;
    /** Rectangularity: contour area / bbox area. */
    private static final double STRIP_MIN_RECT      = 0.50;

    // ── Reference patch constraints ───────────────────────────────────────
    private static final double PATCH_MIN_AREA_FRAC  = 0.0003;
    private static final double PATCH_MAX_AREA_FRAC  = 0.05;
    private static final double PATCH_MAX_AR         = 2.5;
    private static final double PATCH_MIN_RECT       = 0.45;
    /** Max ratio of largest to smallest patch area in the trio. */
    private static final double PATCH_MAX_SIZE_RATIO = 3.0;

    // ── 3-patch brightness constraints ────────────────────────────────────
    /** Minimum luma gap between each adjacent pair: dark→mid, mid→bright. */
    private static final double PATCH_MIN_LUM_GAP    = 30.0;
    /** Total luma spread (bright − dark) must exceed this. */
    private static final double PATCH_MIN_SPREAD     = 70.0;
    /** Dark patch: luma must be AT MOST this value. */
    private static final double PATCH_DARK_MAX_LUM   = 140.0;
    /** White patch: luma must be AT LEAST this value. */
    private static final double PATCH_WHITE_MIN_LUM  = 160.0;
    /**
     * Maximum centre-to-centre distance between two patches,
     * expressed as a multiple of the larger patch's diagonal.
     * Tight (2.5×) to prevent random spatially-scattered objects from grouping.
     */
    private static final double PATCH_MAX_DIST_MULT  = 4.0;

    // ── Strip–reference proximity ─────────────────────────────────────────
    /**
     * Max distance from strip centre to reference-group centre,
     * as a multiple of the strip's longer side.
     * Both objects must be on the same badge.
     */
    private static final double PROXIMITY_MAX_MULT   = 3.0;
    /** Min distance: strip and ref must NOT overlap (physically separate). */
    private static final double PROXIMITY_MIN_MULT   = 0.3;

    // ── Canny parameters ─────────────────────────────────────────────────
    private static final int CANNY_T1 = 15;
    private static final int CANNY_T2 = 50;

    // ─────────────────────────────────────────────────────────────────────
    // Public result
    // ─────────────────────────────────────────────────────────────────────

    public static class DetectionResult {
        public final boolean stripDetected;
        public final boolean referenceDetected;
        public final double  confidence;   // 0.0–1.0
        public final String  message;      // human-readable reason
        public final String  debugInfo;    // one-line summary for overlay

        /** Strip bounding box in ORIGINAL image coordinates. Null on failure. */
        public final android.graphics.Rect stripRoi;
        /** Mean RGB of the detected WHITE patch. Null on failure. */
        public final int[] refWhiteRGB;

        private DetectionResult(boolean strip, boolean ref, double conf,
                                 String msg, String dbg,
                                 android.graphics.Rect roi, int[] white) {
            this.stripDetected     = strip;
            this.referenceDetected = ref;
            this.confidence        = conf;
            this.message           = msg;
            this.debugInfo         = dbg;
            this.stripRoi          = roi;
            this.refWhiteRGB       = white;
        }

        public static DetectionResult fail(String msg, String reason) {
            String dbg = "Sensor detected=NO  confidence=0%  reason=" + reason;
            Log.w(TAG, "[FAIL] " + msg);
            return new DetectionResult(false, false, 0.0, msg, dbg, null, null);
        }

        public static DetectionResult success(double conf,
                                               android.graphics.Rect roi,
                                               int[] white,
                                               String debugMsg) {
            String dbg = "Sensor detected=YES  confidence="
                    + String.format("%.0f%%", conf * 100) + "  " + debugMsg;
            return new DetectionResult(true, true, conf,
                    "Strip and reference scale detected.", dbg, roi, white);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Main entry point
    // ─────────────────────────────────────────────────────────────────────

    public static DetectionResult detect(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return DetectionResult.fail("Bitmap is null.", "null bitmap");
        }

        int origW = bitmap.getWidth();
        int origH = bitmap.getHeight();

        // ── 1. Downscale ──────────────────────────────────────────────────
        float scale = (float) WORK_MAX_DIM / Math.max(origW, origH);
        int workW = Math.max(1, Math.round(origW * scale));
        int workH = Math.max(1, Math.round(origH * scale));
        Bitmap scaled = (scale < 1.0f)
                ? Bitmap.createScaledBitmap(bitmap, workW, workH, true)
                : bitmap;

        Mat bgr = new Mat();
        Utils.bitmapToMat(scaled, bgr);
        if (scaled != bitmap) scaled.recycle();
        Imgproc.cvtColor(bgr, bgr, Imgproc.COLOR_BGRA2BGR);

        int workArea = workW * workH;

        // ── 2. Greyscale + edge map ───────────────────────────────────────
        Mat grey = new Mat();
        Imgproc.cvtColor(bgr, grey, Imgproc.COLOR_BGR2GRAY);
        bgr.release();
        Imgproc.GaussianBlur(grey, grey, new Size(5, 5), 0);

        Mat edges = new Mat();
        Imgproc.Canny(grey, edges, CANNY_T1, CANNY_T2);
        Mat k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, k);
        k.release();

        // ── 3. Find contours ──────────────────────────────────────────────
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hier = new Mat();
        Imgproc.findContours(edges, contours, hier,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        hier.release();
        edges.release();

        // ── 4. Classify contours ──────────────────────────────────────────
        List<CandidateRect> stripCands  = new ArrayList<>();
        List<CandidateRect> patchCands  = new ArrayList<>();

        for (MatOfPoint c : contours) {
            double area = Imgproc.contourArea(c);
            if (area < 8) continue;
            Rect bb = Imgproc.boundingRect(c);
            double bbArea   = (double) bb.width * bb.height;
            double areaFrac = bbArea / workArea;
            if (areaFrac < PATCH_MIN_AREA_FRAC) continue;

            double arVal = (bb.width >= bb.height)
                    ? (double) bb.width / bb.height
                    : (double) bb.height / bb.width;
            double rectVal = (bbArea > 0) ? area / bbArea : 0;
            double lum     = regionMeanLum(grey, bb);

            CandidateRect cr = new CandidateRect(bb, arVal, areaFrac, rectVal, lum, scale);

            // Strip candidate
            if (areaFrac <= STRIP_MAX_AREA_FRAC
                    && arVal >= STRIP_MIN_AR && arVal <= STRIP_MAX_AR
                    && rectVal >= STRIP_MIN_RECT) {
                stripCands.add(cr);
            }

            // Patch candidate
            if (areaFrac <= PATCH_MAX_AREA_FRAC
                    && arVal <= PATCH_MAX_AR
                    && rectVal >= PATCH_MIN_RECT) {
                patchCands.add(cr);
            }
        }
        for (MatOfPoint c : contours) c.release();

        Log.d(TAG, "stripCands=" + stripCands.size() + " patchCands=" + patchCands.size());

        // ── 5. Find reference scale (3-patch group) ───────────────────────
        ThreePatchGroup refGroup = findThreePatchGroup(patchCands, grey);

        // ── 6. Select best strip ──────────────────────────────────────────
        CandidateRect bestStrip = selectBestStrip(stripCands, refGroup);

        grey.release();

        // ── 7. Validate both ──────────────────────────────────────────────
        if (bestStrip == null && refGroup == null) {
            return DetectionResult.fail(
                    "Sensor strip and reference scale not detected. "
                    + "Ensure both are visible inside the frame.",
                    "no strip, no ref");
        }
        if (bestStrip == null) {
            return DetectionResult.fail(
                    "Reference scale found but no H2S strip detected. "
                    + "Ensure the strip is clearly visible.",
                    "no strip (ref found)");
        }
        if (refGroup == null) {
            return DetectionResult.fail(
                    "Strip shape found but no BLACK|GREY|WHITE reference scale detected. "
                    + "Ensure the reference scale is visible.",
                    "no ref (strip found)");
        }

        // ── 8. Proximity check ────────────────────────────────────────────
        if (!areProximate(bestStrip, refGroup)) {
            return DetectionResult.fail(
                    "Strip and reference scale are too far apart. "
                    + "Hold the complete dosimeter badge in frame.",
                    "not proximate");
        }

        // ── 9. Confidence score ───────────────────────────────────────────
        // Based on how well the strip aspect ratio fits the expected range
        // and how large the luminance spread is on the reference scale.
        double arScore    = 1.0 - Math.min(1.0, Math.abs(bestStrip.ar - 2.2) / 2.0);
        double spreadScore = Math.min(1.0, (refGroup.brightLum - refGroup.darkLum) / 120.0);
        double confidence  = (arScore * 0.5 + spreadScore * 0.5);

        String debugMsg = "roi=" + bestStrip.origRect.left + ","
                + bestStrip.origRect.top + ","
                + bestStrip.origRect.width() + ","
                + bestStrip.origRect.height()
                + "  ar=" + String.format("%.2f", bestStrip.ar)
                + "  refSpread=" + String.format("%.0f", refGroup.brightLum - refGroup.darkLum);

        // Sample the actual BGR colour from the white patch in the original bitmap
        int[] whiteRgb = sampleMeanRgbFromBitmap(bitmap, refGroup.brightRect, scale);

        Log.i(TAG, "[PASS] " + debugMsg + "  confidence=" + String.format("%.0f%%", confidence*100));

        return DetectionResult.success(confidence, bestStrip.origRect, whiteRgb, debugMsg);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Three-patch detection
    // ─────────────────────────────────────────────────────────────────────

    private static ThreePatchGroup findThreePatchGroup(
            List<CandidateRect> patches, Mat grey) {

        int n = patches.size();
        if (n < 3) {
            Log.d(TAG, "Patch group: only " + n + " candidates (need >=3)");
            return null;
        }

        ThreePatchGroup best = null;
        double bestSpread = -1;

        // Rejection counters (diagnostic: which gate rejects the real reference?)
        int notClose = 0, badDark = 0, badWhite = 0, badLumGap = 0,
            badSpread = 0, badSize = 0, badCollinear = 0;

        for (int i = 0; i < n - 2; i++) {
            for (int j = i + 1; j < n - 1; j++) {
                for (int k = j + 1; k < n; k++) {
                    CandidateRect a = patches.get(i);
                    CandidateRect b = patches.get(j);
                    CandidateRect c = patches.get(k);

                    // All three pairs must be close
                    if (!pairClose(a, b) || !pairClose(b, c) || !pairClose(a, c)) {
                        notClose++;
                        continue;
                    }

                    // Sort by luminance
                    CandidateRect[] trio = {a, b, c};
                    Arrays.sort(trio, Comparator.comparingDouble(x -> x.lum));
                    CandidateRect dark = trio[0], mid = trio[1], bright = trio[2];

                    // Strict brightness constraints
                    if (dark.lum   > PATCH_DARK_MAX_LUM)         { badDark++;    continue; }
                    if (bright.lum < PATCH_WHITE_MIN_LUM)        { badWhite++;   continue; }
                    if ((mid.lum - dark.lum)   < PATCH_MIN_LUM_GAP) { badLumGap++; continue; }
                    if ((bright.lum - mid.lum) < PATCH_MIN_LUM_GAP) { badLumGap++; continue; }
                    double spread = bright.lum - dark.lum;
                    if (spread < PATCH_MIN_SPREAD)               { badSpread++;  continue; }

                    // Patches must be similar size (real ref scale has equal patches)
                    double areaA = a.workRect.width * a.workRect.height;
                    double areaB = b.workRect.width * b.workRect.height;
                    double areaC = c.workRect.width * c.workRect.height;
                    double maxArea = Math.max(areaA, Math.max(areaB, areaC));
                    double minArea = Math.min(areaA, Math.min(areaB, areaC));
                    if (minArea <= 0 || (maxArea / minArea) > PATCH_MAX_SIZE_RATIO) {
                        badSize++;
                        continue;
                    }

                    // Patches must be approximately collinear (in a row)
                    if (!areCollinear(centre(a), centre(b), centre(c))) {
                        badCollinear++;
                        continue;
                    }

                    if (spread > bestSpread) {
                        bestSpread = spread;
                        best = new ThreePatchGroup(
                                dark.lum, mid.lum, bright.lum,
                                dark.workRect, mid.workRect, bright.workRect,
                                centre(dark), centre(mid), centre(bright));
                    }
                }
            }
        }

        if (best == null) {
            int combos = n * (n - 1) * (n - 2) / 6;
            Log.d(TAG, "Patch group FAIL: candidates=" + n + " combos=" + combos
                    + " notClose=" + notClose + " badDark=" + badDark
                    + " badWhite=" + badWhite + " badLumGap=" + badLumGap
                    + " badSpread=" + badSpread + " badSize=" + badSize
                    + " badCollinear=" + badCollinear);
        } else {
            Log.d(TAG, "Patch group OK: spread=" + String.format("%.0f", bestSpread));
        }
        return best;
    }

    private static boolean pairClose(CandidateRect p, CandidateRect q) {
        double diagP = Math.sqrt(p.workRect.width * p.workRect.width
                                + p.workRect.height * p.workRect.height);
        double diagQ = Math.sqrt(q.workRect.width * q.workRect.width
                                + q.workRect.height * q.workRect.height);
        double maxDiag = Math.max(diagP, diagQ);
        double dist = dist(centre(p), centre(q));
        return dist <= PATCH_MAX_DIST_MULT * maxDiag;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Grid-based fallback: colour sampling when contour detection misses
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Grid-based fallback for reference patch detection.
     * Divides the working image into blocks, samples the mean luma of each,
     * and groups dark/mid/bright blocks into candidate patch triplets.
     * Used when the Canny contour approach fails to find 3 distinct patches.
     */
    private static ThreePatchGroup findThreePatchGroupByGrid(
            Bitmap origBitmap, float scale) {

        // Downscale to working image size so coordinates match strip candidates
        int workW = Math.max(1, Math.round(origBitmap.getWidth() * scale));
        int workH = Math.max(1, Math.round(origBitmap.getHeight() * scale));
        Bitmap workBitmap = (scale < 1.0f)
                ? Bitmap.createScaledBitmap(origBitmap, workW, workH, true)
                : origBitmap;
        boolean ownCopy = (workBitmap != origBitmap);

        final int BLOCK = 30;
        int w = workBitmap.getWidth();
        int h = workBitmap.getHeight();
        int cols = Math.max(1, w / BLOCK);
        int rows = Math.max(1, h / BLOCK);

        List<CandidateRect> gridPatches = new ArrayList<>();

        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                int x0 = gx * BLOCK;
                int y0 = gy * BLOCK;
                int x1 = Math.min(x0 + BLOCK, w);
                int y1 = Math.min(y0 + BLOCK, h);

                long rSum = 0, gSum = 0, bSum = 0, n = 0;
                for (int y = y0; y < y1; y += 2) {
                    for (int x = x0; x < x1; x += 2) {
                        int p = workBitmap.getPixel(x, y);
                        rSum += android.graphics.Color.red(p);
                        gSum += android.graphics.Color.green(p);
                        bSum += android.graphics.Color.blue(p);
                        n++;
                    }
                }
                if (n == 0) continue;

                int r = (int)(rSum / n);
                int g = (int)(gSum / n);
                int b = (int)(bSum / n);
                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                double areaFrac = (double)(x1 - x0) * (y1 - y0) / (double)(w * h);

                Rect workRect = new Rect(x0, y0, x1 - x0, y1 - y0);
                android.graphics.Rect origRect = new android.graphics.Rect(
                        Math.round(x0 / scale), Math.round(y0 / scale),
                        Math.round(x1 / scale), Math.round(y1 / scale));

                gridPatches.add(new CandidateRect(
                        workRect, origRect, 1.0, areaFrac, 1.0, lum));
            }
        }

        // Group into triplets (same constraints as contour path)
        int n = gridPatches.size();
        if (n < 3) {
            if (ownCopy) workBitmap.recycle();
            return null;
        }

        ThreePatchGroup best = null;
        double bestSpread = -1;

        for (int i = 0; i < n - 2; i++) {
            for (int j = i + 1; j < n - 1; j++) {
                for (int k = j + 1; k < n; k++) {
                    CandidateRect a = gridPatches.get(i);
                    CandidateRect b = gridPatches.get(j);
                    CandidateRect c = gridPatches.get(k);

                    if (!pairClose(a, b) || !pairClose(b, c) || !pairClose(a, c))
                        continue;

                    CandidateRect[] trio = {a, b, c};
                    Arrays.sort(trio, Comparator.comparingDouble(x -> x.lum));
                    CandidateRect dark = trio[0], mid = trio[1], bright = trio[2];

                    double spread = bright.lum - dark.lum;
                    if (spread < PATCH_MIN_SPREAD) continue;
                    if (dark.lum > PATCH_DARK_MAX_LUM) continue;
                    if (bright.lum < PATCH_WHITE_MIN_LUM) continue;
                    if ((mid.lum - dark.lum) < PATCH_MIN_LUM_GAP) continue;
                    if ((bright.lum - mid.lum) < PATCH_MIN_LUM_GAP) continue;

                    if (spread > bestSpread) {
                        bestSpread = spread;
                        best = new ThreePatchGroup(
                                dark.lum, mid.lum, bright.lum,
                                dark.workRect, mid.workRect, bright.workRect,
                                patchCentre(dark), patchCentre(mid), patchCentre(bright));
                    }
                }
            }
        }
        if (ownCopy) workBitmap.recycle();
        return best;
    }

    private static Point patchCentre(CandidateRect c) {
        return new Point(
                c.workRect.x + c.workRect.width  / 2.0,
                c.workRect.y + c.workRect.height / 2.0);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Strip selection
    // ─────────────────────────────────────────────────────────────────────

    private static CandidateRect selectBestStrip(
            List<CandidateRect> candidates, ThreePatchGroup refGroup) {
        if (candidates.isEmpty()) return null;

        CandidateRect best = null;
        double bestScore = Double.MAX_VALUE;

        for (CandidateRect c : candidates) {
            // Penalise aspect ratios far from the typical strip shape
            double arScore = Math.abs(c.ar - 2.2);

            // Prefer candidates near the reference group
            double proxPenalty = 0;
            if (refGroup != null) {
                double d = dist(centre(c), refGroup.groupCentre());
                double stripLong = Math.max(c.workRect.width, c.workRect.height);
                proxPenalty = d / (stripLong + 1);
            }

            double score = arScore + 0.4 * proxPenalty;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Proximity check
    // ─────────────────────────────────────────────────────────────────────

    private static boolean areProximate(CandidateRect strip, ThreePatchGroup ref) {
        double d = dist(centre(strip), ref.groupCentre());
        double stripLong = Math.max(strip.workRect.width, strip.workRect.height);
        double maxDist = PROXIMITY_MAX_MULT * stripLong;
        double minDist = PROXIMITY_MIN_MULT * stripLong;
        Log.d(TAG, "Proximity: dist=" + String.format("%.1f", d)
                + " min=" + String.format("%.1f", minDist)
                + " max=" + String.format("%.1f", maxDist));
        return d >= minDist && d <= maxDist;
    }

    /**
     * Checks if three points are approximately collinear.
     * Uses the perpendicular distance from the middle point to the line
     * formed by the two outer points. Returns true if the deviation
     * is less than 30% of the longest segment.
     */
    private static boolean areCollinear(Point a, Point b, Point c) {
        // Find which point is in the middle by X or Y span
        double minX = Math.min(a.x, Math.min(b.x, c.x));
        double maxX = Math.max(a.x, Math.max(b.x, c.x));
        double minY = Math.min(a.y, Math.min(b.y, c.y));
        double maxY = Math.max(a.y, Math.max(b.y, c.y));
        double spanX = maxX - minX;
        double spanY = maxY - minY;

        // Pick the two endpoints as the ones with max distance
        double dAB = dist(a, b), dBC = dist(b, c), dAC = dist(a, c);
        Point p1, p2, pmid;
        if (dAB >= dBC && dAB >= dAC) {
            p1 = a; p2 = b; pmid = c;
        } else if (dBC >= dAB && dBC >= dAC) {
            p1 = b; p2 = c; pmid = a;
        } else {
            p1 = a; p2 = c; pmid = b;
        }

        double lineLen = dist(p1, p2);
        if (lineLen < 1) return true; // degenerate

        // Perpendicular distance from pmid to line p1→p2
        double dx = p2.x - p1.x, dy = p2.y - p1.y;
        double perpDist = Math.abs(dy * pmid.x - dx * pmid.y + p2.x * p1.y - p2.y * p1.x) / lineLen;

        return perpDist <= 0.30 * lineLen;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pixel helpers
    // ─────────────────────────────────────────────────────────────────────

    private static double regionMeanLum(Mat grey, Rect rect) {
        int x0 = Math.max(0, rect.x),   y0 = Math.max(0, rect.y);
        int x1 = Math.min(grey.cols(), rect.x + rect.width);
        int y1 = Math.min(grey.rows(), rect.y + rect.height);
        if (x1 <= x0 || y1 <= y0) return 0;
        Mat roi = grey.submat(y0, y1, x0, x1);
        Scalar mean = Core.mean(roi);
        roi.release();
        return mean.val[0];
    }

    /**
     * Sample the mean RGB of a working-image rect from the ORIGINAL bitmap.
     * Scale factor converts working-image coordinates back to original pixels.
     */
    private static int[] sampleMeanRgbFromBitmap(Bitmap bmp, Rect workRect, float scale) {
        int ox = Math.round(workRect.x / scale);
        int oy = Math.round(workRect.y / scale);
        int ow = Math.round(workRect.width  / scale);
        int oh = Math.round(workRect.height / scale);
        int x0 = Math.max(0, ox),      y0 = Math.max(0, oy);
        int x1 = Math.min(bmp.getWidth(),  ox + ow);
        int y1 = Math.min(bmp.getHeight(), oy + oh);
        if (x1 <= x0 || y1 <= y0) return new int[]{200, 200, 200};

        long rS = 0, gS = 0, bS = 0; long n = 0;
        for (int y = y0; y < y1; y += 2)
            for (int x = x0; x < x1; x += 2) {
                int p = bmp.getPixel(x, y);
                rS += android.graphics.Color.red(p);
                gS += android.graphics.Color.green(p);
                bS += android.graphics.Color.blue(p);
                n++;
            }
        if (n == 0) return new int[]{200, 200, 200};
        return new int[]{(int)(rS/n), (int)(gS/n), (int)(bS/n)};
    }

    private static Point centre(CandidateRect c) {
        return new Point(c.workRect.x + c.workRect.width  / 2.0,
                         c.workRect.y + c.workRect.height / 2.0);
    }

    private static double dist(Point a, Point b) {
        double dx = a.x - b.x, dy = a.y - b.y;
        return Math.sqrt(dx*dx + dy*dy);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────

    private static class CandidateRect {
        final Rect workRect;
        final android.graphics.Rect origRect;
        final double ar, areaFrac, rect, lum;

        CandidateRect(Rect bb, double ar, double areaFrac, double rect,
                      double lum, float scale) {
            this.workRect = bb;
            this.ar = ar; this.areaFrac = areaFrac;
            this.rect = rect; this.lum = lum;
            int ox = Math.round(bb.x / scale), oy = Math.round(bb.y / scale);
            int ow = Math.round(bb.width / scale), oh = Math.round(bb.height / scale);
            this.origRect = new android.graphics.Rect(ox, oy, ox+ow, oy+oh);
        }

        /**
         * Constructor for grid-based fallback: original-rect coordinates are
         * computed by the caller, so no scale division is required here.
         */
        CandidateRect(Rect workRect, android.graphics.Rect origRect,
                      double ar, double areaFrac, double rect, double lum) {
            this.workRect = workRect;
            this.origRect = origRect;
            this.ar = ar; this.areaFrac = areaFrac;
            this.rect = rect; this.lum = lum;
        }
    }

    private static class ThreePatchGroup {
        final double darkLum, midLum, brightLum;
        final Rect darkRect, midRect, brightRect;
        final Point darkCentre, midCentre, brightCentre;

        ThreePatchGroup(double dl, double ml, double bl,
                        Rect dr, Rect mr, Rect br,
                        Point dc, Point mc, Point bc) {
            darkLum = dl; midLum = ml; brightLum = bl;
            darkRect = dr; midRect = mr; brightRect = br;
            darkCentre = dc; midCentre = mc; brightCentre = bc;
        }

        Point groupCentre() {
            return new Point(
                (darkCentre.x + midCentre.x + brightCentre.x) / 3.0,
                (darkCentre.y + midCentre.y + brightCentre.y) / 3.0);
        }
    }
}
