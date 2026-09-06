package com.ankita.h2sdosimeter.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.adapter.ExposureRecordAdapter;
import com.ankita.h2sdosimeter.api.ApiConfig;
import com.ankita.h2sdosimeter.api.BackendSyncService;
import com.ankita.h2sdosimeter.model.Badge;
import com.ankita.h2sdosimeter.model.ExposureRecord;
import com.ankita.h2sdosimeter.model.Shift;
import com.ankita.h2sdosimeter.model.Worker;
import com.ankita.h2sdosimeter.ui.scan.ScanActivity;
import com.ankita.h2sdosimeter.util.MockDataHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * DashboardActivity - Worker Home screen.
 *
 * Displays mock exposure status, shift info, quick actions.
 * All data labelled DEMO - not real H2S measurements.
 */
public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    public static final String EXTRA_ROLE = "extra_role";

    // -- Views -----------------------------------------
    private TextView tvGreeting;
    private TextView tvWorkerName;
    private TextView tvWorkerId;
    private TextView tvAvatar;
    private ExposureGaugeView exposureGauge;
    private TextView tvExposureCategory;
    private TextView tvCategoryDesc;
    private TextView tvCurrentShift;
    private TextView tvBadgeStatusSmall;
    private TextView tvEstimatedExposure;
    private TextView tvShiftName;
    private TextView tvShiftStart;
    private TextView tvShiftEnd;
    private TextView tvBadgeId;
    private TextView tvBadgeValidity;
    private RecyclerView rvRecentRecords;

    // -- Quick action containers ------------------------
    private View btnQuickScan;
    private View btnQuickHistory;
    private View btnQuickShift;
    private View btnViewAllHistory;
    private View btnNotification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        bindViews();
        populateMockData();           // Always show mock data immediately
        setupClickListeners();
        animateEntrance();

        // Overlay real backend data on top of mock if available
        if (ApiConfig.BACKEND_SYNC_ENABLED && ApiConfig.LOAD_DASHBOARD_FROM_BACKEND) {
            loadBackendExposureData();
        }
    }

    // -- View binding ----------------------------------

    private void bindViews() {
        tvGreeting          = findViewById(R.id.tvGreeting);
        tvWorkerName        = findViewById(R.id.tvWorkerName);
        tvWorkerId          = findViewById(R.id.tvWorkerId);
        tvAvatar            = findViewById(R.id.tvAvatar);
        exposureGauge       = findViewById(R.id.exposureGauge);
        tvExposureCategory  = findViewById(R.id.tvExposureCategory);
        tvCategoryDesc      = findViewById(R.id.tvCategoryDesc);
        tvCurrentShift      = findViewById(R.id.tvCurrentShift);
        tvBadgeStatusSmall  = findViewById(R.id.tvBadgeStatusSmall);
        tvEstimatedExposure = findViewById(R.id.tvEstimatedExposure);
        tvShiftName         = findViewById(R.id.tvShiftName);
        tvShiftStart        = findViewById(R.id.tvShiftStart);
        tvShiftEnd          = findViewById(R.id.tvShiftEnd);
        tvBadgeId           = findViewById(R.id.tvBadgeId);
        tvBadgeValidity     = findViewById(R.id.tvBadgeValidity);
        rvRecentRecords     = findViewById(R.id.rvRecentRecords);
        btnQuickScan        = findViewById(R.id.btnQuickScan);
        btnQuickHistory     = findViewById(R.id.btnQuickHistory);
        btnQuickShift       = findViewById(R.id.btnQuickShift);
        btnViewAllHistory   = findViewById(R.id.btnViewAllHistory);
        btnNotification     = findViewById(R.id.btnNotification);
    }

    // -- Mock data population --------------------------

    private void populateMockData() {
        Worker worker = MockDataHelper.getMockWorker();
        Shift shift   = MockDataHelper.getCurrentMockShift();
        Badge badge   = MockDataHelper.getMockBadge();
        ExposureRecord current = MockDataHelper.getMockCurrentExposure();

        // -- Header --
        tvGreeting.setText(getGreeting());
        tvWorkerName.setText(worker.getName());
        tvWorkerId.setText(worker.getWorkerId());
        tvAvatar.setText(worker.getInitials());

        // -- Gauge + exposure status --
        ExposureGaugeView.Level gaugeLevel = mapCategory(current.getCategory());
        float gaugeFraction = gaugeFromCategory(current.getCategory());
        exposureGauge.setExposureLevel(gaugeFraction, gaugeLevel);

        // Category label + description
        switch (current.getCategory()) {
            case HIGH:
                tvExposureCategory.setText(getString(R.string.exposure_category_high));
                tvExposureCategory.setTextColor(getColor(R.color.colorStatusDanger));
                tvExposureCategory.setBackgroundResource(R.drawable.bg_status_high);
                tvCategoryDesc.setText(getString(R.string.exposure_high_desc));
                break;
            case ELEVATED:
                tvExposureCategory.setText(getString(R.string.exposure_category_elevated));
                tvExposureCategory.setTextColor(getColor(R.color.colorStatusWarning));
                tvExposureCategory.setBackgroundResource(R.drawable.bg_status_elevated);
                tvCategoryDesc.setText(getString(R.string.exposure_elevated_desc));
                break;
            default:
                tvExposureCategory.setText(getString(R.string.exposure_category_low));
                tvExposureCategory.setTextColor(getColor(R.color.colorStatusSafe));
                tvExposureCategory.setBackgroundResource(R.drawable.bg_status_low);
                tvCategoryDesc.setText(getString(R.string.exposure_low_desc));
                break;
        }

        tvCurrentShift.setText(shift.getShiftName());
        tvEstimatedExposure.setText(current.getEstimatedExposureDisplay());

        // Badge status (small inline)
        if (badge.getStatus() == Badge.BadgeStatus.VALID) {
            tvBadgeStatusSmall.setText(getString(R.string.lbl_badge_valid) + " (check)");
            tvBadgeStatusSmall.setTextColor(getColor(R.color.colorBadgeValid));
            tvBadgeStatusSmall.setBackgroundResource(R.drawable.bg_badge_valid);
        } else {
            tvBadgeStatusSmall.setText(getString(R.string.lbl_badge_expired) + " (x)");
            tvBadgeStatusSmall.setTextColor(getColor(R.color.colorBadgeExpired));
            tvBadgeStatusSmall.setBackgroundResource(R.drawable.bg_badge_expired);
        }

        // -- Shift card --
        tvShiftName.setText(shift.getShiftName());
        tvShiftStart.setText(shift.getStartTime());
        tvShiftEnd.setText(shift.getEndTime());
        tvBadgeId.setText(badge.getBadgeId());

        if (badge.getStatus() == Badge.BadgeStatus.VALID) {
            tvBadgeValidity.setText(getString(R.string.lbl_badge_valid) + " (check)");
            tvBadgeValidity.setTextColor(getColor(R.color.colorBadgeValid));
            tvBadgeValidity.setBackgroundResource(R.drawable.bg_badge_valid);
        } else {
            tvBadgeValidity.setText(getString(R.string.lbl_badge_expired) + " (x)");
            tvBadgeValidity.setTextColor(getColor(R.color.colorBadgeExpired));
            tvBadgeValidity.setBackgroundResource(R.drawable.bg_badge_expired);
        }

        // -- Recent records list (show 3 most recent) --
        List<ExposureRecord> history = MockDataHelper.getMockExposureHistory();
        List<ExposureRecord> recent = history.size() > 3 ? history.subList(0, 3) : history;

        ExposureRecordAdapter adapter = new ExposureRecordAdapter(this, recent);
        rvRecentRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecentRecords.setAdapter(adapter);
        rvRecentRecords.setNestedScrollingEnabled(false);
    }

    // -- Click listeners --------------------------------

    private void setupClickListeners() {

        btnQuickScan.setOnClickListener(v -> launchScan());

        btnQuickHistory.setOnClickListener(v ->
                showStub("Exposure History - coming in Phase 2"));

        btnQuickShift.setOnClickListener(v ->
                showStub("Shift Details - coming in Phase 2"));

        btnViewAllHistory.setOnClickListener(v ->
                showStub("Exposure History - coming in Phase 2"));

        btnNotification.setOnClickListener(v ->
                showStub("Notifications - coming in Phase 2"));

        tvAvatar.setOnClickListener(v ->
                showStub("Profile - coming in Phase 2"));
    }

    private void launchScan() {
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void showStub(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // -- Backend data loading ---------------------------

    /**
     * Fetches today's real exposure summary from the backend and updates the
     * gauge + estimated exposure field. Falls back to mock data silently.
     */
    private void loadBackendExposureData() {
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        // Load today's exposure summary
        BackendSyncService.getTodayExposure(ApiConfig.CURRENT_WORKER_ID,
                new BackendSyncService.SyncCallback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        mainHandler.post(() -> {
                            try {
                                JSONObject data = body.optJSONObject("data");
                                if (data == null) return;

                                int scanCount       = data.optInt("scanCount", 0);
                                double totalPpmHr   = data.optDouble("totalEstimatedPpmHr", 0);
                                String calibStatus  = data.optString("calibrationStatus", "UNCALIBRATED");
                                boolean calibrated  = "CALIBRATED".equals(calibStatus)
                                        || "PARTIAL".equals(calibStatus);

                                if (scanCount == 0) {
                                    // No scans today — keep mock display
                                    Log.d(TAG, "No scans today from backend");
                                    return;
                                }

                                // Update exposure display with real data
                                String exposureLabel;
                                if (calibrated) {
                                    exposureLabel = String.format("%.3f ppm.hr [REAL·%d scan%s]",
                                            totalPpmHr, scanCount, scanCount == 1 ? "" : "s");
                                } else {
                                    exposureLabel = scanCount + " scan"
                                            + (scanCount == 1 ? "" : "s") + " today [CALIB REQUIRED]";
                                }
                                tvEstimatedExposure.setText(exposureLabel);

                                // Update gauge fraction based on real total
                                float fraction = (float) Math.min(totalPpmHr / 10.0, 1.0);
                                ExposureGaugeView.Level level;
                                if (totalPpmHr < 1.0) level = ExposureGaugeView.Level.LOW;
                                else if (totalPpmHr < 5.0) level = ExposureGaugeView.Level.ELEVATED;
                                else level = ExposureGaugeView.Level.HIGH;
                                exposureGauge.setExposureLevel(fraction, level);

                                Log.d(TAG, "Backend today: " + scanCount + " scans, "
                                        + totalPpmHr + " ppm.hr total");
                            } catch (Exception e) {
                                Log.w(TAG, "Error parsing today summary", e);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Could not load today exposure from backend: " + message);
                        // Keep mock data — no user-visible error
                    }
                });

        // Load real scan history (last 3 scans)
        BackendSyncService.getExposureHistory(ApiConfig.CURRENT_WORKER_ID,
                new BackendSyncService.SyncCallback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        mainHandler.post(() -> {
                            try {
                                JSONArray dataArr = body.optJSONArray("data");
                                if (dataArr == null || dataArr.length() == 0) return;

                                List<ExposureRecord> realRecords = new ArrayList<>();
                                int limit = Math.min(dataArr.length(), 3);
                                for (int i = 0; i < limit; i++) {
                                    JSONObject s = dataArr.getJSONObject(i);
                                    String date       = s.optString("scanDate", "");
                                    String time       = s.optString("scanTimestamp", "").substring(11, 16);
                                    String shift      = s.optString("shiftName", "Shift");
                                    String badge      = s.optString("badgeId", "");
                                    String calibSt    = s.optString("calibrationStatus", "UNCALIBRATED");
                                    double ppm        = s.optDouble("estimatedPpmHr", 0);
                                    String category   = s.optString("exposureCategory", "UNKNOWN");

                                    String displayVal;
                                    if ("UNCALIBRATED".equals(calibSt)) {
                                        double diff = s.optDouble("colourDifference", 0);
                                        displayVal = String.format("Δ%.1f [CALIB REQUIRED]", diff);
                                    } else {
                                        displayVal = String.format("%.3f ppm.hr [REAL]", ppm);
                                    }

                                    ExposureRecord.ExposureCategory cat;
                                    try { cat = ExposureRecord.ExposureCategory.valueOf(category); }
                                    catch (Exception ex) { cat = ExposureRecord.ExposureCategory.UNKNOWN; }

                                    realRecords.add(new ExposureRecord(
                                            "REAL-" + s.optInt("id"),
                                            date, time, shift, badge,
                                            displayVal, cat,
                                            Badge.BadgeStatus.VALID));
                                }

                                if (!realRecords.isEmpty()) {
                                    ExposureRecordAdapter adapter =
                                            new ExposureRecordAdapter(DashboardActivity.this, realRecords);
                                    rvRecentRecords.setLayoutManager(new LinearLayoutManager(DashboardActivity.this));
                                    rvRecentRecords.setAdapter(adapter);
                                    Log.d(TAG, "Dashboard history updated with " + realRecords.size()
                                            + " real backend records");
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error parsing history", e);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Could not load history from backend: " + message);
                    }
                });
    }

    // -- Entrance animation -----------------------------

    private void animateEntrance() {
        View header = findViewById(R.id.dashHeader);
        if (header != null) {
            header.setAlpha(0f);
            header.animate().alpha(1f).setDuration(400).start();
        }
    }

    // -- Helpers ----------------------------------------

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String base;
        if (hour < 12) {
            base = getString(R.string.dashboard_greeting_morning);
        } else if (hour < 17) {
            base = getString(R.string.dashboard_greeting_afternoon);
        } else {
            base = getString(R.string.dashboard_greeting_evening);
        }
        return base + ",";
    }

    private ExposureGaugeView.Level mapCategory(ExposureRecord.ExposureCategory cat) {
        switch (cat) {
            case HIGH:     return ExposureGaugeView.Level.HIGH;
            case ELEVATED: return ExposureGaugeView.Level.ELEVATED;
            default:       return ExposureGaugeView.Level.LOW;
        }
    }

    /**
     * Maps category to a demo gauge fill fraction (0-1).
     * These fractions are DEMO values for visual representation only.
     */
    private float gaugeFromCategory(ExposureRecord.ExposureCategory cat) {
        switch (cat) {
            case HIGH:     return 0.85f;
            case ELEVATED: return 0.55f;
            default:       return 0.25f;
        }
    }
}
