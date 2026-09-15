package com.ankita.h2sdosimeter.api;

import android.util.Log;

import com.ankita.h2sdosimeter.calibration.CalibrationCurve;
import com.ankita.h2sdosimeter.model.ColourAnalysisResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BackendSyncService — all backend interactions from the Android app.
 *
 * Every public method:
 * - Runs on a background thread (bgExecutor).
 * - Delivers result to a Callback on the CALLING thread's handler is NOT done here —
 *   callers receive the result via SyncCallback and must post to main thread themselves.
 * - Never crashes the app: failures are silent unless a callback handles them.
 *
 * DEMO mode: methods that receive isDemo=true skip all network calls immediately.
 */
public class BackendSyncService {

    private static final String TAG = "H2S-BackendSync";

    // Shared single-thread executor for all backend calls
    private static final ExecutorService bgExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "H2S-BackendSync");
                t.setDaemon(true);
                return t;
            });

    // -----------------------------------------------------------------------
    // Callback interface
    // -----------------------------------------------------------------------

    public interface SyncCallback {
        void onSuccess(JSONObject data);
        void onError(String message);
    }

    // -----------------------------------------------------------------------
    // SCAN SUBMISSION
    // -----------------------------------------------------------------------

    /**
     * Submits a completed scan result to the backend.
     * Called from ProcessingActivity after the pipeline finishes.
     *
     * @param result     The colour analysis result from the on-device pipeline
     * @param conversion The calibration conversion result (may be null)
     * @param workerId   Current worker ID
     * @param badgeId    Badge ID used for this scan
     * @param shiftName  Current shift name
     * @param callback   Invoked on a background thread — post to main if needed
     */
    public static void submitScan(ColourAnalysisResult result,
                                   CalibrationCurve.ConversionResult conversion,
                                   String workerId,
                                   String badgeId,
                                   String shiftName,
                                   SyncCallback callback) {

        if (!ApiConfig.BACKEND_SYNC_ENABLED) {
            Log.d(TAG, "Backend sync disabled — skipping scan submit");
            if (callback != null) callback.onError("Backend sync disabled");
            return;
        }

        if (result == null || !result.isRealCapture()) {
            Log.d(TAG, "Skipping scan submit: DEMO or null result");
            if (callback != null) callback.onError("Not a real capture");
            return;
        }

        bgExecutor.execute(() -> {
            try {
                JSONObject payload = buildScanPayload(result, conversion,
                        workerId, badgeId, shiftName);
                ApiClient.ApiResult apiResult = ApiClient.post("/scans", payload);

                if (apiResult.success && apiResult.body != null) {
                    Log.i(TAG, "Scan submitted to backend: id="
                            + apiResult.body.optJSONObject("data") != null
                            ? String.valueOf(apiResult.body.optJSONObject("data").optInt("id"))
                            : "?");
                    if (callback != null) callback.onSuccess(apiResult.body);
                } else {
                    Log.w(TAG, "Scan submit failed: " + apiResult.errorMsg);
                    if (callback != null) callback.onError(apiResult.errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Scan submit exception", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    // -----------------------------------------------------------------------
    // WORKER & BADGE
    // -----------------------------------------------------------------------

    public static void getWorker(String workerId, SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/workers/" + workerId);
            deliver(r, callback);
        });
    }

    public static void validateBadge(String badgeId, SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/badges/" + badgeId + "/validate");
            deliver(r, callback);
        });
    }

    // -----------------------------------------------------------------------
    // EXPOSURE HISTORY
    // -----------------------------------------------------------------------

    public static void getExposureHistory(String workerId, SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/scans/worker/" + workerId);
            deliver(r, callback);
        });
    }

    public static void getTodayExposure(String workerId, SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/scans/worker/" + workerId + "/today");
            deliver(r, callback);
        });
    }

    public static void getLatestScan(String workerId, SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/scans/worker/" + workerId + "/latest");
            deliver(r, callback);
        });
    }

    public static void getScanById(long scanId, SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/scans/" + scanId);
            deliver(r, callback);
        });
    }

    // -----------------------------------------------------------------------
    // CALIBRATION SYNC
    // -----------------------------------------------------------------------

    /**
     * Fetches server calibration points for the "default" version.
     * The Android app can use these to supplement or replace local calibration.
     */
    public static void getServerCalibrationPoints(SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/calibration/points?version=default");
            deliver(r, callback);
        });
    }

    public static void getCalibrationStatus(SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/calibration/status?version=default");
            deliver(r, callback);
        });
    }

    /**
     * Pushes a single device-local calibration point to the server.
     */
    public static void pushCalibrationPoint(double colourDiff, double knownPpmHr,
                                             String label, SyncCallback callback) {
        bgExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("colourDifference", colourDiff);
                body.put("knownPpmHr", knownPpmHr);
                body.put("label", label != null ? label : "");
                body.put("calibrationVersion", "default");
                body.put("addedBy", ApiConfig.CURRENT_WORKER_ID);

                ApiClient.ApiResult r = ApiClient.post("/calibration/points", body);
                deliver(r, callback);
            } catch (JSONException e) {
                if (callback != null) callback.onError("JSON error: " + e.getMessage());
            }
        });
    }

    // -----------------------------------------------------------------------
    // HEALTH CHECK
    // -----------------------------------------------------------------------

    /**
     * Quick ping to check if the backend is reachable.
     * Result: success = server is up, error = not reachable.
     */
    public static void checkHealth(SyncCallback callback) {
        bgExecutor.execute(() -> {
            ApiClient.ApiResult r = ApiClient.get("/health");
            deliver(r, callback);
        });
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static void deliver(ApiClient.ApiResult r, SyncCallback cb) {
        if (cb == null) return;
        if (r.success) {
            cb.onSuccess(r.body);
        } else {
            cb.onError(r.errorMsg != null ? r.errorMsg : "Unknown error");
        }
    }

    private static JSONObject buildScanPayload(ColourAnalysisResult result,
                                                CalibrationCurve.ConversionResult conversion,
                                                String workerId,
                                                String badgeId,
                                                String shiftName) throws JSONException {
        JSONObject json = new JSONObject();

        json.put("workerId",  workerId);
        json.put("badgeId",   badgeId);
        json.put("shiftName", shiftName != null ? shiftName : JSONObject.NULL);
        // ISO-8601 timestamp compatible with backend parser
        String ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
        json.put("scanTimestamp", ts);

        // Analysis status
        json.put("analysisStatus",        result.getStatus().name());
        json.put("analysisStatusMessage", result.getStatusMessage());

        if (result.getStatus() == ColourAnalysisResult.Status.SUCCESS) {
            json.put("rawSensorR",       result.getRawSensorR());
            json.put("rawSensorG",       result.getRawSensorG());
            json.put("rawSensorB",       result.getRawSensorB());
            json.put("referenceR",       result.getReferenceR());
            json.put("referenceG",       result.getReferenceG());
            json.put("referenceB",       result.getReferenceB());
            json.put("correctedSensorR", result.getCorrectedSensorR());
            json.put("correctedSensorG", result.getCorrectedSensorG());
            json.put("correctedSensorB", result.getCorrectedSensorB());
            // Calibration metric: lighting-corrected ΔE₀₀ (same value as calibration points)
            json.put("colourDifference", result.getDeltaE());
            json.put("colourMetric",     "CIEDE2000");
            json.put("rgbDistance",      result.getColourDifference());
            json.put("brightness",       result.getBrightness());
            json.put("sharpness",        result.getSharpness());
            json.put("imageQualityLabel",result.getImageQualityLabel());
        }

        // Calibration
        if (conversion != null && conversion.success) {
            json.put("calibrationStatus",
                    conversion.confidence == CalibrationCurve.Confidence.HIGH
                            ? "CALIBRATED" : "EXTRAPOLATED");
            json.put("estimatedPpmHr",    conversion.estimatedPpmHr);
            json.put("calibrationVersion","device-local");
        } else {
            json.put("calibrationStatus", "UNCALIBRATED");
        }

        return json;
    }
}
