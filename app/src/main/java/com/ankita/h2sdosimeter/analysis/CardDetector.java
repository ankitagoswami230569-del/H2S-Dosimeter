package com.ankita.h2sdosimeter.analysis;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CardDetector - the ONLY OpenCV-dependent stage of the analysis pipeline.
 *
 * Locates the four ArUco fiducial markers printed on the reference card
 * (dictionary DICT_4X4_50, ids {@link ReferenceCardSpec#MARKER_IDS}),
 * computes a homography from the detected marker centres to the canonical
 * marker centres defined in {@link ReferenceCardSpec}, and warps the
 * captured photo into a fixed-size, fronto-parallel "rectified" card
 * image. Every downstream stage (RegionSampler, ColorCalibrator,
 * ScaleReader) then works on stable, known pixel coordinates regardless of
 * how the card was angled or framed in the original photo.
 *
 * OpenCV's native library is loaded lazily on first use via OpenCVLoader.
 * This class fails CLEANLY (returns null) rather than throwing when:
 *   - the OpenCV native library cannot be loaded,
 *   - fewer than {@link ReferenceCardSpec#MARKER_COUNT} markers are found, or
 *   - the computed homography is degenerate (singular / non-invertible).
 */
public final class CardDetector {

    private static final String TAG = "CardDetector";

    private static volatile boolean openCvLoadAttempted = false;
    private static volatile boolean openCvLoaded = false;

    /**
     * Below this confidence, a detection is treated as unreliable and
     * rejected (return null) even though markers were technically found.
     */
    private static final double MIN_CONFIDENCE = 0.35;

    private CardDetector() { /* static entry point only */ }

    /** Result of a successful card detection. */
    public static final class Detection {
        /** The perspective-corrected card image, sized RECTIFIED_WIDTH x RECTIFIED_HEIGHT. */
        public final Bitmap rectified;
        /** Detection confidence in [0,1]; see {@link #estimateConfidence(int)}. */
        public final double confidence;

        public Detection(Bitmap rectified, double confidence) {
            this.rectified = rectified;
            this.confidence = confidence;
        }
    }

    /**
     * Detects the reference card in {@code input} and returns the rectified
     * card image, or {@code null} on any failure (see class javadoc).
     */
    public static Detection detect(Bitmap input) {
        if (input == null) return null;

        if (!ensureOpenCvLoaded()) {
            Log.w(TAG, "OpenCV native library unavailable - cannot detect reference card");
            return null;
        }

        Mat rgba = null;
        Mat gray = null;
        Mat ids = null;
        Mat homography = null;
        Mat rectifiedMat = null;
        List<Mat> corners = new ArrayList<>();
        List<Mat> rejected = new ArrayList<>();

        try {
            rgba = new Mat();
            Utils.bitmapToMat(input, rgba);

            gray = new Mat();
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

            Dictionary dictionary = Objdetect.getPredefinedDictionary(ReferenceCardSpec.ARUCO_DICT_4X4_50);
            DetectorParameters parameters = new DetectorParameters();
            ArucoDetector detector = new ArucoDetector(dictionary, parameters);

            ids = new Mat();
            detector.detectMarkers(gray, corners, ids, rejected);

            int detectedCount = ids.empty() ? 0 : ids.rows();
            if (detectedCount < ReferenceCardSpec.MARKER_COUNT) {
                Log.d(TAG, "Only " + detectedCount + " marker(s) detected, need "
                        + ReferenceCardSpec.MARKER_COUNT);
                return null;
            }

            Map<Integer, Point> centreById = new HashMap<>();
            for (int i = 0; i < detectedCount; i++) {
                int id = (int) ids.get(i, 0)[0];
                centreById.put(id, cornerCentre(corners.get(i)));
            }

            for (int requiredId : ReferenceCardSpec.MARKER_IDS) {
                if (!centreById.containsKey(requiredId)) {
                    Log.d(TAG, "Required marker id " + requiredId + " not found among detections");
                    return null;
                }
            }

            List<Point> srcPts = new ArrayList<>();
            List<Point> dstPts = new ArrayList<>();
            for (int i = 0; i < ReferenceCardSpec.MARKER_IDS.length; i++) {
                int id = ReferenceCardSpec.MARKER_IDS[i];
                srcPts.add(centreById.get(id));

                ReferenceCardSpec.NormPoint dst = ReferenceCardSpec.MARKER_CENTRES[i];
                dstPts.add(new Point(
                        dst.x * ReferenceCardSpec.RECTIFIED_WIDTH,
                        dst.y * ReferenceCardSpec.RECTIFIED_HEIGHT));
            }

            MatOfPoint2f src = new MatOfPoint2f();
            src.fromList(srcPts);
            MatOfPoint2f dst = new MatOfPoint2f();
            dst.fromList(dstPts);

            homography = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 3.0);
            if (homography == null || homography.empty() || !isWellFormedHomography(homography)) {
                Log.w(TAG, "Degenerate homography - rejecting detection");
                return null;
            }

            rectifiedMat = new Mat();
            Imgproc.warpPerspective(rgba, rectifiedMat, homography,
                    new Size(ReferenceCardSpec.RECTIFIED_WIDTH, ReferenceCardSpec.RECTIFIED_HEIGHT));

            double confidence = estimateConfidence(detectedCount);
            if (confidence < MIN_CONFIDENCE) {
                Log.w(TAG, "Detection confidence too low: " + confidence);
                return null;
            }

            Bitmap rectifiedBitmap = Bitmap.createBitmap(
                    ReferenceCardSpec.RECTIFIED_WIDTH, ReferenceCardSpec.RECTIFIED_HEIGHT,
                    Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rectifiedMat, rectifiedBitmap);

            return new Detection(rectifiedBitmap, confidence);

        } catch (Exception e) {
            Log.e(TAG, "Card detection failed", e);
            return null;
        } finally {
            release(rgba);
            release(gray);
            release(ids);
            release(homography);
            release(rectifiedMat);
            for (Mat m : corners) release(m);
            for (Mat m : rejected) release(m);
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static Point cornerCentre(Mat markerCorners) {
        // markerCorners is a 1x4 Mat of CV_32FC2 (four corner points).
        double cx = 0, cy = 0;
        for (int k = 0; k < 4; k++) {
            double[] pt = markerCorners.get(0, k);
            cx += pt[0];
            cy += pt[1];
        }
        return new Point(cx / 4.0, cy / 4.0);
    }

    /** Rejects a homography that is empty, non-square, or singular. */
    private static boolean isWellFormedHomography(Mat h) {
        if (h.rows() != 3 || h.cols() != 3) return false;
        double det = Core.determinant(h);
        return !Double.isNaN(det) && !Double.isInfinite(det) && Math.abs(det) > 1e-9;
    }

    /**
     * Simple confidence heuristic: exactly the required markers found -> 1.0.
     * Extra spurious detections (uncommon with a small, printed dictionary)
     * slightly reduce confidence since they suggest a noisier image.
     */
    private static double estimateConfidence(int totalDetected) {
        if (totalDetected < ReferenceCardSpec.MARKER_COUNT) return 0.0;
        if (totalDetected == ReferenceCardSpec.MARKER_COUNT) return 1.0;
        int extra = totalDetected - ReferenceCardSpec.MARKER_COUNT;
        return Math.max(0.5, 1.0 - 0.1 * extra);
    }

    private static void release(Mat m) {
        if (m != null) m.release();
    }

    /**
     * Lazily loads the OpenCV native library. Safe to call repeatedly and
     * from multiple threads - the load is attempted at most once.
     */
    private static boolean ensureOpenCvLoaded() {
        if (openCvLoaded) return true;
        if (openCvLoadAttempted) return false;

        synchronized (CardDetector.class) {
            if (openCvLoadAttempted) return openCvLoaded;
            openCvLoadAttempted = true;
            try {
                // initDebug() loads the native library bundled in the APK
                // (no Play Services / async callback required).
                openCvLoaded = OpenCVLoader.initDebug();
            } catch (Throwable t) {
                Log.e(TAG, "OpenCV native library failed to load", t);
                openCvLoaded = false;
            }
        }
        return openCvLoaded;
    }
}
