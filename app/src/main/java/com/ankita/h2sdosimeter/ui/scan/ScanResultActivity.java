package com.ankita.h2sdosimeter.ui.scan;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.analysis.StripColourMeasurement;
import com.ankita.h2sdosimeter.api.ApiConfig;
import com.ankita.h2sdosimeter.api.BackendSyncService;
import com.ankita.h2sdosimeter.calibration.CalibrationActivity;
import com.ankita.h2sdosimeter.calibration.CalibrationCurve;
import com.ankita.h2sdosimeter.calibration.CalibrationPoint;
import com.ankita.h2sdosimeter.calibration.CalibrationStore;
import com.ankita.h2sdosimeter.model.ColourAnalysisResult;
import com.ankita.h2sdosimeter.model.ExposureRecord;
import com.ankita.h2sdosimeter.ui.dashboard.DashboardActivity;
import com.ankita.h2sdosimeter.util.MockDataHelper;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ScanResultActivity - Phase 4: Displays real analysis results or demo data.
 *
 * REAL capture path:
 *   - Shows sensor RGB, corrected RGB, colour difference.
 *   - Never shows the hardcoded 0.8 ppm.hr demo value.
 *   - Marks exposure as "Calibration required" since no validated
 *     calibration curve exists in this build.
 *   - Shows QUALITY_REJECTED or REGION_NOT_FOUND with clear messages.
 *
 * DEMO path:
 *   - Behaves exactly as before (mock 0.8 ppm.hr [DEMO]).
 */
public class ScanResultActivity extends AppCompatActivity {

    public static final String EXTRA_RECORD_ID = "extra_record_id";
    private static final String TAG = "ScanResultActivity";

    // -- Primary result card
    private TextView tvExposureValue;
    private TextView tvExposureUnits;
    private TextView tvResultCardTitle;
    private TextView tvResultDemoBadge;
    private TextView tvExposureCategory;
    private ImageView ivCategoryIcon;
    private TextView tvCategoryDescription;

    // -- Scan details card
    private TextView tvScanDate;
    private TextView tvScanTime;
    private TextView tvScanShift;
    private TextView tvScanBadgeId;
    private TextView tvImageQuality;

    // -- The DEMO notice banner (hidden for real captures)
    private View demoNoticeBanner;

    // -- Calibration section (shown for real SUCCESS captures)
    private View   calibrationSection;
    private TextView tvCalibStatus;
    private TextView tvCalibPpmValue;
    private TextView tvCalibConfidence;
    private View   btnOpenCalibration;

