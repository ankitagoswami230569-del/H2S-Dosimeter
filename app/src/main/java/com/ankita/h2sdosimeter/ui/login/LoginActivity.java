package com.ankita.h2sdosimeter.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.ui.dashboard.DashboardActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * LoginActivity - sign-in screen.
 *
 * Frontend phase: no real authentication.
 * - Demo login button bypasses credentials and goes directly to Dashboard.
 * - Login button validates that fields are non-empty, then proceeds.
 * - Role chips store the selected role in a local variable (no backend yet).
 */
public class LoginActivity extends AppCompatActivity {

    // Selected role (frontend state only)
    private String selectedRole = "WORKER";

    // Views
    private TextInputLayout tilWorkerId;
    private TextInputLayout tilPassword;
    private TextInputEditText etWorkerId;
    private TextInputEditText etPassword;
    private CheckBox cbRememberMe;
    private MaterialButton btnLogin;
    private MaterialButton btnDemoLogin;
    private MaterialButton btnForgotPassword;
    private ChipGroup roleChipGroup;
    private TextView tvLoginError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        bindViews();
        setupRoleChips();
        setupClickListeners();
        animateEntrance();
    }

    private void bindViews() {
        tilWorkerId = findViewById(R.id.tilWorkerId);
        tilPassword = findViewById(R.id.tilPassword);
        etWorkerId = findViewById(R.id.etWorkerId);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        btnLogin = findViewById(R.id.btnLogin);
        btnDemoLogin = findViewById(R.id.btnDemoLogin);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);
        roleChipGroup = findViewById(R.id.roleChipGroup);
        tvLoginError = findViewById(R.id.tvLoginError);
    }

    private void setupRoleChips() {
        roleChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipWorker) {
                selectedRole = "WORKER";
            } else if (id == R.id.chipSafetyOfficer) {
                selectedRole = "SAFETY_OFFICER";
            } else if (id == R.id.chipAdmin) {
                selectedRole = "ADMIN";
            }
        });
    }

    private void setupClickListeners() {

        btnLogin.setOnClickListener(v -> {
            if (validateInputs()) {
                navigateToDashboard();
            }
        });

        btnDemoLogin.setOnClickListener(v -> {
            // Demo login - bypass credentials for frontend testing
            selectedRole = "WORKER";
            navigateToDashboard();
        });

        btnForgotPassword.setOnClickListener(v -> {
            // Frontend stub - show a toast/message
            showError(getString(R.string.mock_notice));
        });
    }

    /**
     * Validates that Worker ID and Password are non-empty.
     * Frontend only - no real credential check.
     */
    private boolean validateInputs() {
        clearErrors();
        boolean valid = true;

        String workerId = etWorkerId.getText() != null
                ? etWorkerId.getText().toString().trim() : "";
        String password = etPassword.getText() != null
                ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(workerId)) {
            tilWorkerId.setError(getString(R.string.error_empty_id));
            valid = false;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.error_empty_password));
            valid = false;
        }

        if (!valid) return false;

        // Frontend: accept any non-empty credentials
        // In production this would call the authentication API
        return true;
    }

    private void clearErrors() {
        tilWorkerId.setError(null);
        tilPassword.setError(null);
        tvLoginError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        tvLoginError.setText(message);
        tvLoginError.setVisibility(View.VISIBLE);
        tvLoginError.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.fade_in)
        );
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
        // Pass selected role for dashboard to adapt its UI
        intent.putExtra(DashboardActivity.EXTRA_ROLE, selectedRole);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    /** Subtle entrance animation for the login card */
    private void animateEntrance() {
        View card = findViewById(R.id.loginCard);
        if (card != null) {
            card.setAlpha(0f);
            card.setTranslationY(60f);
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setStartDelay(150)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }
}
