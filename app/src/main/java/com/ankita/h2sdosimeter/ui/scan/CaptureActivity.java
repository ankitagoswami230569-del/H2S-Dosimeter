package com.ankita.h2sdosimeter.ui.scan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.ankita.h2sdosimeter.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * CaptureActivity - Phase 3: Real rear-camera capture using CameraX.
 *
 * Behaviour:
 *   - On open: requests CAMERA permission if not yet granted.
 *   - Permission granted: starts live CameraX preview (rear lens).
 *   - "Capture" button: takes a real JPEG, saves to app cache,
 *     passes the FileProvider URI to ProcessingActivity.
 *   - "Use Demo Image": bypasses camera entirely, goes straight to
 *     ProcessingActivity with no image URI (demo flow unchanged).
 *   - Permission denied / camera error: shows the demo overlay with
 *     an error message; "Use Demo Image" still works.
 */
public class CaptureActivity extends AppCompatActivity {

    private static final String TAG = "CaptureActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 101;

    // Extra key passed forward to ProcessingActivity
    public static final String EXTRA_IMAGE_URI  = "extra_image_uri";
    /** Absolute file-system path of the captured JPEG - used by the analysis pipeline. */
    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    private PreviewView previewView;
    private LinearLayout demoOverlay;
    private View viewfinderGuide;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;

    private boolean isCameraRunning = false;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        bindViews();
        setupClickListeners();
        requestCameraOrStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();
    }

    // ---------------------------------------------------------------
    // View binding
    // ---------------------------------------------------------------

    private void bindViews() {
        previewView     = findViewById(R.id.cameraPreview);
        demoOverlay     = findViewById(R.id.demoOverlay);
        viewfinderGuide = findViewById(R.id.viewfinderGuide);
    }

    // ---------------------------------------------------------------
    // Click listeners
    // ---------------------------------------------------------------

    private void setupClickListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnCapture).setOnClickListener(v -> {
            if (isCameraRunning && imageCapture != null) {
                capturePhoto();
            } else {
                // Camera not ready - fall through to demo
                Toast.makeText(this,
                        "Camera not ready. Use Demo Image to continue.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Demo image always works regardless of camera state
        findViewById(R.id.btnUseDemoImage).setOnClickListener(v -> launchProcessing(null));
    }

    // ---------------------------------------------------------------
    // Permission handling
    // ---------------------------------------------------------------

    private void requestCameraOrStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                showCameraError("Camera permission denied. Use Demo Image to continue.");
            }
        }
    }

    // ---------------------------------------------------------------
    // CameraX initialisation
    // ---------------------------------------------------------------

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);

        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "CameraProvider init failed", e);
                showCameraError("Camera could not be initialised. Use Demo Image to continue.");
            }
        }, mainExecutor());
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider provider) {
        // Select rear camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Preview use case
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // ImageCapture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        try {
            provider.unbindAll();
            Camera camera = provider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture);

            // Camera opened successfully - hide demo overlay, show guide
            runOnUiThread(() -> {
                demoOverlay.setVisibility(View.GONE);
                viewfinderGuide.setVisibility(View.VISIBLE);
                isCameraRunning = true;
            });

            Log.d(TAG, "Camera bound successfully: " + camera.getCameraInfo().getCameraState());

        } catch (Exception e) {
            Log.e(TAG, "Camera bind failed", e);
            showCameraError("Camera could not be opened. Use Demo Image to continue.");
        }
    }

    private void releaseCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    // ---------------------------------------------------------------
    // Photo capture
    // ---------------------------------------------------------------

    private void capturePhoto() {
        // Disable button during capture to prevent double-tap
        findViewById(R.id.btnCapture).setEnabled(false);

        // Create output file in app's cache/images directory
        File outputFile = createImageFile();
        if (outputFile == null) {
            Toast.makeText(this, "Could not create image file.", Toast.LENGTH_SHORT).show();
            findViewById(R.id.btnCapture).setEnabled(true);
            return;
        }

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCapture.takePicture(
                outputOptions,
                mainExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        Log.d(TAG, "Photo saved to: " + outputFile.getAbsolutePath());

                        // Build a FileProvider URI safe to pass via Intent
                        Uri imageUri = FileProvider.getUriForFile(
                                CaptureActivity.this,
                                getApplicationContext().getPackageName() + ".fileprovider",
                                outputFile);

                        launchProcessing(imageUri);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed", exception);
                        runOnUiThread(() -> {
                            Toast.makeText(CaptureActivity.this,
                                    "Capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            findViewById(R.id.btnCapture).setEnabled(true);
                        });
                    }
                });
    }

    // ---------------------------------------------------------------
    // File helper
    // ---------------------------------------------------------------

    private File createImageFile() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            String filename = "H2S_BADGE_" + timestamp + ".jpg";
            File imagesDir = new File(getCacheDir(), "images");
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                Log.e(TAG, "Could not create images directory");
                return null;
            }
            return new File(imagesDir, filename);
        } catch (Exception e) {
            Log.e(TAG, "createImageFile failed", e);
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------

    /**
     * Launches ProcessingActivity.
     *
     * @param imageUri FileProvider URI of captured image, or null for demo flow.
     */
    private void launchProcessing(Uri imageUri) {
        Intent intent = new Intent(this, ProcessingActivity.class);
        if (imageUri != null) {
            // Pass both the FileProvider URI (for external sharing) AND
            // the raw file path (for the in-process analysis pipeline).
            // The analysis pipeline reads the file directly to avoid any
            // FileProvider permission issues with ContentResolver.
            intent.putExtra(EXTRA_IMAGE_URI, imageUri.toString());

            // Reconstruct the real file path from cache
            // (the URI was created from getCacheDir()/images/<filename>)
            // We store the path separately so BadgeAnalysisPipeline can
            // open it directly without ContentResolver.
            intent.putExtra(EXTRA_IMAGE_PATH, getImagePathFromUri(imageUri));
        }
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left);
    }

    /**
     * Recovers the real file-system path from the FileProvider URI by
     * listing the cache/images directory and matching by filename.
     * Returns null if the file cannot be resolved.
     */
    private String getImagePathFromUri(Uri uri) {
        // The URI last segment is the filename (e.g. H2S_BADGE_20260826_023000.jpg)
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment == null) return null;

        // Strip any path prefix added by FileProvider (e.g. "cached_images/filename")
        String filename = lastSegment.contains("/")
                ? lastSegment.substring(lastSegment.lastIndexOf('/') + 1)
                : lastSegment;

        File imagesDir = new File(getCacheDir(), "images");
        File target = new File(imagesDir, filename);
        return target.exists() ? target.getAbsolutePath() : null;
    }

    // ---------------------------------------------------------------
    // Error state
    // ---------------------------------------------------------------

    private void showCameraError(String message) {
        runOnUiThread(() -> {
            isCameraRunning = false;
            demoOverlay.setVisibility(View.VISIBLE);
            viewfinderGuide.setVisibility(View.GONE);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private Executor mainExecutor() {
        return ContextCompat.getMainExecutor(this);
    }
}
