package com.ankita.h2sdosimeter.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * MarkedBadgeDetector — locates the H2S sensor strip and its reference card
 * on the marked badge design.
 *
 * BADGE DESIGN
 *   - Four red L-shaped corner marks frame the sensor strip.
 *   - A reference card with three patches (WHITE, LIGHT, DARK) lies against
 *     one short end of the strip, inside the strip's width.
 *
 * PIPELINE
 *   1. Red mask (strong, saturated red only) → connected blobs.
 *   2. Choose the 4 blobs that form a consistent rectangle; the outer corner
 *      of each L is a strip corner.
 *   3. Perspective mapping: unit square (u across, v along the strip) → image.
 *      Rotation, tilt and distance no longer matter.
 *   4. Strip colour = trimmed mean of the inner strip area (edges, red ink
 *      and torn borders are excluded). Glare and out-of-frame are rejected.
 *   5. Reference card: searched beyond BOTH short ends (badge may be upside
 *      down). The bright block (white + light patch) is found and split in
 *      half; the dark patch is the block-sized area next to it. Patches are
 *      identified by brightness, not position, then validated.
 *
 * Nothing is guessed: if any step fails the result is not detected and the
 * message tells the user what to fix.
 *
 * Pure Java (ARGB int[] in) so the full detector runs in JVM unit tests.
 */
public final class MarkedBadgeDetector {

    // ── Red corner marks ────────────────────────────────────────────────
    private static final int    RED_MIN_VALUE        = 90;
    private static final double RED_MIN_SATURATION   = 0.45;
    /** Minimum R − max(G, B): separates printed red from brown / skin / wood. */
    private static final int    RED_MIN_DOMINANCE    = 60;
    /** Hue window around 0° (negative = towards magenta, positive = towards orange). */
    private static final double RED_HUE_MIN          = -25.0;
    private static final double RED_HUE_MAX          = 12.0;

    private static final int    MARK_MIN_PIXELS          = 12;
    private static final double MARK_MIN_AREA_FRACTION   = 0.00005;
    private static final double MARK_MAX_AREA_FRACTION   = 0.02;
    private static final double MARK_MAX_BOX_ASPECT      = 3.0;
    private static final double MARK_MIN_FILL            = 0.20;
    /** L-shaped marks do not fill their bounding box; solid red objects do. */
    private static final double MARK_MAX_FILL            = 0.88;
    private static final int    MAX_MARK_CANDIDATES      = 12;
    private static final double MARK_MAX_SIZE_RATIO      = 3.5;
    private static final double MAX_OPPOSITE_SIDE_RATIO  = 1.6;
    private static final double MAX_DIAGONAL_RATIO       = 1.5;
    private static final double MARK_MAX_SIZE_VS_SIDE    = 0.7;

    // ── Strip ───────────────────────────────────────────────────────────
    private static final double STRIP_MIN_ASPECT      = 1.3;
    private static final double STRIP_MAX_ASPECT      = 3.5;
    private static final double STRIP_INSET_U         = 0.22;
    private static final double STRIP_INSET_V         = 0.15;
    private static final int    STRIP_SAMPLES_U       = 24;
    private static final int    STRIP_SAMPLES_V       = 48;
    private static final double MIN_INSIDE_FRACTION   = 0.95;
    private static final int    GLARE_LEVEL           = 250;
    private static final double MAX_GLARE_FRACTION    = 0.05;

    // ── Reference card search (u in strip widths, t in strip lengths) ───
    private static final double CARD_U_MIN            = -0.2;
    private static final double CARD_U_MAX            = 1.2;
    private static final double CARD_T_MAX            = 1.2;
    private static final int    CARD_GRID_U           = 56;
    private static final int    CARD_GRID_T           = 72;
    private static final double BRIGHT_RELATIVE       = 0.82;
    private static final double BLOCK_MIN_WIDTH       = 0.12;
    private static final double BLOCK_MAX_WIDTH       = 0.90;
    private static final double BLOCK_MIN_LENGTH      = 0.15;
    private static final double BLOCK_MAX_LENGTH      = 1.10;
    /** The block may contain the dark patch as a hole (card border around it). */
    private static final double BLOCK_MIN_FILL        = 0.40;
    private static final int    PATCH_SAMPLES         = 12;
    private static final double DARK_RELATIVE         = 0.62;
    private static final int    MAX_ROW_GAP           = 3;
    private static final double DARK_RUN_MIN_LENGTH   = 0.04;
    private static final double BRIGHT_RUN_MIN_LENGTH = 0.08;
    private static final double BRIGHT_TO_DARK_MIN    = 1.2;
    private static final double BRIGHT_TO_DARK_MAX    = 4.0;

