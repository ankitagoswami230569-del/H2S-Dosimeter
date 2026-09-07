package com.ankita.h2sdosimeter.ui.scan;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.analysis.BadgeAnalysisPipeline;
import com.ankita.h2sdosimeter.api.ApiConfig;
import com.ankita.h2sdosimeter.api.BackendSyncService;
import com.ankita.h2sdosimeter.calibration.CalibrationCurve;
import com.ankita.h2sdosimeter.calibration.CalibrationPoint;
import com.ankita.h2sdosimeter.calibration.CalibrationStore;
import com.ankita.h2sdosimeter.calibration.EnvCompensation;
import com.ankita.h2sdosimeter.model.ColourAnalysisResult;
import com.ankita.h2sdosimeter.util.MockDataHelper;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ProcessingActivity — runs on-device colour analysis pipeline and syncs result
 * to the backend when BACKEND_SYNC_ENABLED is true.
 *
 * REAL capture path:
 *   1. Runs BadgeAnalysisPipeline (unchanged).
 *   2. Runs local CalibrationCurve conversion if calibration points exist.
 *   3. Posts the scan to backend via BackendSyncService (fire-and-forget).
 *   4. Stores the backend scan ID in the Intent for ScanResultActivity.
 *
 * DEMO path: unchanged — no backend calls are made.
 */
public class ProcessingActivity extends AppCompatActivity {

    private static final String TAG = "ProcessingActivity";

    public static final String EXTRA_ANALYSIS_RESULT = "extra_analysis_result";
    /** Backend-assigned scan ID, passed to ScanResultActivity. */
    public static final String EXTRA_BACKEND_SCAN_ID = "extra_backend_scan_id";

    private static final long STEP2_DELAY_MS = 1200L;
    private static final long STEP3_DELAY_MS = 1800L;
    private static final long STEP4_DELAY_MS = 2400L;
    private static final long MIN_DISPLAY_MS = 3000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private ImageView ivStep1, ivStep2, ivStep3, ivStep4;
    private TextView  tvStep1, tvStep2, tvStep3, tvStep4;

    private String imageUriString;
    private String imageFilePath;
    private volatile ColourAnalysisResult analysisResult;
    private volatile boolean analysisComplete  = false;
    private volatile boolean animationComplete = false;
    private volatile long    backendScanId     = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        imageUriString = getIntent().getStringExtra(CaptureActivity.EXTRA_IMAGE_URI);
        imageFilePath  = getIntent().getStringExtra(CaptureActivity.EXTRA_IMAGE_PATH);

