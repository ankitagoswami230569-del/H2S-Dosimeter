package com.ankita.h2sdosimeter.ui.scan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.model.Badge;
import com.ankita.h2sdosimeter.util.MockDataHelper;

/**
 * ScanActivity - Entry screen for wristband scanning.
 *
 * Shows badge details, pre-scan checklist, and a button to
 * launch the camera capture screen.
 *
 * Phase 2: All badge data is DEMO/MOCK only.
 */
public class ScanActivity extends AppCompatActivity {

    private TextView tvBadgeId;
    private TextView tvBadgeStatus;
    private TextView tvBadgeExpiry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        setupToolbar();
        bindViews();
        populateBadgeInfo();
        setupClickListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scan Wristband");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        tvBadgeId     = findViewById(R.id.tvBadgeId);
        tvBadgeStatus = findViewById(R.id.tvBadgeStatus);
        tvBadgeExpiry = findViewById(R.id.tvBadgeExpiry);
    }

    private void populateBadgeInfo() {
        Badge badge = MockDataHelper.getMockBadge();

        tvBadgeId.setText(badge.getBadgeId());
        tvBadgeExpiry.setText(badge.getExpiryDate());

        if (badge.getStatus() == Badge.BadgeStatus.VALID) {
            tvBadgeStatus.setText(getString(R.string.lbl_badge_valid) + " (check)");
            tvBadgeStatus.setTextColor(getColor(R.color.colorBadgeValid));
            tvBadgeStatus.setBackgroundResource(R.drawable.bg_badge_valid);
        } else {
            tvBadgeStatus.setText(getString(R.string.lbl_badge_expired) + " (x)");
            tvBadgeStatus.setTextColor(getColor(R.color.colorBadgeExpired));
            tvBadgeStatus.setBackgroundResource(R.drawable.bg_badge_expired);
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btnStartCapture).setOnClickListener(v -> launchCapture());
    }

    private void launchCapture() {
        Intent intent = new Intent(this, CaptureActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
