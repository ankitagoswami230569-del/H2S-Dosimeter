package com.ankita.h2sdosimeter.calibration;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.api.ApiConfig;
import com.ankita.h2sdosimeter.api.BackendSyncService;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CalibrationActivity - lets the user enter, view and delete calibration
 * reference points that map colourDifference values to known ppm.hr exposures.
 *
 * HOW TO USE THIS SCREEN:
 * 1. Expose a badge to a known H2S concentration for a known duration
 *    under controlled / laboratory conditions.
 * 2. Scan the badge with this app to get its colourDifference value.
 * 3. On this screen, enter that colourDifference and the known ppm.hr.
 * 4. Repeat for at least two different exposure levels.
 * 5. Subsequent real scans will use these points to convert
 *    colourDifference → estimated ppm.hr.
 *
 * SAFETY WARNING:
 * The quality of ppm.hr estimates depends entirely on the validity of the
 * reference points entered here. Only enter values confirmed by a calibrated
 * industrial detector or laboratory analysis.
 */
public class CalibrationActivity extends AppCompatActivity {

    private static final String TAG = "CalibrationActivity";

    private RecyclerView rvCalibrationPoints;
    private TextView     tvStatus;
    private TextView     tvRangeSummary;
    private TextView     tvServerStatus;
    private MaterialButton btnAddPoint;
    private MaterialButton btnClearAll;
    private MaterialButton btnSyncToServer;
    private MaterialButton btnLoadFromServer;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PointsAdapter adapter;
    private List<CalibrationPoint> points = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        setupToolbar();
        bindViews();
        setupRecyclerView();
        setupClickListeners();
        refreshPoints();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPoints();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Sensor Calibration");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        rvCalibrationPoints = findViewById(R.id.rvCalibrationPoints);
        tvStatus            = findViewById(R.id.tvCalibStatus);
        tvRangeSummary      = findViewById(R.id.tvCalibRangeSummary);
        tvServerStatus      = findViewById(R.id.tvServerCalibStatus);
        btnAddPoint         = findViewById(R.id.btnAddCalibPoint);
        btnClearAll         = findViewById(R.id.btnClearCalib);
        btnSyncToServer     = findViewById(R.id.btnSyncToServer);
        btnLoadFromServer   = findViewById(R.id.btnLoadFromServer);
    }

    private void setupRecyclerView() {
        adapter = new PointsAdapter(points, this::onDeletePoint);
        rvCalibrationPoints.setLayoutManager(new LinearLayoutManager(this));
        rvCalibrationPoints.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnAddPoint.setOnClickListener(v -> showAddPointDialog());
        btnClearAll.setOnClickListener(v -> confirmClearAll());
        if (btnSyncToServer != null) {
            btnSyncToServer.setOnClickListener(v -> syncLocalPointsToServer());
        }
        if (btnLoadFromServer != null) {
            btnLoadFromServer.setOnClickListener(v -> loadPointsFromServer());
        }
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void refreshPoints() {
        points.clear();
        points.addAll(CalibrationStore.loadPoints(this));
        adapter.notifyDataSetChanged();
        updateStatusUi();
    }

    private void updateStatusUi() {
        int count = points.size();
        int required = CalibrationStore.MIN_POINTS_FOR_CONVERSION;

        if (count >= required) {
            tvStatus.setText("✓ Calibration active (" + count + " point"
                    + (count == 1 ? "" : "s") + ")");
            tvStatus.setTextColor(getColor(R.color.colorStatusSafe));
        } else {
            int needed = required - count;
            tvStatus.setText("⚠ CALIBRATION REQUIRED — add "
                    + needed + " more point" + (needed == 1 ? "" : "s")
                    + " (have " + count + "/" + required + ")");
            tvStatus.setTextColor(getColor(R.color.colorStatusWarning));
        }

        tvRangeSummary.setText(CalibrationCurve.rangeSummary(points));
        btnClearAll.setEnabled(count > 0);
        if (btnSyncToServer != null) btnSyncToServer.setEnabled(count > 0);

        // Refresh server calibration status
        if (tvServerStatus != null) {
            checkServerCalibrationStatus();
        }
    }

    /** Queries /api/calibration/status and updates the server status label. */
    private void checkServerCalibrationStatus() {
        if (!ApiConfig.BACKEND_SYNC_ENABLED) {
            tvServerStatus.setText("Server: backend sync disabled");
            tvServerStatus.setTextColor(getColor(R.color.colorTextHint));
            return;
        }
        tvServerStatus.setText("Server: checking...");
        tvServerStatus.setTextColor(getColor(R.color.colorTextHint));

        BackendSyncService.getCalibrationStatus(new BackendSyncService.SyncCallback() {
            @Override
            public void onSuccess(JSONObject body) {
                mainHandler.post(() -> {
                    try {
                        JSONObject data = body.optJSONObject("data");
                        if (data != null) {
                            boolean cal = data.optBoolean("calibrated", false);
                            int pts     = data.optInt("pointCount", 0);
                            String msg  = cal
                                    ? "Server: ✓ " + pts + " calibration point" + (pts == 1 ? "" : "s") + " active"
                                    : "Server: ⚠ " + pts + "/" + data.optInt("minRequired", 2) + " points (needs more)";
                            tvServerStatus.setText(msg);
                            tvServerStatus.setTextColor(cal
                                    ? getColor(R.color.colorStatusSafe)
                                    : getColor(R.color.colorStatusWarning));
                        }
                    } catch (Exception e) {
                        tvServerStatus.setText("Server: parse error");
                    }
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    tvServerStatus.setText("Server: unreachable");
                    tvServerStatus.setTextColor(getColor(R.color.colorTextHint));
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // Backend sync
    // ------------------------------------------------------------------

    /**
     * Pushes all local calibration points to the backend server.
     * This makes them available for server-side calibration of submitted scans.
     */
    private void syncLocalPointsToServer() {
        if (points.isEmpty()) {
            Toast.makeText(this, "No local calibration points to sync", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ApiConfig.BACKEND_SYNC_ENABLED) {
            Toast.makeText(this, "Backend sync is disabled in ApiConfig", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Syncing " + points.size() + " point(s) to server...", Toast.LENGTH_SHORT).show();
        btnSyncToServer.setEnabled(false);

        final int[] remaining = { points.size() };
        final int[] succeeded = { 0 };

        for (CalibrationPoint p : points) {
            BackendSyncService.pushCalibrationPoint(
                    p.getColourDifference(),
                    p.getKnownPpmHr(),
                    p.getLabel(),
                    new BackendSyncService.SyncCallback() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            synchronized (remaining) {
                                succeeded[0]++;
                                remaining[0]--;
                                if (remaining[0] == 0) notifyDone();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            synchronized (remaining) {
                                remaining[0]--;
                                if (remaining[0] == 0) notifyDone();
                            }
                            Log.w(TAG, "Failed to sync point: " + message);
                        }

                        private void notifyDone() {
                            mainHandler.post(() -> {
                                btnSyncToServer.setEnabled(true);
                                String msg = succeeded[0] + "/" + points.size()
                                        + " point(s) synced to server";
                                Toast.makeText(CalibrationActivity.this, msg, Toast.LENGTH_LONG).show();
                                checkServerCalibrationStatus();
                            });
                        }
                    });
        }
    }

    /**
     * Loads active calibration points from the server and imports any
     * that are not already stored locally (by colour difference match).
     */
    private void loadPointsFromServer() {
        if (!ApiConfig.BACKEND_SYNC_ENABLED) {
            Toast.makeText(this, "Backend sync is disabled in ApiConfig", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Loading calibration from server...", Toast.LENGTH_SHORT).show();

        BackendSyncService.getServerCalibrationPoints(new BackendSyncService.SyncCallback() {
            @Override
            public void onSuccess(JSONObject body) {
                mainHandler.post(() -> {
                    try {
                        JSONArray arr = body.optJSONArray("data");
                        if (arr == null || arr.length() == 0) {
                            Toast.makeText(CalibrationActivity.this,
                                    "No calibration points on server", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int imported = 0;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject pt = arr.getJSONObject(i);
                            double diff = pt.getDouble("colourDifference");
                            double ppm  = pt.getDouble("knownPpmHr");
                            String lbl  = pt.optString("label", "From server");

                            // Only import if not already stored locally
                            boolean exists = points.stream()
                                    .anyMatch(p -> Math.abs(p.getColourDifference() - diff) < 0.01);
                            if (!exists) {
                                CalibrationPoint cp = new CalibrationPoint(
                                        diff, ppm, lbl + " [server]",
                                        System.currentTimeMillis());
                                CalibrationStore.addPoint(CalibrationActivity.this, cp);
                                imported++;
                            }
                        }

                        String msg = imported > 0
                                ? "Imported " + imported + " new point(s) from server"
                                : "All server points already present locally";
                        Toast.makeText(CalibrationActivity.this, msg, Toast.LENGTH_LONG).show();
                        refreshPoints();

                    } catch (Exception e) {
                        Toast.makeText(CalibrationActivity.this,
                                "Error parsing server data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() ->
                        Toast.makeText(CalibrationActivity.this,
                                "Could not reach server: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    // ------------------------------------------------------------------
    // Add point dialog
    // ------------------------------------------------------------------

    private void showAddPointDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_calibration_point, null);

        EditText etColourDiff = dialogView.findViewById(R.id.etColourDiff);
        EditText etPpmHr      = dialogView.findViewById(R.id.etPpmHr);
        EditText etLabel      = dialogView.findViewById(R.id.etLabel);

        new AlertDialog.Builder(this)
                .setTitle("Add Calibration Point")
                .setMessage("Enter values from a badge scanned under KNOWN exposure conditions. "
                        + "The colour difference must come from a real scan of that badge.")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String diffStr = etColourDiff.getText().toString().trim();
                    String ppmStr  = etPpmHr.getText().toString().trim();
                    String label   = etLabel.getText().toString().trim();
                    saveNewPoint(diffStr, ppmStr, label);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveNewPoint(String diffStr, String ppmStr, String label) {
        if (TextUtils.isEmpty(diffStr)) {
            Toast.makeText(this, "Please enter a colour difference value", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(ppmStr)) {
            Toast.makeText(this, "Please enter the known ppm.hr value", Toast.LENGTH_SHORT).show();
            return;
        }

        double diff, ppm;
        try {
            diff = Double.parseDouble(diffStr);
            ppm  = Double.parseDouble(ppmStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (diff < 0 || diff > 441) {
            Toast.makeText(this, "Colour difference must be 0–441", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ppm < 0) {
            Toast.makeText(this, "ppm.hr cannot be negative", Toast.LENGTH_SHORT).show();
            return;
        }

        String autoLabel = TextUtils.isEmpty(label)
                ? "Added " + new SimpleDateFormat("dd-MMM HH:mm", Locale.US)
                        .format(new Date())
                : label;

        CalibrationPoint point = new CalibrationPoint(diff, ppm, autoLabel,
                System.currentTimeMillis());
        boolean saved = CalibrationStore.addPoint(this, point);

        if (saved) {
            Toast.makeText(this, "Calibration point saved", Toast.LENGTH_SHORT).show();
            refreshPoints();
        } else {
            Toast.makeText(this, "Failed to save calibration point", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------
    // Delete / clear
    // ------------------------------------------------------------------

    private void onDeletePoint(CalibrationPoint point) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Calibration Point")
                .setMessage("Remove point: diff=" + String.format("%.2f", point.getColourDifference())
                        + ", " + String.format("%.3f", point.getKnownPpmHr()) + " ppm.hr?\n\n"
                        + "\"" + point.getLabel() + "\"")
                .setPositiveButton("Remove", (d, w) -> {
                    CalibrationStore.removePoint(this, point.getColourDifference());
                    refreshPoints();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Calibration Points")
                .setMessage("This will delete all " + points.size() + " calibration point(s). "
                        + "Scans will show \"CALIBRATION REQUIRED\" until new points are added. "
                        + "This cannot be undone.")
                .setPositiveButton("Clear All", (d, w) -> {
                    CalibrationStore.clearAll(this);
                    refreshPoints();
                    Toast.makeText(this, "Calibration cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------
    // RecyclerView adapter
    // ------------------------------------------------------------------

    interface OnDeleteListener {
        void onDelete(CalibrationPoint point);
    }

    static class PointsAdapter extends RecyclerView.Adapter<PointsAdapter.VH> {

        private final List<CalibrationPoint> items;
        private final OnDeleteListener       listener;

        PointsAdapter(List<CalibrationPoint> items, OnDeleteListener listener) {
            this.items    = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calibration_point, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            CalibrationPoint p = items.get(position);
            Context ctx = h.itemView.getContext();

            h.tvIndex.setText(String.valueOf(position + 1));
            h.tvDiff.setText("Colour diff: " + String.format("%.2f", p.getColourDifference()));
            h.tvPpm.setText(String.format("%.3f ppm.hr", p.getKnownPpmHr()));
            h.tvLabel.setText(p.getLabel());
            h.tvLabel.setVisibility(p.getLabel().isEmpty() ? View.GONE : View.VISIBLE);
            h.btnDelete.setOnClickListener(v -> listener.onDelete(p));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView       tvIndex, tvDiff, tvPpm, tvLabel;
            MaterialButton btnDelete;

            VH(View v) {
                super(v);
                tvIndex   = v.findViewById(R.id.tvPointIndex);
                tvDiff    = v.findViewById(R.id.tvPointDiff);
                tvPpm     = v.findViewById(R.id.tvPointPpm);
                tvLabel   = v.findViewById(R.id.tvPointLabel);
                btnDelete = v.findViewById(R.id.btnDeletePoint);
            }
        }
    }
}
