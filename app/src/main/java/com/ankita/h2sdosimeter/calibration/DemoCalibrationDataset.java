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
    private static final String KEY_SEEDED = "demo_data_seeded_de00_v1";

    public static final String SYNTHETIC_LABEL_PREFIX = "[SYNTHETIC — NOT VALIDATED] ";

    // Synthetic ΔE₀₀ → ppm·hr points for software testing only.
    // Anchor: an unexposed strip measures ≈5.4 ΔE₀₀ on the marked badge.
    // Replace with points from strips exposed to KNOWN doses.

    private static final double[][] POINTS = {
        // { deltaE00, known_ppm_hr }
        {  5.5,  0.0 },   // Unexposed strip
        {  8.0,  1.0 },
        { 12.0,  3.0 },
        { 20.0,  6.0 },
        { 30.0, 10.0 },
        { 45.0, 15.0 },
        { 60.0, 20.0 },
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