    private static final double DARK_MAX_RELATIVE     = 0.75;
    private static final double MIN_WHITE_DARK_LUMA   = 30.0;
    private static final double LIGHT_MIN_POSITION    = 0.40;
    private static final double MAX_WHITE_VARIATION  = 0.12;
    private static final double MAX_PATCH_VARIATION   = 0.20;

    private MarkedBadgeDetector() {}

    // ─────────────────────────────────────────────────────────────────────
    // Result
    // ─────────────────────────────────────────────────────────────────────

    public static final class Result {
        public final boolean detected;
        /** Machine-readable failure code, null on success. */
        public final String  failureCode;
        /** User-facing message. */
        public final String  message;
        /** One-line debug summary. */
        public final String  debug;

        /** Strip corners in image pixels: x0,y0 … x3,y3 (clockwise, 0→1 is a short side). */
        public final double[] stripCorners;
        public final double   stripAspectRatio;
        /** Captured strip colour {R,G,B} (trimmed mean). */
        public final double[] stripRgb;
        /** Luma standard deviation / mean over the strip — blotchiness. */
        public final double   stripVariation;
        public final double[] whiteRgb;
        public final double[] lightRgb;
        public final double[] darkRgb;

        private Result(boolean detected, String failureCode, String message, String debug,
                       double[] stripCorners, double stripAspectRatio,
                       double[] stripRgb, double stripVariation,
                       double[] whiteRgb, double[] lightRgb, double[] darkRgb) {
            this.detected         = detected;
            this.failureCode      = failureCode;
            this.message          = message;
            this.debug            = debug;
            this.stripCorners     = stripCorners;
            this.stripAspectRatio = stripAspectRatio;
            this.stripRgb         = stripRgb;
            this.stripVariation   = stripVariation;
            this.whiteRgb         = whiteRgb;
            this.lightRgb         = lightRgb;
            this.darkRgb          = darkRgb;
        }

