package com.ankita.h2sdosimeter.analysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ReferenceCardSpec - single source of truth for the printed reference card
 * layout shared between this app and the physical card artwork.
 *
 * WHY A PRINTED CARD:
 * Consumer smartphone cameras auto white-balance and auto-expose, so the
 * same badge photographed under different lighting produces very different
 * raw RGB values. Placing the badge next to a printed card that carries
 * (a) four ArUco fiducial markers for geometric registration and
 * (b) a set of known-colour reference swatches lets the app both locate the
 * card precisely (via {@link CardDetector}) and cancel out that photo's
 * lighting bias (via {@link ColorCalibrator}) — making the reading
 * lighting-independent.
 *
 * COORDINATE SYSTEM:
 * Every region below is expressed in NORMALISED card coordinates: x and y
 * both range over [0, 1], with the origin at the top-left corner, measured
 * on the RECTIFIED card image (i.e. after {@link CardDetector} has warped
 * the photographed card to a fronto-parallel view). Multiplying by
 * {@link #RECTIFIED_WIDTH} / {@link #RECTIFIED_HEIGHT} gives pixel
 * coordinates on the rectified bitmap.
 *
 * This class contains NO Android or OpenCV imports so it can be shared by
 * plain JVM unit tests and (conceptually) by whatever tool renders the
 * printed card artwork.
 */
public final class ReferenceCardSpec {

    private ReferenceCardSpec() { /* constants only */ }

    // ------------------------------------------------------------------
    // ArUco marker configuration
    // ------------------------------------------------------------------

    /**
     * ArUco predefined dictionary identifier, matching
     * org.opencv.objdetect.Objdetect.DICT_4X4_50 (value 0). Declared here as
     * a plain int constant so this class stays free of OpenCV imports.
     */
    public static final int ARUCO_DICT_4X4_50 = 0;

    /** Number of fiducial markers printed on the card. */
    public static final int MARKER_COUNT = 4;

    /**
     * Marker ids in canonical corner order: top-left, top-right,
     * bottom-right, bottom-left (clockwise from TL). CardDetector uses this
     * order to build the homography source/destination point pairs.
     */
    public static final int[] MARKER_IDS = {0, 1, 2, 3};

    /** Fixed size (pixels) of the rectified/warped card image. */
    public static final int RECTIFIED_WIDTH  = 800;
    public static final int RECTIFIED_HEIGHT = 500;

    /**
     * Normalised centres of the four markers, in the SAME order as
     * {@link #MARKER_IDS} (TL, TR, BR, BL). These are the homography
     * destination points that detected marker centres are warped onto.
     */
    public static final NormPoint[] MARKER_CENTRES = {
            new NormPoint(0.05, 0.06),  // id 0 - top-left
            new NormPoint(0.95, 0.06),  // id 1 - top-right
            new NormPoint(0.95, 0.94),  // id 2 - bottom-right
            new NormPoint(0.05, 0.94),  // id 3 - bottom-left
    };

    // ------------------------------------------------------------------
    // Reaction strip - where the badge sensor is presented against the card
    // ------------------------------------------------------------------

    /**
     * Region where the badge's colour-changing reaction strip is placed for
     * reading, once the badge is laid on / against the printed card per the
     * capture guidance shown to the user.
     */
    public static final NormRect REACTION_STRIP_REGION =
            new NormRect(0.40, 0.40, 0.20, 0.12);

    // ------------------------------------------------------------------
    // Reference colour scale swatches
    // ------------------------------------------------------------------

    /**
     * PLACEHOLDER swatch colours approximating a lead-acetate style strip
     * darkening from pale cream through to near-black as H2S dose
     * accumulates. These are NOT laboratory-measured colours - they exist
     * so the geometry/algorithm can be exercised end-to-end. Replace with
     * spectrophotometer-measured swatch colours from the actual printed
     * card stock before any real-world deployment.
     */
    public static final Swatch[] SCALE_SWATCHES = {
            new Swatch(0, new NormRect(0.08, 0.72, 0.10, 0.10), new int[]{238, 230, 214}),
            new Swatch(1, new NormRect(0.24, 0.72, 0.10, 0.10), new int[]{221, 202, 174}),
            new Swatch(2, new NormRect(0.40, 0.72, 0.10, 0.10), new int[]{193, 165, 135}),
            new Swatch(3, new NormRect(0.56, 0.72, 0.10, 0.10), new int[]{156, 124, 103}),
            new Swatch(4, new NormRect(0.72, 0.72, 0.10, 0.10), new int[]{104,  82,  70}),
            new Swatch(5, new NormRect(0.88, 0.72, 0.08, 0.10), new int[]{ 38,  32,  30}),
    };

    public static final int SWATCH_COUNT = SCALE_SWATCHES.length;

    public static List<Swatch> swatchList() {
        return Collections.unmodifiableList(Arrays.asList(SCALE_SWATCHES));
    }

    // ------------------------------------------------------------------
    // Expiry indicator patch
    // ------------------------------------------------------------------

    /**
     * A single printed patch that itself darkens over time / with
     * temperature exposure, used as a badge-expiry indicator independent
     * of the reaction strip.
     */
    public static final NormRect EXPIRY_PATCH_REGION =
            new NormRect(0.86, 0.10, 0.10, 0.10);

    // ------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------

    /** A normalised (x, y) point in [0,1] card coordinates. */
    public static final class NormPoint {
        public final double x;
        public final double y;

        public NormPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    /** A normalised rectangle in [0,1] card coordinates. */
    public static final class NormRect {
        public final double x;
        public final double y;
        public final double width;
        public final double height;

        public NormRect(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * One reference-scale swatch: a sample region on the printed card, its
     * known reference colour (as printed, PLACEHOLDER value), and the dose
     * level it represents on the scale (0 = unexposed/palest, increasing
     * with accumulated dose).
     */
    public static final class Swatch {
        public final int doseIndex;
        public final NormRect region;
        /** Known reference colour {r, g, b}, 0-255 each. PLACEHOLDER value. */
        public final int[] rgb;

        public Swatch(int doseIndex, NormRect region, int[] rgb) {
            this.doseIndex = doseIndex;
            this.region = region;
            this.rgb = rgb;
        }
    }
}
