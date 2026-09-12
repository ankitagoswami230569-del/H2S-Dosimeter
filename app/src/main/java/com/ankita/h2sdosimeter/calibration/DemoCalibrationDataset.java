package com.ankita.h2sdosimeter.calibration;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * DemoCalibrationDataset — seeds the CalibrationStore with synthetic
 * calibration data for software testing only.
 *
 * NOT REAL LABORATORY DATA.
 */
public class DemoCalibrationDataset {

    private static final String TAG = "DemoCalibration";

    private static final String PREFS_NAME = "h2s_demo_calibration_meta";
    private static final String KEY_SEEDED = "demo_data_seeded_v2";

    public static final String SYNTHETIC_LABEL_PREFIX = "[SYNTHETIC — NOT VALIDATED] ";

    // Calibration based on actual colourDifference values from 6 strip images:
    // Strips measure 138–199 colour difference (out of 441 max).
    // Map this range to 1–10 ppm·hr.
    // Below 130 = near-white = 0 ppm·hr
    // 130–160 = light exposure = 1–3 ppm·hr
    // 161–190 = moderate = 4–7 ppm·hr
    // 191–220 = heavy = 8–10 ppm·hr
    // Above 220 = very dark = up to 20 ppm·hr

    private static final double[][] POINTS = {
        // { colourDifference, known_ppm_hr }
        {   0.0,  0.0 },   // Pure white
        { 100.0,  0.0 },   // Near-white
        { 130.0,  1.0 },   // Light exposure starts
        { 160.0,  3.0 },   // Light-moderate
        { 190.0,  6.0 },   // Moderate-heavy
        { 220.0, 10.0 },   // Heavy
        { 350.0, 15.0 },   // Very dark
        { 441.0, 20.0 },   // Pure black
    };

    public static boolean seedIfEmpty(Context context) {
        List<CalibrationPoint> existing = CalibrationStore.loadPoints(context);
        if (!existing.isEmpty()) return false;

        boolean alreadySeeded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SEEDED, false);
        if (alreadySeeded) return false;

        long now = System.currentTimeMillis();
        for (double[] pt : POINTS) {
            String label = SYNTHETIC_LABEL_PREFIX + "ΔE₀₀=" + pt[0] + " → " + pt[1] + " ppm·hr";
            CalibrationStore.addPoint(context, new CalibrationPoint(pt[0], pt[1], label, now++));
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SEEDED, true).apply();

        Log.i(TAG, "Seeded " + POINTS.length + " synthetic calibration points.");
        return true;
    }

    public static boolean isSyntheticDataActive(Context context) {
        for (CalibrationPoint p : CalibrationStore.loadPoints(context)) {
            if (p.getLabel().startsWith(SYNTHETIC_LABEL_PREFIX)) return true;
        }
        return false;
    }

    private DemoCalibrationDataset() {}
}
