package com.ankita.h2sdosimeter.calibration;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CalibrationStore - persists calibration points using SharedPreferences + JSON.
 *
 * Storage key: "h2s_calibration_points" in prefs file "h2s_calibration".
 *
 * Thread-safety: all writes go through apply() (async) - safe to call on
 * the main thread. Reads are synchronous and lightweight (small JSON list).
 *
 * Maximum stored points: MAX_POINTS (20). Oldest point is dropped when
 * this limit is exceeded to prevent unbounded storage growth.
 */
public class CalibrationStore {

    private static final String PREFS_NAME  = "h2s_calibration";
    private static final String KEY_POINTS  = "calibration_points";
    public  static final int    MAX_POINTS  = 20;

    // Minimum calibration points required to attempt ppm.hr conversion
    public static final int MIN_POINTS_FOR_CONVERSION = 2;

    // JSON field names
    private static final String F_DIFF      = "diff";
    private static final String F_PPM       = "ppm";
    private static final String F_LABEL     = "label";
    private static final String F_TIMESTAMP = "ts";

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    /**
     * Returns all stored calibration points sorted by colourDifference (ascending).
     * Returns an empty list if none are stored.
     */
    public static List<CalibrationPoint> loadPoints(Context context) {
        SharedPreferences prefs = prefs(context);
        String json = prefs.getString(KEY_POINTS, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();

        List<CalibrationPoint> points = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                points.add(new CalibrationPoint(
                        obj.getDouble(F_DIFF),
                        obj.getDouble(F_PPM),
                        obj.optString(F_LABEL, ""),
                        obj.optLong(F_TIMESTAMP, 0L)
                ));
            }
        } catch (JSONException e) {
            // Corrupted storage - return empty so the user can re-enter points
            return new ArrayList<>();
        }

        // Sort by colourDifference so the calibration curve is ordered
        Collections.sort(points, (a, b) ->
                Double.compare(a.getColourDifference(), b.getColourDifference()));
        return points;
    }

    /**
     * Returns true if enough calibration points exist to attempt conversion.
     */
    public static boolean isCalibrated(Context context) {
        return loadPoints(context).size() >= MIN_POINTS_FOR_CONVERSION;
    }

    // -----------------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------------

    /**
     * Adds a new calibration point. If MAX_POINTS is already reached,
     * removes the oldest point (by timestamp) before adding.
     *
     * @return true if the point was saved successfully.
     */
    public static boolean addPoint(Context context, CalibrationPoint point) {
        List<CalibrationPoint> points = loadPoints(context);

        // Enforce max points limit - drop oldest by timestamp
        if (points.size() >= MAX_POINTS) {
            CalibrationPoint oldest = Collections.min(points,
                    (a, b) -> Long.compare(a.getTimestampMs(), b.getTimestampMs()));
            points.remove(oldest);
        }

        points.add(point);
        return savePoints(context, points);
    }

    /**
     * Removes a calibration point by its exact colourDifference value.
     * Uses epsilon comparison for floating-point safety.
     *
     * @return true if a point was removed, false if none matched.
     */
    public static boolean removePoint(Context context, double colourDiff) {
        List<CalibrationPoint> points = loadPoints(context);
        boolean removed = points.removeIf(
                p -> Math.abs(p.getColourDifference() - colourDiff) < 0.001);
        if (removed) savePoints(context, points);
        return removed;
    }

    /** Removes all calibration points. */
    public static void clearAll(Context context) {
        prefs(context).edit().remove(KEY_POINTS).apply();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static boolean savePoints(Context context, List<CalibrationPoint> points) {
        try {
            JSONArray arr = new JSONArray();
            for (CalibrationPoint p : points) {
                JSONObject obj = new JSONObject();
                obj.put(F_DIFF,      p.getColourDifference());
                obj.put(F_PPM,       p.getKnownPpmHr());
                obj.put(F_LABEL,     p.getLabel());
                obj.put(F_TIMESTAMP, p.getTimestampMs());
                arr.put(obj);
            }
            prefs(context).edit().putString(KEY_POINTS, arr.toString()).apply();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