    private ColourAnalysisResult analysisResult;
    private String imageUriString;
    private long   backendScanId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_result);

        imageUriString = getIntent().getStringExtra(CaptureActivity.EXTRA_IMAGE_URI);
        analysisResult = (ColourAnalysisResult)
                getIntent().getSerializableExtra(ProcessingActivity.EXTRA_ANALYSIS_RESULT);
        backendScanId  = getIntent().getLongExtra(ProcessingActivity.EXTRA_BACKEND_SCAN_ID, -1);

        setupToolbar();
        bindViews();
        populateResult();
        setupClickListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("Scan Result");
        }
    }

    private void bindViews() {
        tvExposureValue       = findViewById(R.id.tvExposureValue);
        tvExposureUnits       = findViewById(R.id.tvExposureUnits);
        tvResultCardTitle     = findViewById(R.id.tvResultCardTitle);
        tvResultDemoBadge     = findViewById(R.id.tvResultDemoBadge);
        tvExposureCategory    = findViewById(R.id.tvExposureCategory);
        ivCategoryIcon        = findViewById(R.id.ivCategoryIcon);
        tvCategoryDescription = findViewById(R.id.tvCategoryDescription);
        tvScanDate            = findViewById(R.id.tvScanDate);
        tvScanTime            = findViewById(R.id.tvScanTime);
        tvScanShift           = findViewById(R.id.tvScanShift);
        tvScanBadgeId         = findViewById(R.id.tvScanBadgeId);
        tvImageQuality        = findViewById(R.id.tvImageQuality);
        demoNoticeBanner      = findViewById(R.id.demoNoticeBanner);

        // Calibration section
        calibrationSection  = findViewById(R.id.calibrationResultSection);
        tvCalibStatus       = findViewById(R.id.tvCalibResultStatus);
        tvCalibPpmValue     = findViewById(R.id.tvCalibPpmValue);
        tvCalibConfidence   = findViewById(R.id.tvCalibConfidence);
        btnOpenCalibration  = findViewById(R.id.btnOpenCalibration);
    }

    // ------------------------------------------------------------------
    // Result population
    // ------------------------------------------------------------------

    private void populateResult() {
        boolean isReal = analysisResult != null && analysisResult.isRealCapture();

        if (isReal) {
            populateRealResult(analysisResult);
        } else {
            populateDemoResult();
        }
    }

    private void populateRealResult(ColourAnalysisResult result) {
        // Hide the DEMO banner - this is a real capture
        if (demoNoticeBanner != null)  demoNoticeBanner.setVisibility(View.GONE);
        // Hide "DEMO" badge chip on the result card
        if (tvResultDemoBadge != null) tvResultDemoBadge.setVisibility(View.GONE);
        // Update card title — show backend sync status
        if (tvResultCardTitle != null) {
            if (backendScanId > 0) {
                tvResultCardTitle.setText("COLOUR ANALYSIS - REAL [Saved #" + backendScanId + "]");
            } else {
                tvResultCardTitle.setText("COLOUR ANALYSIS - REAL [Local only]");
            }
        }

        // Date/time from now (real capture happened just now)
        String now = new SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(new Date());
        String timeNow = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        tvScanDate.setText(now);
        tvScanTime.setText(timeNow);
        tvScanShift.setText(MockDataHelper.getCurrentMockShift().getShiftName());
        tvScanBadgeId.setText(MockDataHelper.getMockBadge().getBadgeId());

        switch (result.getStatus()) {
            case SUCCESS:
                populateSuccessResult(result);
                break;

            case QUALITY_REJECTED:
                if (calibrationSection != null) calibrationSection.setVisibility(View.GONE);
                tvExposureValue.setText("--");
                tvExposureValue.setTextColor(getColor(R.color.colorTextSecondary));
                if (tvExposureUnits != null)
                    tvExposureUnits.setText("Analysis unavailable - image quality too low");
                tvImageQuality.setText("Poor");
                tvImageQuality.setTextColor(getColor(R.color.colorStatusDanger));
                applyCategory(
                        "ANALYSIS UNAVAILABLE",
                        R.color.colorTextSecondary,
                        R.drawable.bg_status_low,
                        R.drawable.ic_warning,
                        R.color.colorStatusWarning,
                        "Image quality too low to analyse.\n"
                        + result.getStatusMessage()
                        + "\nPlease retake in better lighting."
                );
                break;

            case REGION_NOT_FOUND:
                if (calibrationSection != null) calibrationSection.setVisibility(View.GONE);
                if (tvResultCardTitle != null) tvResultCardTitle.setText("SENSOR STRIP NOT DETECTED");
                tvExposureValue.setText("N/A");
                tvExposureValue.setTextColor(getColor(R.color.colorStatusDanger));
                if (tvExposureUnits != null) {
                    tvExposureUnits.setText("Colour Diff: N/A   RGB: N/A   H2S Exposure: N/A");
                    tvExposureUnits.setTextColor(getColor(R.color.colorStatusDanger));
                }
                tvImageQuality.setText(result.getImageQualityLabel());
                applyCategory(
                        "SENSOR STRIP NOT DETECTED",
                        R.color.colorStatusDanger,
                        R.drawable.bg_status_high,
                        R.drawable.ic_warning,
                        R.color.colorStatusDanger,
                        result.getStatusMessage()
                        + "\n\n[DEBUG] " + result.getDetectionDebug()
                );
                break;

            default: // ERROR
                if (calibrationSection != null) calibrationSection.setVisibility(View.GONE);
                tvExposureValue.setText("--");
                if (tvExposureUnits != null)
                    tvExposureUnits.setText("Analysis failed - see detail below");
                tvImageQuality.setText("Error");
                tvImageQuality.setTextColor(getColor(R.color.colorStatusDanger));
                applyCategory(
                        "ERROR",
                        R.color.colorStatusDanger,
                        R.drawable.bg_status_high,
                        R.drawable.ic_warning,
                        R.color.colorStatusDanger,
                        "Analysis failed: " + result.getStatusMessage()
                );
                break;
        }
    }

    private void populateSuccessResult(ColourAnalysisResult result) {
        tvImageQuality.setText(result.getImageQualityLabel() + " [REAL]");
        tvImageQuality.setTextColor(getColor(R.color.colorAccent));

        // ΔE₀₀: lighting-corrected colour change of the strip (calibration input)
        double deltaE = result.getDeltaE();
        List<CalibrationPoint> calibPoints = CalibrationStore.loadPoints(this);
        CalibrationCurve.ConversionResult localConversion =
                CalibrationCurve.convert(deltaE, calibPoints);

        if (localConversion.success) {
            tvExposureValue.setText(String.format(Locale.US, "%.2f", localConversion.estimatedPpmHr));
            tvExposureValue.setTextColor(getColor(R.color.colorAccent));
            if (tvExposureUnits != null) {
                tvExposureUnits.setText(String.format(Locale.US,
                        "ppm.hr [ESTIMATE]   ΔE₀₀ = %.1f", deltaE));
                tvExposureUnits.setTextColor(getColor(R.color.colorAccent));
            }
            showCalibrationResult(result, localConversion, "device-local");
        } else {
            tvExposureValue.setText(String.format(Locale.US, "%.1f", deltaE));
            tvExposureValue.setTextColor(getColor(R.color.colorAccent));
            if (tvExposureUnits != null) {
                tvExposureUnits.setText("ΔE₀₀ colour change [CALIBRATION REQUIRED]");
                tvExposureUnits.setTextColor(getColor(R.color.colorStatusWarning));
            }
            showCalibrationRequired(result, localConversion.message);
        }

        // -- If scan was saved to backend, fetch server-calibrated result --
        // Server may have calibration data the device doesn't have locally.
        if (backendScanId > 0 && ApiConfig.BACKEND_SYNC_ENABLED) {
            fetchBackendCalibration(backendScanId);
        }

        // Category based on ΔE₀₀
        if (deltaE < StripColourMeasurement.MINIMAL_CHANGE_MAX_DE) {
            applyCategory("MINIMAL CHANGE", R.color.colorStatusSafe, R.drawable.bg_status_low,
                    R.drawable.ic_verified, R.color.colorStatusSafe,
                    "Sensor shows minimal colour change from reference white.\n"
                    + "Corrected RGB: " + result.getCorrectedRgbString());
        } else if (deltaE < StripColourMeasurement.MODERATE_CHANGE_MAX_DE) {
            applyCategory("MODERATE CHANGE", R.color.colorStatusWarning, R.drawable.bg_status_elevated,
                    R.drawable.ic_warning, R.color.colorStatusWarning,
                    "Sensor shows moderate colour change from reference.\n"
                    + "Corrected RGB: " + result.getCorrectedRgbString());
        } else {
            applyCategory("SIGNIFICANT CHANGE", R.color.colorStatusDanger, R.drawable.bg_status_high,
                    R.drawable.ic_warning, R.color.colorStatusDanger,
                    "Sensor shows significant colour change from reference.\n"
                    + "Corrected RGB: " + result.getCorrectedRgbString());
        }
    }

    /**
     * Fetches the backend-stored scan record and updates the ppm.hr display
     * if the server applied calibration that differs from (or supplements) the local result.
     */
    private void fetchBackendCalibration(long scanId) {
        BackendSyncService.getScanById(scanId, new BackendSyncService.SyncCallback() {
            @Override
            public void onSuccess(JSONObject body) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        JSONObject data = body.optJSONObject("data");
                        if (data == null) return;

                        String calibStatus = data.optString("calibrationStatus", "UNCALIBRATED");
                        double ppmHr       = data.optDouble("estimatedPpmHr", Double.NaN);
                        String safetyNote  = data.optString("safetyNotice", "");

                        if (!Double.isNaN(ppmHr) && !"UNCALIBRATED".equals(calibStatus)) {
                            // Server has a calibrated result — update display
                            Log.d(TAG, "Server calibration: " + ppmHr + " ppm.hr [" + calibStatus + "]");

                            if (calibrationSection != null) {
                                calibrationSection.setVisibility(View.VISIBLE);
                            }
                            if (tvCalibPpmValue != null) {
                                tvCalibPpmValue.setText(String.format(Locale.US,
                                        "%.3f ppm.hr [ESTIMATE — server]", ppmHr));
                                tvCalibPpmValue.setTextColor(getColor(R.color.colorAccent));
                            }
                            if (tvCalibStatus != null) {
                                boolean interp = "CALIBRATED".equals(calibStatus);
                                tvCalibStatus.setText(interp
                                        ? "✓ Server-calibrated estimate (id #" + scanId + ")"
                                        : "⚠ Server estimate (extrapolated, id #" + scanId + ")");
                                tvCalibStatus.setTextColor(getColor(interp
                                        ? R.color.colorStatusSafe : R.color.colorStatusWarning));
                            }
                            if (tvCalibConfidence != null) {
                                String category = data.optString("exposureCategory", "UNKNOWN");
                                tvCalibConfidence.setText("Category: " + category
                                        + " | " + safetyNote.substring(0, Math.min(60, safetyNote.length())) + "…");
                                tvCalibConfidence.setTextColor(getColor(R.color.colorTextSecondary));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not parse server calibration result", e);
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.d(TAG, "Could not fetch backend scan (non-blocking): " + message);
            }
        });
    }

    /**
     * Shows the calibration-derived ppm.hr estimate in the calibration section.
     * @param source "device-local" or "server" — shown in the label for transparency.
     */
    private void showCalibrationResult(ColourAnalysisResult result,
                                        CalibrationCurve.ConversionResult conversion,
                                        String source) {
        if (calibrationSection == null) return;
        calibrationSection.setVisibility(View.VISIBLE);

        String ppmFormatted = String.format(Locale.US, "%.3f ppm.hr [ESTIMATE — %s]",
                conversion.estimatedPpmHr, source);
        tvCalibPpmValue.setText(ppmFormatted);
        tvCalibPpmValue.setTextColor(getColor(R.color.colorAccent));

        if (conversion.confidence == CalibrationCurve.Confidence.HIGH) {
            tvCalibStatus.setText("✓ Calibrated estimate (interpolated)");
            tvCalibStatus.setTextColor(getColor(R.color.colorStatusSafe));
            tvCalibConfidence.setText("Confidence: WITHIN calibration range");
            tvCalibConfidence.setTextColor(getColor(R.color.colorStatusSafe));
        } else {
            tvCalibStatus.setText("⚠ Calibrated estimate (extrapolated)");
            tvCalibStatus.setTextColor(getColor(R.color.colorStatusWarning));
            tvCalibConfidence.setText("Confidence: OUTSIDE calibration range — lower reliability");
            tvCalibConfidence.setTextColor(getColor(R.color.colorStatusWarning));
        }

        if (btnOpenCalibration != null) {
            btnOpenCalibration.setOnClickListener(v -> openCalibration());
        }
    }

    /**
     * Shows the "CALIBRATION REQUIRED" state in the calibration section.
     */
    private void showCalibrationRequired(ColourAnalysisResult result, String reason) {
        if (calibrationSection == null) return;
        calibrationSection.setVisibility(View.VISIBLE);

        tvCalibPpmValue.setText("CALIBRATION REQUIRED");
        tvCalibPpmValue.setTextColor(getColor(R.color.colorStatusWarning));
        tvCalibStatus.setText(String.format(Locale.US,
                "ΔE₀₀ colour change: %.1f", result.getDeltaE()));
        tvCalibStatus.setTextColor(getColor(R.color.colorTextSecondary));
        tvCalibConfidence.setText(reason + "\nTap below to add calibration points.");
        tvCalibConfidence.setTextColor(getColor(R.color.colorTextSecondary));

        if (btnOpenCalibration != null) {
            btnOpenCalibration.setOnClickListener(v -> openCalibration());
        }
    }

    private void openCalibration() {
        startActivity(new Intent(this, CalibrationActivity.class));
    }

    private void populateDemoResult() {
        // Ensure DEMO banner and DEMO badge are visible
        if (demoNoticeBanner != null)  demoNoticeBanner.setVisibility(View.VISIBLE);
        if (tvResultDemoBadge != null) tvResultDemoBadge.setVisibility(View.VISIBLE);
        if (tvResultCardTitle != null) tvResultCardTitle.setText("Estimated Exposure");

        ExposureRecord record = MockDataHelper.getMockCurrentExposure();
        tvExposureValue.setText("0.8");
        tvExposureValue.setTextColor(getColor(R.color.colorAccent));

        // Restore the DEMO units label
        if (tvExposureUnits != null) {
            tvExposureUnits.setText("ppm.hr [ESTIMATED - DEMO]");
            tvExposureUnits.setTextColor(0x99FFFFFF); // semi-transparent white
        }

        tvScanDate.setText(record.getDate());
        tvScanTime.setText(record.getTime());
        tvScanShift.setText(record.getShiftName());
        tvScanBadgeId.setText(record.getBadgeId());
        tvImageQuality.setText(record.getImageQuality() + " [DEMO]");
        tvImageQuality.setTextColor(getColor(R.color.colorStatusSafe));

        switch (record.getCategory()) {
            case HIGH:
                applyCategory(getString(R.string.exposure_category_high),
                        R.color.colorStatusDanger, R.drawable.bg_status_high,
                        R.drawable.ic_warning, R.color.colorStatusDanger,
                        getString(R.string.exposure_high_desc));
                break;
            case ELEVATED:
                applyCategory(getString(R.string.exposure_category_elevated),
                        R.color.colorStatusWarning, R.drawable.bg_status_elevated,
                        R.drawable.ic_warning, R.color.colorStatusWarning,
                        getString(R.string.exposure_elevated_desc));
                break;
            default:
                applyCategory(getString(R.string.exposure_category_low),
                        R.color.colorStatusSafe, R.drawable.bg_status_low,
                        R.drawable.ic_verified, R.color.colorStatusSafe,
                        getString(R.string.exposure_low_desc));
                break;
        }
    }

    private void applyCategory(String label, int textColor, int bgRes,
                                int iconRes, int iconTint, String desc) {
        tvExposureCategory.setText(label);
        tvExposureCategory.setTextColor(getColor(textColor));
        tvExposureCategory.setBackgroundResource(bgRes);
        ivCategoryIcon.setImageResource(iconRes);
        ivCategoryIcon.setColorFilter(getColor(iconTint));
        tvCategoryDescription.setText(desc);
    }

    // ------------------------------------------------------------------
    // Scan result layout: add demo notice banner ID support
    // ------------------------------------------------------------------

    private void setupClickListeners() {
        findViewById(R.id.btnViewDetail).setOnClickListener(v -> launchDetail());
        findViewById(R.id.btnBackToDashboard).setOnClickListener(v -> backToDashboard());
    }

    private void launchDetail() {
        Intent intent = new Intent(this, ExposureDetailActivity.class);
        intent.putExtra(EXTRA_RECORD_ID, "REC-001");
        if (imageUriString != null) {
            intent.putExtra(CaptureActivity.EXTRA_IMAGE_URI, imageUriString);
        }
        if (analysisResult != null) {
            intent.putExtra(ProcessingActivity.EXTRA_ANALYSIS_RESULT, analysisResult);
        }
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void backToDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left);
        finish();
    }

    @Override
    public void onBackPressed() {
        backToDashboard();
    }
}
