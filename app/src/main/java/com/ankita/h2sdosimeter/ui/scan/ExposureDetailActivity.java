package com.ankita.h2sdosimeter.ui.scan;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.calibration.CalibrationActivity;
import com.ankita.h2sdosimeter.calibration.CalibrationCurve;
import com.ankita.h2sdosimeter.calibration.CalibrationPoint;
import com.ankita.h2sdosimeter.calibration.CalibrationStore;
import com.ankita.h2sdosimeter.calibration.DemoCalibrationDataset;
import com.ankita.h2sdosimeter.model.Badge;
import com.ankita.h2sdosimeter.model.ColourAnalysisResult;
import com.ankita.h2sdosimeter.model.ExposureRecord;
import com.ankita.h2sdosimeter.ui.dashboard.DashboardActivity;
import com.ankita.h2sdosimeter.util.MockDataHelper;

import java.util.List;
import java.util.Locale;

/**
 * ExposureDetailActivity - Phase 4: Full detail for one exposure record.
 *
 * REAL capture: shows the complete colour analysis breakdown -
 *   raw RGB, reference RGB, corrected RGB, colour difference,
 *   image quality, sharpness, brightness, analysis status.
 *   Exposure marked as "Calibration required" - no validated
 *   ppm.hr conversion exists in this build.
 *
 * DEMO capture: shows mock data exactly as before.
 */
public class ExposureDetailActivity extends AppCompatActivity {

    // -- Standard detail fields
    private TextView tvRecordId;
    private TextView tvRecordDate;
    private TextView tvExposureValue;
    private TextView tvCategory;
    private TextView tvCategoryDesc;
    private TextView tvDetailShift;
    private TextView tvDetailBadgeId;
    private TextView tvDetailBadgeStatus;
    private TextView tvDetailImageQuality;
    private TextView tvLightingCorrection;

    // -- Colour analysis section (shown only for real captures)
    private View colourAnalysisSection;
    private TextView tvRawRgb;
    private TextView tvReferenceRgb;
    private TextView tvCorrectedRgb;
    private TextView tvColourDiff;
    private TextView tvBrightness;
    private TextView tvSharpness;
    private TextView tvAnalysisStatus;
    private TextView tvCalibrationNote;

    // -- Demo notice banner
    private View demoNoticeBanner;