        bindViews();
        startAnimationSequence();
        startAnalysis();
    }

    private void bindViews() {
        ivStep1 = findViewById(R.id.ivStep1);
        ivStep2 = findViewById(R.id.ivStep2);
        ivStep3 = findViewById(R.id.ivStep3);
        ivStep4 = findViewById(R.id.ivStep4);
        tvStep1 = findViewById(R.id.tvStep1);
        tvStep2 = findViewById(R.id.tvStep2);
        tvStep3 = findViewById(R.id.tvStep3);
        tvStep4 = findViewById(R.id.tvStep4);
    }

    // ------------------------------------------------------------------
    // Animation (unchanged)
    // ------------------------------------------------------------------

    private void startAnimationSequence() {
        activateStep(ivStep1, tvStep1);
        mainHandler.postDelayed(() -> activateStep(ivStep2, tvStep2), STEP2_DELAY_MS);
        mainHandler.postDelayed(() -> activateStep(ivStep3, tvStep3), STEP3_DELAY_MS);
        mainHandler.postDelayed(() -> {
            activateStep(ivStep4, tvStep4);
            animationComplete = true;
            maybeNavigate();
        }, STEP4_DELAY_MS);
        mainHandler.postDelayed(() -> {
            animationComplete = true;
            maybeNavigate();
        }, MIN_DISPLAY_MS);
    }

    private void activateStep(ImageView icon, TextView label) {
        icon.setColorFilter(getColor(R.color.colorAccent));
        label.setTextColor(getColor(R.color.white));
    }

    // ------------------------------------------------------------------
    // Background analysis + backend sync
    // ------------------------------------------------------------------

    private void startAnalysis() {
        if (imageUriString == null) {
            // DEMO flow — no real analysis, no backend call
            analysisResult   = ColourAnalysisResult.demo();
            analysisComplete = true;
            maybeNavigate();
            return;
        }

        final Uri uriForPipeline = imageFilePath != null
                ? Uri.fromFile(new java.io.File(imageFilePath))
                : Uri.parse(imageUriString);

        bgExecutor.execute(() -> {
            Log.d(TAG, "Starting image analysis on background thread");
            ColourAnalysisResult result = BadgeAnalysisPipeline.analyse(
                    getApplicationContext(), uriForPipeline);
            Log.d(TAG, "Analysis complete: status=" + result.getStatus()
                    + " diff=" + String.format("%.1f", result.getColourDifference()));

            // Run local calibration conversion.
            //
            // DUAL-PATH STRATEGY:
            //   1. PREFERRED: scale-position (lighting-independent - only
            //      available when the ArUco reference card was detected).
            //   2. FALLBACK: legacy colour-difference method, used when the
            //      scale-position path is unavailable or under-calibrated.
            //
            // Both raw values are passed through EnvCompensation first so a
            // scan taken far from the printed scale's reference temperature/
            // humidity doesn't read artificially high or low. With no real
            // environmental sensor wired up yet, this call uses the
            // reference condition and is a safe no-op (factor == 1).
            CalibrationCurve.ConversionResult conversion = null;
            if (result.getStatus() == ColourAnalysisResult.Status.SUCCESS) {
                Context appCtx = getApplicationContext();

                if (result.isCardDetected()) {
                    List<CalibrationPoint> scalePts = CalibrationStore.loadPointsByScale(appCtx);
                    double normalisedScalePos = EnvCompensation.normalise(result.getScalePosition());
                    conversion = CalibrationCurve.convertByScalePosition(normalisedScalePos, scalePts);
                }

                if (conversion == null || !conversion.success) {
                    List<CalibrationPoint> diffPts = CalibrationStore.loadPoints(appCtx);
                    double normalisedDiff = EnvCompensation.normalise(result.getColourDifference());
                    conversion = CalibrationCurve.convert(normalisedDiff, diffPts);
                }

                if (conversion.success) {
                    Log.d(TAG, "Local calib: "
                            + String.format("%.3f", conversion.estimatedPpmHr)
                            + " ppm.hr (" + conversion.confidence + ")");
                }
            }

            final CalibrationCurve.ConversionResult finalConversion = conversion;

            // Submit to backend (fire-and-forget — does NOT block navigation)
            if (ApiConfig.BACKEND_SYNC_ENABLED && result.isRealCapture()) {
                BackendSyncService.submitScan(
                        result,
                        finalConversion,
                        ApiConfig.CURRENT_WORKER_ID,
                        ApiConfig.CURRENT_BADGE_ID,
                        MockDataHelper.getCurrentMockShift().getShiftName(),
                        new BackendSyncService.SyncCallback() {
                            @Override
                            public void onSuccess(JSONObject data) {
                                try {
                                    JSONObject scanData = data.optJSONObject("data");
                                    if (scanData != null) {
                                        backendScanId = scanData.optLong("id", -1);
                                        Log.i(TAG, "Scan saved to backend: id=" + backendScanId);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse backend scan ID", e);
                                }
                            }

                            @Override
                            public void onError(String message) {
                                Log.w(TAG, "Backend sync failed (non-blocking): " + message);
                            }
                        });
            }

            mainHandler.post(() -> {
                analysisResult   = result;
                analysisComplete = true;
                maybeNavigate();
            });
        });
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    private void maybeNavigate() {
        if (animationComplete && analysisComplete) {
            navigateToResult();
        }
    }

    private void navigateToResult() {
        if (isFinishing() || isDestroyed()) return;

        Intent intent = new Intent(this, ScanResultActivity.class);
        if (imageUriString != null)
            intent.putExtra(CaptureActivity.EXTRA_IMAGE_URI, imageUriString);
        if (analysisResult != null)
            intent.putExtra(EXTRA_ANALYSIS_RESULT, analysisResult);
        if (backendScanId > 0)
            intent.putExtra(EXTRA_BACKEND_SCAN_ID, backendScanId);

        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        bgExecutor.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        mainHandler.removeCallbacksAndMessages(null);
        bgExecutor.shutdownNow();
        super.onBackPressed();
    }
}
