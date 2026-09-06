package com.ankita.h2sdosimeter.ui.splash;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.ui.login.LoginActivity;

/**
 * SplashActivity - launch screen.
 *
 * Shows the H2S Dosimeter branding with subtle entry animations.
 * Respects the system reduce-motion preference: when enabled,
 * animations are skipped and navigation happens immediately.
 *
 * Navigation target: LoginActivity
 */
public class SplashActivity extends AppCompatActivity {

    // Duration before auto-navigating to Login
    private static final long SPLASH_DURATION_MS = 2400L;
    // Shorter delay when reduce-motion is on
    private static final long SPLASH_DURATION_REDUCED_MS = 600L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen immersive
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_splash);

        View logo = findViewById(R.id.splashLogo);
        View centerGroup = findViewById(R.id.splashCenterGroup);
        View bottomGroup = findViewById(R.id.splashBottomGroup);
        View progress = findViewById(R.id.splashProgress);

        if (isReduceMotionEnabled()) {
            // Skip animations - just show the screen briefly
            scheduleNavigation(SPLASH_DURATION_REDUCED_MS);
        } else {
            // Animate logo in with scale-pulse
            Animation scalePulse = AnimationUtils.loadAnimation(this, R.anim.scale_pulse);
            logo.startAnimation(scalePulse);

            // Fade-in-up for the title group (delayed)
            centerGroup.setAlpha(0f);
            centerGroup.postDelayed(() -> {
                centerGroup.setAlpha(1f);
                Animation fadeInUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_up);
                centerGroup.startAnimation(fadeInUp);
            }, 300);

            // Fade-in for bottom group (delayed more)
            bottomGroup.setAlpha(0f);
            bottomGroup.postDelayed(() -> {
                bottomGroup.setAlpha(1f);
                Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                bottomGroup.startAnimation(fadeIn);
            }, 700);

            // Progress indicator fade in
            progress.setAlpha(0f);
            progress.postDelayed(() -> {
                progress.setAlpha(1f);
                Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                progress.startAnimation(fadeIn);
            }, 900);

            scheduleNavigation(SPLASH_DURATION_MS);
        }
    }

    private void scheduleNavigation(long delayMs) {
        handler.postDelayed(this::navigateToLogin, delayMs);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        // Custom transition: slide in from right
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    /**
     * Checks whether the user has enabled "Remove animations" or reduce-motion
     * in Android accessibility settings.
     * On Android 10+, this checks the animator duration scale via reflection-safe API.
     * Fallback: assume animations are on.
     */
    private boolean isReduceMotionEnabled() {
        try {
            float scale = android.provider.Settings.Global.getFloat(
                    getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
            );
            return scale == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