        static Result fail(String code, String message, String debug) {
            return new Result(false, code, message, "FAIL " + code + " " + debug,
                    null, 0, null, 0, null, null, null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param argb   pixels, row-major, 0xAARRGGBB (as Bitmap.getPixels / BufferedImage.getRGB)
     * @param width  image width
     * @param height image height
     */
    public static Result detect(int[] argb, int width, int height) {
        if (argb == null || width < 32 || height < 32 || argb.length < width * height) {
            return Result.fail("BAD_IMAGE", "Image is empty or too small.", "");
        }
        Image img = new Image(argb, width, height);

        // 1. Red marks
        int[] labels = new int[width * height];
        List<Blob> marks = findMarkBlobs(img, labels);
        if (marks.size() < 4) {
            return Result.fail("MARKS_NOT_FOUND",
                    "Found " + marks.size() + " of 4 red corner marks. Keep all four red "
                    + "corners around the strip inside the frame.",
                    "marks=" + marks.size());
        }

        // 2. Rectangle of 4 marks
        Blob[] quadMarks = selectRectangle(marks);
        if (quadMarks == null) {
            return Result.fail("MARKS_NOT_RECTANGLE",
                    "The red corner marks do not form a rectangle. Hold the camera "
                    + "straight above the badge.",
                    "marks=" + marks.size());
        }
        double[] corners = stripCorners(quadMarks, labels, width);
        double shortSides = dist(corners, 0, 1) + dist(corners, 2, 3);
        double longSides  = dist(corners, 1, 2) + dist(corners, 3, 0);
        double aspect = longSides / Math.max(1e-6, shortSides);
        if (aspect < STRIP_MIN_ASPECT || aspect > STRIP_MAX_ASPECT) {
            return Result.fail("STRIP_SHAPE",
                    "The area inside the red marks is not strip-shaped. Make sure the marks "
                    + "belong to the sensor strip.",
                    String.format(Locale.US, "aspect=%.2f", aspect));
        }
        Homography map = Homography.unitSquareTo(corners);
        if (map == null) {
            return Result.fail("MARKS_NOT_RECTANGLE",
                    "The red corner marks do not form a rectangle.", "degenerate");
        }

        // 3. Strip colour
        Samples strip = sample(img, map,
                STRIP_INSET_U, 1 - STRIP_INSET_U, STRIP_INSET_V, 1 - STRIP_INSET_V,
                STRIP_SAMPLES_U, STRIP_SAMPLES_V);
        if (strip.insideFraction() < MIN_INSIDE_FRACTION || strip.count() == 0) {
            return Result.fail("STRIP_OUT_OF_FRAME",
                    "Part of the strip is outside the photo. Move back slightly.", "");
        }
        if (strip.glareFraction() > MAX_GLARE_FRACTION) {
            return Result.fail("GLARE_ON_STRIP",
                    "Glare / reflection on the strip. Tilt the badge or avoid direct light.",
                    String.format(Locale.US, "glare=%.0f%%", strip.glareFraction() * 100));
        }

        // 4. Reference card
        Card best = null;
        String cardFailure = "no bright card block beside the strip";
        for (boolean beyondEnd : new boolean[]{false, true}) {
            CardSearch search = findCard(img, map, beyondEnd);
            if (search.card != null && (best == null || search.card.contrast > best.contrast)) {
                best = search.card;
            } else if (search.card == null && search.failure != null) {
                cardFailure = search.failure;
            }
        }
        if (best == null) {
            return Result.fail("REFERENCE_NOT_FOUND",
                    "Reference colour card (white / light / dark) not found next to the strip. "
                    + "Keep the whole card in the frame and avoid glare on it.",
                    cardFailure);
        }

        double[] stripRgb = strip.trimmedMean();
        String debug = String.format(Locale.US,
                "OK aspect=%.2f strip=%s var=%.2f white=%s light=%s dark=%s corners=%s",
                aspect, fmt(stripRgb), strip.lumaVariation(),
                fmt(best.white), fmt(best.light), fmt(best.dark), fmt(corners));
        return new Result(true, null, "Strip and reference card detected.", debug,
                corners, aspect, stripRgb, strip.lumaVariation(),
                best.white, best.light, best.dark);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Red marks
    // ─────────────────────────────────────────────────────────────────────

    static boolean isRedMark(int p) {
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max != r || max < RED_MIN_VALUE) return false;
        int chroma = max - min;
        if (chroma < RED_MIN_SATURATION * max) return false;
        if (r - Math.max(g, b) < RED_MIN_DOMINANCE) return false;
        double hue = 60.0 * (g - b) / chroma;
        return hue >= RED_HUE_MIN && hue <= RED_HUE_MAX;
    }

    private static List<Blob> findMarkBlobs(Image img, int[] labels) {
        int w = img.w, h = img.h, n = w * h;
        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) mask[i] = isRedMark(img.px[i]);

        double minPixels = Math.max(MARK_MIN_PIXELS, MARK_MIN_AREA_FRACTION * n);
        double maxPixels = MARK_MAX_AREA_FRACTION * n;

        List<Blob> kept = new ArrayList<>();
        int[] stack = new int[n];
        int nextId = 0;
        for (int start = 0; start < n; start++) {
            if (!mask[start] || labels[start] != 0) continue;
            Blob blob = new Blob(++nextId);
            int sp = 0;
            stack[sp++] = start;
            labels[start] = blob.id;
            while (sp > 0) {
                int c = stack[--sp];
                int x = c % w, y = c / w;
                blob.add(x, y);
                if (x > 0     && mask[c - 1] && labels[c - 1] == 0) { labels[c - 1] = blob.id; stack[sp++] = c - 1; }
                if (x < w - 1 && mask[c + 1] && labels[c + 1] == 0) { labels[c + 1] = blob.id; stack[sp++] = c + 1; }
                if (y > 0     && mask[c - w] && labels[c - w] == 0) { labels[c - w] = blob.id; stack[sp++] = c - w; }
                if (y < h - 1 && mask[c + w] && labels[c + w] == 0) { labels[c + w] = blob.id; stack[sp++] = c + w; }
            }
            if (blob.count < minPixels || blob.count > maxPixels) continue;
            double bw = blob.boxWidth(), bh = blob.boxHeight();
            double boxAspect = Math.max(bw, bh) / Math.min(bw, bh);
            double fill = blob.count / (bw * bh);
            if (boxAspect > MARK_MAX_BOX_ASPECT || fill < MARK_MIN_FILL || fill > MARK_MAX_FILL) continue;
            kept.add(blob);
        }
        kept.sort((a, b) -> Integer.compare(b.count, a.count));
        return kept.size() > MAX_MARK_CANDIDATES ? kept.subList(0, MAX_MARK_CANDIDATES) : kept;
    }

    /** Best set of 4 marks forming a convex, roughly rectangular quadrilateral. */
    private static Blob[] selectRectangle(List<Blob> marks) {
        int n = marks.size();
        Blob[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                for (int k = j + 1; k < n; k++)
                    for (int l = k + 1; l < n; l++) {
                        Blob[] q = orderByAngle(new Blob[]{marks.get(i), marks.get(j), marks.get(k), marks.get(l)});
                        double score = rectangleScore(q);
                        if (score < bestScore) {
                            bestScore = score;
                            best = q;
                        }
                    }
        return best;
    }

    private static double rectangleScore(Blob[] q) {
        int minCount = Integer.MAX_VALUE, maxCount = 0;
        double maxMarkSize = 0;
        double[] pts = new double[8];
        for (int i = 0; i < 4; i++) {
            minCount = Math.min(minCount, q[i].count);
            maxCount = Math.max(maxCount, q[i].count);
            maxMarkSize = Math.max(maxMarkSize, Math.max(q[i].boxWidth(), q[i].boxHeight()));
            pts[2 * i] = q[i].cx();
            pts[2 * i + 1] = q[i].cy();
        }
        double sizeRatio = (double) maxCount / minCount;
        if (sizeRatio > MARK_MAX_SIZE_RATIO) return Double.MAX_VALUE;

        double s0 = dist(pts, 0, 1), s1 = dist(pts, 1, 2), s2 = dist(pts, 2, 3), s3 = dist(pts, 3, 0);
        double minSide = Math.min(Math.min(s0, s1), Math.min(s2, s3));
        if (minSide < 1) return Double.MAX_VALUE;
        double opp1 = Math.max(s0, s2) / Math.min(s0, s2);
        double opp2 = Math.max(s1, s3) / Math.min(s1, s3);
        if (opp1 > MAX_OPPOSITE_SIDE_RATIO || opp2 > MAX_OPPOSITE_SIDE_RATIO) return Double.MAX_VALUE;
        double d1 = dist(pts, 0, 2), d2 = dist(pts, 1, 3);
        double diagRatio = Math.max(d1, d2) / Math.max(1e-6, Math.min(d1, d2));
        if (diagRatio > MAX_DIAGONAL_RATIO) return Double.MAX_VALUE;
        if (maxMarkSize > MARK_MAX_SIZE_VS_SIDE * minSide) return Double.MAX_VALUE;
        if (!isConvex(pts)) return Double.MAX_VALUE;

        return Math.log(sizeRatio) + (opp1 - 1) + (opp2 - 1) + (diagRatio - 1);
    }

    private static Blob[] orderByAngle(Blob[] q) {
        double cx = 0, cy = 0;
        for (Blob b : q) { cx += b.cx(); cy += b.cy(); }
        final double mx = cx / 4, my = cy / 4;
        Blob[] sorted = q.clone();
        Arrays.sort(sorted, (a, b) -> Double.compare(
                Math.atan2(a.cy() - my, a.cx() - mx), Math.atan2(b.cy() - my, b.cx() - mx)));
        return sorted;
    }

    private static boolean isConvex(double[] pts) {
        int sign = 0;
        for (int i = 0; i < 4; i++) {
            int a = i, b = (i + 1) % 4, c = (i + 2) % 4;
            double cross = (pts[2 * b] - pts[2 * a]) * (pts[2 * c + 1] - pts[2 * b + 1])
                         - (pts[2 * b + 1] - pts[2 * a + 1]) * (pts[2 * c] - pts[2 * b]);
            int s = cross > 0 ? 1 : (cross < 0 ? -1 : 0);
            if (s == 0) return false;
            if (sign == 0) sign = s;
            else if (s != sign) return false;
        }
        return true;
    }

    /**
     * Outer corner of each L mark (its pixel farthest from the rectangle centre),
     * ordered clockwise so that corner 0 → corner 1 is a SHORT side of the strip.
     */
    private static double[] stripCorners(Blob[] quad, int[] labels, int w) {
        double mx = 0, my = 0;
        for (Blob b : quad) { mx += b.cx(); my += b.cy(); }
        mx /= 4; my /= 4;

        double[] c = new double[8];
        for (int i = 0; i < 4; i++) {
            Blob b = quad[i];
            double bestD = -1;
            for (int y = b.minY; y <= b.maxY; y++) {
                for (int x = b.minX; x <= b.maxX; x++) {
                    if (labels[y * w + x] != b.id) continue;
                    double d = (x - mx) * (x - mx) + (y - my) * (y - my);
                    if (d > bestD) {
                        bestD = d;
                        c[2 * i] = x + 0.5;
                        c[2 * i + 1] = y + 0.5;
                    }
                }
            }
        }
        // Rotate so side 0→1 is short; of the two valid starts pick the upper-left one.
        int start = dist(c, 0, 1) + dist(c, 2, 3) <= dist(c, 1, 2) + dist(c, 3, 0) ? 0 : 1;
        int alt = start + 2;
        if (c[2 * alt] + c[2 * alt + 1] < c[2 * start] + c[2 * start + 1]) start = alt;
        double[] out = new double[8];
        for (int i = 0; i < 4; i++) {
            int src = (start + i) % 4;
            out[2 * i] = c[2 * src];
            out[2 * i + 1] = c[2 * src + 1];
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reference card
    // ─────────────────────────────────────────────────────────────────────

    private static final class Card {
        final double[] white, light, dark;
        final double contrast;
        Card(double[] white, double[] light, double[] dark) {
            this.white = white; this.light = light; this.dark = dark;
            this.contrast = luma(white) - luma(dark);
        }
    }

    private static final class CardSearch {
        final Card card; final String failure;
        CardSearch(Card card, String failure) { this.card = card; this.failure = failure; }
    }

    /**
     * Searches beyond one short end of the strip.
     * @param beyondEnd false = beyond side 0→1 (v &lt; 0), true = beyond side 2→3 (v &gt; 1)
     */
    private static CardSearch findCard(Image img, Homography map, boolean beyondEnd) {
        double stepU = (CARD_U_MAX - CARD_U_MIN) / CARD_GRID_U;
        double stepT = CARD_T_MAX / CARD_GRID_T;
        double[] luma = new double[CARD_GRID_U * CARD_GRID_T];
        boolean[] valid = new boolean[luma.length];
        double[] rgb = new double[3];
        double[] xy = new double[2];
        List<Double> validLuma = new ArrayList<>();

        for (int gt = 0; gt < CARD_GRID_T; gt++) {
            double t = (gt + 0.5) * stepT;
            double v = beyondEnd ? 1 + t : -t;
            for (int gu = 0; gu < CARD_GRID_U; gu++) {
                double u = CARD_U_MIN + (gu + 0.5) * stepU;
                int idx = gt * CARD_GRID_U + gu;
                if (!map.apply(u, v, xy) || !img.sampleBilinear(xy[0], xy[1], rgb)) continue;
                if (isRedMark(packRgb(rgb))) continue;
                valid[idx] = true;
                luma[idx] = luma(rgb);
                validLuma.add(luma[idx]);
            }
        }
        if (validLuma.size() < luma.length / 4) {
            return new CardSearch(null, "end outside image");
        }
        double[] sortedLuma = new double[validLuma.size()];
        for (int i = 0; i < sortedLuma.length; i++) sortedLuma[i] = validLuma.get(i);
        Arrays.sort(sortedLuma);
        double p95 = sortedLuma[(int) (0.95 * (sortedLuma.length - 1))];
        double brightThreshold = BRIGHT_RELATIVE * p95;
        double darkThreshold = DARK_RELATIVE * p95;

        boolean[] bright = new boolean[luma.length];
        for (int i = 0; i < luma.length; i++) bright[i] = valid[i] && luma[i] >= brightThreshold;

        Card best = null;
        String failure = "no bright block";
        int[] seen = new int[luma.length];
        int[] stack = new int[luma.length];
        int id = 0;
        for (int startIdx = 0; startIdx < luma.length; startIdx++) {
            if (!bright[startIdx] || seen[startIdx] != 0) continue;
            id++;
            int sp = 0, count = 0;
            int uMin = CARD_GRID_U, uMax = -1, tMin = CARD_GRID_T, tMax = -1;
            stack[sp++] = startIdx;
            seen[startIdx] = id;
            while (sp > 0) {
                int c = stack[--sp];
                int gu = c % CARD_GRID_U, gt = c / CARD_GRID_U;
                count++;
                uMin = Math.min(uMin, gu); uMax = Math.max(uMax, gu);
                tMin = Math.min(tMin, gt); tMax = Math.max(tMax, gt);
                if (gu > 0 && bright[c - 1] && seen[c - 1] == 0) { seen[c - 1] = id; stack[sp++] = c - 1; }
                if (gu < CARD_GRID_U - 1 && bright[c + 1] && seen[c + 1] == 0) { seen[c + 1] = id; stack[sp++] = c + 1; }
                if (gt > 0 && bright[c - CARD_GRID_U] && seen[c - CARD_GRID_U] == 0) { seen[c - CARD_GRID_U] = id; stack[sp++] = c - CARD_GRID_U; }
                if (gt < CARD_GRID_T - 1 && bright[c + CARD_GRID_U] && seen[c + CARD_GRID_U] == 0) { seen[c + CARD_GRID_U] = id; stack[sp++] = c + CARD_GRID_U; }
            }

            int cellsU = uMax - uMin + 1, cellsT = tMax - tMin + 1;
            double widthU = cellsU * stepU, lengthT = cellsT * stepT;
            double fill = (double) count / (cellsU * cellsT);
            double blockU0 = CARD_U_MIN + uMin * stepU, blockU1 = blockU0 + widthU;
            double centreU = (blockU0 + blockU1) / 2;
            if (widthU < BLOCK_MIN_WIDTH || widthU > BLOCK_MAX_WIDTH
                    || lengthT < BLOCK_MIN_LENGTH || lengthT > BLOCK_MAX_LENGTH
                    || fill < BLOCK_MIN_FILL || centreU < 0 || centreU > 1) {
                continue;
            }

            // Brightness profile along the card, through the middle of the block.
            int cu0 = uMin + (int) Math.round(0.3 * cellsU);
            int cu1 = Math.max(cu0, uMax - (int) Math.round(0.3 * cellsU));
            CardSearch attempt = findPatchesInProfile(img, map, beyondEnd, luma, valid,
                    cu0, cu1, brightThreshold, darkThreshold, blockU0, blockU1);
            if (attempt.card != null) {
                if (best == null || attempt.card.contrast > best.contrast) best = attempt.card;
            } else {
                failure = attempt.failure;
            }
        }
        return new CardSearch(best, best == null ? failure : null);
    }

    /**
     * Classifies each row of the card profile as bright / dark / other, then looks
     * for a DARK run directly next to a BRIGHT run about twice as long
     * (bright run = white + light patch, dark run = dark patch).
     */
    private static CardSearch findPatchesInProfile(Image img, Homography map, boolean beyondEnd,
                                                   double[] luma, boolean[] valid, int cu0, int cu1,
                                                   double brightThreshold, double darkThreshold,
                                                   double blockU0, double blockU1) {
        double stepT = CARD_T_MAX / CARD_GRID_T;
        int[] cls = new int[CARD_GRID_T];
        double[] row = new double[cu1 - cu0 + 1];
        for (int gt = 0; gt < CARD_GRID_T; gt++) {
            int n = 0;
            for (int gu = cu0; gu <= cu1; gu++) {
                int idx = gt * CARD_GRID_U + gu;
                if (valid[idx]) row[n++] = luma[idx];
            }
            if (n * 2 < row.length) continue;
            Arrays.sort(row, 0, n);
            double median = row[n / 2];
            cls[gt] = median >= brightThreshold ? 1 : (median <= darkThreshold ? -1 : 0);
        }
        // Remove single-row interruptions (thin printed borders between patches).
        int[] smooth = cls.clone();
        for (int gt = 1; gt < CARD_GRID_T - 1; gt++) {
            if (cls[gt - 1] != 0 && cls[gt - 1] == cls[gt + 1] && cls[gt] != cls[gt - 1]) {
                smooth[gt] = cls[gt - 1];
            }
        }
        List<int[]> runs = new ArrayList<>();   // {class, firstRow, lastRow}
        for (int gt = 0; gt < CARD_GRID_T; gt++) {
            if (smooth[gt] == 0) continue;
            if (!runs.isEmpty()) {
                int[] last = runs.get(runs.size() - 1);
                if (last[0] == smooth[gt] && last[2] == gt - 1) {
                    last[2] = gt;
                    continue;
                }
            }
            runs.add(new int[]{smooth[gt], gt, gt});
        }

        double width = blockU1 - blockU0;
        double su0 = blockU0 + 0.3 * width, su1 = blockU1 - 0.3 * width;
        Card best = null;
        String failure = "no dark patch next to the bright block";
        for (int[] darkRun : runs) {
            if (darkRun[0] != -1) continue;
            for (int[] brightRun : runs) {
                if (brightRun[0] != 1) continue;
                int gap = brightRun[1] > darkRun[2]
                        ? brightRun[1] - darkRun[2] - 1
                        : darkRun[1] - brightRun[2] - 1;
                if (gap < 0 || gap > MAX_ROW_GAP) continue;
                double dT0 = darkRun[1] * stepT, dT1 = (darkRun[2] + 1) * stepT;
                double bT0 = brightRun[1] * stepT, bT1 = (brightRun[2] + 1) * stepT;
                double dLen = dT1 - dT0, bLen = bT1 - bT0;
                if (dLen < DARK_RUN_MIN_LENGTH || bLen < BRIGHT_RUN_MIN_LENGTH) continue;
                double ratio = bLen / dLen;
                if (ratio < BRIGHT_TO_DARK_MIN || ratio > BRIGHT_TO_DARK_MAX) continue;

                double half = bLen / 2;
                Samples firstHalf  = sampleT(img, map, beyondEnd, su0, su1, bT0 + 0.25 * half, bT0 + 0.75 * half);
                Samples secondHalf = sampleT(img, map, beyondEnd, su0, su1, bT0 + 1.25 * half, bT1 - 0.25 * half);
                Samples dark       = sampleT(img, map, beyondEnd, su0, su1, dT0 + 0.2 * dLen, dT1 - 0.2 * dLen);
                CardSearch attempt = validatePatches(firstHalf, secondHalf, dark);
                if (attempt.card != null) {
                    if (best == null || attempt.card.contrast > best.contrast) best = attempt.card;
                } else {
                    failure = attempt.failure;
                }
            }
        }
        return new CardSearch(best, best == null ? failure : null);
    }

    private static CardSearch validatePatches(Samples firstHalf, Samples secondHalf, Samples dark) {
        if (dark.insideFraction() < 0.9 || firstHalf.insideFraction() < 0.9
                || secondHalf.insideFraction() < 0.9) {
            return new CardSearch(null, "card partly outside image");
        }
        Samples white = firstHalf.lumaMean() >= secondHalf.lumaMean() ? firstHalf : secondHalf;
        Samples light = white == firstHalf ? secondHalf : firstHalf;
        double wL = white.lumaMean(), lL = light.lumaMean(), dL = dark.lumaMean();

        if (white.glareFraction() > MAX_GLARE_FRACTION) {
            return new CardSearch(null, "glare on white patch");
        }
        if (dL > DARK_MAX_RELATIVE * wL || wL - dL < MIN_WHITE_DARK_LUMA) {
            return new CardSearch(null, String.format(Locale.US,
                    "dark patch not dark enough (white=%.0f dark=%.0f)", wL, dL));
        }
        if ((lL - dL) < LIGHT_MIN_POSITION * (wL - dL)) {
            return new CardSearch(null, "light patch not found");
        }
        if (white.lumaVariation() > MAX_WHITE_VARIATION
                || light.lumaVariation() > MAX_PATCH_VARIATION
                || dark.lumaVariation() > MAX_PATCH_VARIATION) {
            return new CardSearch(null, "reference patches not uniform");
        }
        return new CardSearch(new Card(white.trimmedMean(), light.trimmedMean(), dark.trimmedMean()), null);
    }

    private static Samples sampleT(Image img, Homography map, boolean beyondEnd,
                                   double u0, double u1, double t0, double t1) {
        double v0 = beyondEnd ? 1 + t0 : -t1;
        double v1 = beyondEnd ? 1 + t1 : -t0;
        return sample(img, map, u0, u1, v0, v1, PATCH_SAMPLES, PATCH_SAMPLES);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sampling
    // ─────────────────────────────────────────────────────────────────────

    private static Samples sample(Image img, Homography map,
                                  double u0, double u1, double v0, double v1,
                                  int nu, int nv) {
        Samples s = new Samples(nu * nv);
        double[] xy = new double[2];
        double[] rgb = new double[3];
        for (int j = 0; j < nv; j++) {
            double v = v0 + (j + 0.5) * (v1 - v0) / nv;
            for (int i = 0; i < nu; i++) {
                double u = u0 + (i + 0.5) * (u1 - u0) / nu;
                if (!map.apply(u, v, xy) || !img.sampleBilinear(xy[0], xy[1], rgb)) continue;
                s.insideCount++;
                if (isRedMark(packRgb(rgb))) continue;
                s.add(rgb);
            }
        }
        return s;
    }

    private static final class Samples {
        final int total;
        int insideCount;
        private double[] r, g, b;
        private int n;
        private int glare;

        Samples(int capacity) {
            total = capacity;
            r = new double[capacity]; g = new double[capacity]; b = new double[capacity];
        }

        void add(double[] rgb) {
            r[n] = rgb[0]; g[n] = rgb[1]; b[n] = rgb[2];
            if (Math.max(rgb[0], Math.max(rgb[1], rgb[2])) >= GLARE_LEVEL) glare++;
            n++;
        }

        int count() { return n; }
        double insideFraction() { return total == 0 ? 0 : (double) insideCount / total; }
        double glareFraction() { return n == 0 ? 0 : (double) glare / n; }

        double[] trimmedMean() {
            return new double[]{trimmed(r), trimmed(g), trimmed(b)};
        }

        private double trimmed(double[] channel) {
            if (n == 0) return 0;
            double[] sorted = Arrays.copyOf(channel, n);
            Arrays.sort(sorted);
            int lo = (int) (n * 0.2), hi = Math.max(lo + 1, (int) Math.ceil(n * 0.8));
            double sum = 0;
            for (int i = lo; i < hi; i++) sum += sorted[i];
            return sum / (hi - lo);
        }

        double lumaMean() {
            if (n == 0) return 0;
            double sum = 0;
            for (int i = 0; i < n; i++) sum += 0.299 * r[i] + 0.587 * g[i] + 0.114 * b[i];
            return sum / n;
        }

        double lumaVariation() {
            if (n == 0) return 0;
            double mean = lumaMean(), sq = 0;
            for (int i = 0; i < n; i++) {
                double d = 0.299 * r[i] + 0.587 * g[i] + 0.114 * b[i] - mean;
                sq += d * d;
            }
            return Math.sqrt(sq / n) / Math.max(1, mean);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Geometry / image helpers
    // ─────────────────────────────────────────────────────────────────────

    /** Projective map from the unit square (u, v) to image pixels. */
    static final class Homography {
        private final double a, b, c, d, e, f, g, h;

        private Homography(double a, double b, double c, double d,
                           double e, double f, double g, double h) {
            this.a = a; this.b = b; this.c = c; this.d = d;
            this.e = e; this.f = f; this.g = g; this.h = h;
        }

        /** corners: (0,0)→p0, (1,0)→p1, (1,1)→p2, (0,1)→p3. */
        static Homography unitSquareTo(double[] p) {
            double x0 = p[0], y0 = p[1], x1 = p[2], y1 = p[3];
            double x2 = p[4], y2 = p[5], x3 = p[6], y3 = p[7];
            double dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3;
            double dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3;
            double den = dx1 * dy2 - dx2 * dy1;
            if (Math.abs(den) < 1e-9) return null;
            double g = (dx3 * dy2 - dx2 * dy3) / den;
            double h = (dx1 * dy3 - dx3 * dy1) / den;
            return new Homography(
                    x1 - x0 + g * x1, x3 - x0 + h * x3, x0,
                    y1 - y0 + g * y1, y3 - y0 + h * y3, y0,
                    g, h);
        }

        boolean apply(double u, double v, double[] out) {
            double w = g * u + h * v + 1;
            if (w <= 1e-9) return false;
            out[0] = (a * u + b * v + c) / w;
            out[1] = (d * u + e * v + f) / w;
            return true;
        }
    }

    private static final class Image {
        final int[] px; final int w, h;
        Image(int[] px, int w, int h) { this.px = px; this.w = w; this.h = h; }

        boolean sampleBilinear(double x, double y, double[] out) {
            double fx = x - 0.5, fy = y - 0.5;
            if (fx < 0 || fy < 0 || fx > w - 1 || fy > h - 1) return false;
            int x0 = (int) fx, y0 = (int) fy;
            int x1 = Math.min(x0 + 1, w - 1), y1 = Math.min(y0 + 1, h - 1);
            double ax = fx - x0, ay = fy - y0;
            int p00 = px[y0 * w + x0], p10 = px[y0 * w + x1];
            int p01 = px[y1 * w + x0], p11 = px[y1 * w + x1];
            for (int ch = 0; ch < 3; ch++) {
                int shift = 16 - 8 * ch;
                double top = ((p00 >> shift) & 255) * (1 - ax) + ((p10 >> shift) & 255) * ax;
                double bot = ((p01 >> shift) & 255) * (1 - ax) + ((p11 >> shift) & 255) * ax;
                out[ch] = top * (1 - ay) + bot * ay;
            }
            return true;
        }
    }

    private static final class Blob {
        final int id;
        int count, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        long sumX, sumY;

        Blob(int id) { this.id = id; }

        void add(int x, int y) {
            count++;
            sumX += x; sumY += y;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        double cx() { return (double) sumX / count + 0.5; }
        double cy() { return (double) sumY / count + 0.5; }
        double boxWidth()  { return maxX - minX + 1; }
        double boxHeight() { return maxY - minY + 1; }
    }

    private static double dist(double[] p, int i, int j) {
        return Math.hypot(p[2 * i] - p[2 * j], p[2 * i + 1] - p[2 * j + 1]);
    }

    private static double luma(double[] rgb) {
        return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2];
    }

    private static int packRgb(double[] rgb) {
        return ((int) Math.round(rgb[0]) << 16) | ((int) Math.round(rgb[1]) << 8) | (int) Math.round(rgb[2]);
    }

    private static String fmt(double[] v) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Math.round(v[i]));
        }
        return sb.append(')').toString();
    }
}