    private ColourAnalysisResult analysisResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exposure_detail);

        analysisResult = (ColourAnalysisResult)
                getIntent().getSerializableExtra(ProcessingActivity.EXTRA_ANALYSIS_RESULT);

        setupToolbar();
        bindViews();
        populateRecord();
        setupClickListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Exposure Detail");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        tvRecordId           = findViewById(R.id.tvRecordId);
        tvRecordDate         = findViewById(R.id.tvRecordDate);
        tvExposureValue      = findViewById(R.id.tvExposureValue);
        tvCategory           = findViewById(R.id.tvCategory);
        tvCategoryDesc       = findViewById(R.id.tvCategoryDesc);
        tvDetailShift        = findViewById(R.id.tvDetailShift);
        tvDetailBadgeId      = findViewById(R.id.tvDetailBadgeId);
        tvDetailBadgeStatus  = findViewById(R.id.tvDetailBadgeStatus);
        tvDetailImageQuality = findViewById(R.id.tvDetailImageQuality);
        tvLightingCorrection = findViewById(R.id.tvLightingCorrection);
        demoNoticeBanner     = findViewById(R.id.demoNoticeBannerDetail);

        // Colour analysis section
        colourAnalysisSection = findViewById(R.id.colourAnalysisSection);
        tvRawRgb              = findViewById(R.id.tvRawRgb);
        tvReferenceRgb        = findViewById(R.id.tvReferenceRgb);
        tvCorrectedRgb        = findViewById(R.id.tvCorrectedRgb);
        tvColourDiff          = findViewById(R.id.tvColourDiff);
        tvBrightness          = findViewById(R.id.tvBrightness);
        tvSharpness           = findViewById(R.id.tvSharpness);
        tvAnalysisStatus      = findViewById(R.id.tvAnalysisStatus);
        tvCalibrationNote     = findViewById(R.id.tvCalibrationNote);
    }

    private void populateRecord() {
        boolean isReal = analysisResult != null && analysisResult.isRealCapture();

        if (isReal) {
            populateRealDetail(analysisResult);
        } else {
            populateDemoDetail();
        }
    }

    // ------------------------------------------------------------------
    // Real capture detail
    // ------------------------------------------------------------------

    private void populateRealDetail(ColourAnalysisResult result) {
        if (demoNoticeBanner != null) demoNoticeBanner.setVisibility(View.GONE);

        tvRecordId.setText("REAL-" + System.currentTimeMillis() % 10000);
        tvRecordDate.setText("Real Capture");

        tvDetailShift.setText(MockDataHelper.getCurrentMockShift().getShiftName());
        tvDetailBadgeId.setText(MockDataHelper.getMockBadge().getBadgeId());
        tvDetailBadgeStatus.setText(getString(R.string.lbl_badge_valid) + " (check)");
        tvDetailBadgeStatus.setTextColor(getColor(R.color.colorBadgeValid));
        tvDetailBadgeStatus.setBackgroundResource(R.drawable.bg_badge_valid);

        tvDetailImageQuality.setText(result.getImageQualityLabel());

        // Show lighting correction status based on actual scan mode
        boolean refApplied = result.isReferenceScaleDetected();
        if (result.getStatus() == ColourAnalysisResult.Status.SUCCESS) {
            tvLightingCorrection.setText(refApplied
                    ? "Applied (reference correction)"
                    : "⚠ Not applied — reference scale not detected");
            tvLightingCorrection.setTextColor(getColor(
                    refApplied ? R.color.colorStatusSafe : R.color.colorStatusWarning));
        } else {
            tvLightingCorrection.setText("Not applied");
            tvLightingCorrection.setTextColor(getColor(R.color.colorTextSecondary));
        }

        if (result.getStatus() == ColourAnalysisResult.Status.SUCCESS) {
            String deStr  = String.format(Locale.US, "%.2f", result.getDeltaE());
            String rgbStr = String.format(Locale.US, "%.1f", result.getColourDifference());
            // refApplied already declared above — reuse it here

            // PRIMARY: use deltaE as the calibration input
            List<CalibrationPoint> calibPoints = CalibrationStore.loadPoints(this);
            CalibrationCurve.ConversionResult conversion =
                    CalibrationCurve.convert(result.getDeltaE(), calibPoints);

            boolean isSynthetic = DemoCalibrationDataset.isSyntheticDataActive(this);
            String modeNote = refApplied
                    ? "\nReference correction: Applied"
                    : "\n⚠ Reference colour scale not detected\nResult is approximate — lighting correction not applied.";

            if (conversion.success) {
                String ppmStr = String.format(Locale.US, "%.3f ppm·hr", conversion.estimatedPpmHr);
                String confLabel = conversion.confidence == CalibrationCurve.Confidence.HIGH
                        ? "[ESTIMATE — interpolated]"
                        : "[ESTIMATE — extrapolated, lower confidence]";
                String syntheticNote = isSynthetic ? "\n[SYNTHETIC — NOT VALIDATED]" : "";
                tvExposureValue.setText(ppmStr + "\n" + confLabel + syntheticNote
                        + "\nΔE₀₀=" + deStr + "  |  RGB diff=" + rgbStr + "/441"
                        + modeNote);
                tvExposureValue.setTextColor(getColor(
                        (isSynthetic || !refApplied)
                                ? R.color.colorStatusWarning : R.color.colorAccent));
            } else {
                tvExposureValue.setText("ΔE₀₀=" + deStr + "\n[CALIBRATION REQUIRED]\n"
                        + conversion.message + modeNote);
                tvExposureValue.setTextColor(getColor(R.color.colorStatusWarning));
            }

            double de = result.getDeltaE();
            if (de < 5.0) {
                tvCategory.setText("MINIMAL CHANGE");
                tvCategory.setTextColor(getColor(R.color.colorStatusSafe));
                tvCategory.setBackgroundResource(R.drawable.bg_status_low);
                tvCategoryDesc.setText("Sensor shows minimal colour change (ΔE₀₀ < 5).");
            } else if (de < 20.0) {
                tvCategory.setText("MODERATE CHANGE");
                tvCategory.setTextColor(getColor(R.color.colorStatusWarning));
                tvCategory.setBackgroundResource(R.drawable.bg_status_elevated);
                tvCategoryDesc.setText("Sensor shows moderate colour change (ΔE₀₀ 5–20).");
            } else {
                tvCategory.setText("SIGNIFICANT CHANGE");
                tvCategory.setTextColor(getColor(R.color.colorStatusDanger));
                tvCategory.setBackgroundResource(R.drawable.bg_status_high);
                tvCategoryDesc.setText("Sensor shows significant colour change (ΔE₀₀ > 20).");
            }
        } else {
            tvExposureValue.setText("Analysis unavailable");
            tvCategory.setText(result.getStatus().name());
            tvCategoryDesc.setText(result.getStatusMessage());
        }

        // Colour analysis section
        if (colourAnalysisSection != null) {
            colourAnalysisSection.setVisibility(View.VISIBLE);
            tvRawRgb.setText(result.getRawRgbString());
            tvReferenceRgb.setText(result.getReferenceRgbString());
            tvCorrectedRgb.setText(result.getCorrectedRgbString());
            // Show ΔE₀₀ as primary metric, RGB diff as secondary
            tvColourDiff.setText(String.format(Locale.US,
                    "ΔE₀₀=%.3f  |  RGB dist=%.2f/441", result.getDeltaE(), result.getColourDifference()));
            // Show CIE Lab
            tvBrightness.setText(result.getLabString()
                    + String.format(Locale.US, "  (brightness=%.1f)", result.getBrightness()));
            tvSharpness.setText(String.format(Locale.US, "%.1f (Laplacian var.)", result.getSharpness()));
            tvAnalysisStatus.setText(result.getStatus().name() + ": " + result.getStatusMessage());
            if (tvCalibrationNote != null) {
                List<CalibrationPoint> pts = CalibrationStore.loadPoints(this);
                boolean cal = CalibrationStore.isCalibrated(this);
                boolean isSynthetic = DemoCalibrationDataset.isSyntheticDataActive(this);
                boolean refAppliedNote = result.isReferenceScaleDetected();
                String modeStr = refAppliedNote
                        ? "Scan mode: MODE 1 — reference correction applied."
                        : "Scan mode: MODE 2 — strip only. "
                          + "Reference colour scale not detected. "
                          + "No lighting correction was applied. "
                          + "Result is approximate and lower confidence.";
                String calibStr;
                if (!cal) {
                    calibStr = "CALIBRATION REQUIRED: ΔE₀₀ cannot be converted to ppm·hr until "
                            + "at least " + CalibrationStore.MIN_POINTS_FOR_CONVERSION
                            + " calibration reference points have been added.";
                } else if (isSynthetic) {
                    calibStr = "⚠ SYNTHETIC CALIBRATION ACTIVE — NOT LABORATORY VALIDATED\n"
                            + CalibrationCurve.rangeSummary(pts) + "\n"
                            + "These points were auto-seeded for software testing only.";
                } else {
                    calibStr = "Calibration active: " + CalibrationCurve.rangeSummary(pts)
                            + "\nEstimates depend on the quality of reference measurements.";
                }
                tvCalibrationNote.setText(modeStr + "\n" + calibStr);
                tvCalibrationNote.setTextColor((!cal || isSynthetic || !refAppliedNote)
                        ? getColor(R.color.colorStatusWarning)
                        : getColor(R.color.colorStatusSafe));
            }
        }
    }

    // ------------------------------------------------------------------
    // Demo capture detail (unchanged from Phase 2/3)
    // ------------------------------------------------------------------

    private void populateDemoDetail() {
        if (demoNoticeBanner != null) demoNoticeBanner.setVisibility(View.VISIBLE);
        if (colourAnalysisSection != null) colourAnalysisSection.setVisibility(View.GONE);

        ExposureRecord record = MockDataHelper.getMockCurrentExposure();
        tvRecordId.setText(record.getRecordId());
        tvRecordDate.setText(record.getDate() + " - " + record.getTime());
        tvExposureValue.setText(record.getEstimatedExposureDisplay());
        tvDetailShift.setText(record.getShiftName());
        tvDetailBadgeId.setText(record.getBadgeId());
        tvDetailImageQuality.setText(record.getImageQuality());
        tvLightingCorrection.setText(record.isLightingCorrectionApplied() ? "Applied" : "Not applied");

        if (record.getBadgeStatus() == Badge.BadgeStatus.VALID) {
            tvDetailBadgeStatus.setText(getString(R.string.lbl_badge_valid) + " (check)");
            tvDetailBadgeStatus.setTextColor(getColor(R.color.colorBadgeValid));
            tvDetailBadgeStatus.setBackgroundResource(R.drawable.bg_badge_valid);
        } else {
            tvDetailBadgeStatus.setText(getString(R.string.lbl_badge_expired) + " (x)");
            tvDetailBadgeStatus.setTextColor(getColor(R.color.colorBadgeExpired));
            tvDetailBadgeStatus.setBackgroundResource(R.drawable.bg_badge_expired);
        }

        switch (record.getCategory()) {
            case HIGH:
                tvCategory.setText(getString(R.string.exposure_category_high));
                tvCategory.setTextColor(getColor(R.color.colorStatusDanger));
                tvCategory.setBackgroundResource(R.drawable.bg_status_high);
                tvCategoryDesc.setText(getString(R.string.exposure_high_desc));
                break;
            case ELEVATED:
                tvCategory.setText(getString(R.string.exposure_category_elevated));
                tvCategory.setTextColor(getColor(R.color.colorStatusWarning));
                tvCategory.setBackgroundResource(R.drawable.bg_status_elevated);
                tvCategoryDesc.setText(getString(R.string.exposure_elevated_desc));
                break;
            default:
                tvCategory.setText(getString(R.string.exposure_category_low));
                tvCategory.setTextColor(getColor(R.color.colorStatusSafe));
                tvCategory.setBackgroundResource(R.drawable.bg_status_low);
                tvCategoryDesc.setText(getString(R.string.exposure_low_desc));
                break;
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btnBackToDashboard).setOnClickListener(v -> backToDashboard());
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
        finish();
    }
}
