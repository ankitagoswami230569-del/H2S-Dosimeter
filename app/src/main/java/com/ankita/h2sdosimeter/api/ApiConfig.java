package com.ankita.h2sdosimeter.api;

/**
 * ApiConfig - central configuration for the H2S Dosimeter backend connection.
 *
 * BASE_URL is the full address of the Spring Boot server including /api path.
 *
 * PHYSICAL DEVICE (same WiFi):  "http://192.168.1.7:8080/api"
 * ANDROID EMULATOR (default):   "http://10.0.2.2:8080/api"
 *
 * Update DEVICE_IP if your PC's IP changes (run `ipconfig` on PC).
 * Set USE_EMULATOR = true when testing on emulator, false for physical device.
 */
public class ApiConfig {

    // -----------------------------------------------------------------------
    // Network configuration — update DEVICE_IP to match your PC's WiFi IP
    // -----------------------------------------------------------------------
    private static final String DEVICE_IP   = "10.109.39.250";   // PC WiFi IP (run ipconfig)
    private static final String EMULATOR_IP = "10.0.2.2";
    private static final int    PORT        = 8080;
    private static final String PATH        = "/api";

    /** Set to true when running on Android emulator, false for physical device. */
    public static final boolean USE_EMULATOR = false;

    public static final String BASE_URL =
            "http://" + (USE_EMULATOR ? EMULATOR_IP : DEVICE_IP) + ":" + PORT + PATH;

    // -----------------------------------------------------------------------
    // Timeout configuration
    // -----------------------------------------------------------------------
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS    = 30_000;
    public static final int WRITE_TIMEOUT_MS   = 30_000;

    // -----------------------------------------------------------------------
    // Feature flags
    // -----------------------------------------------------------------------

    /**
     * When true, every REAL scan result is posted to the backend immediately
     * after processing. Failures are silent (app continues to work offline).
     */
    public static final boolean BACKEND_SYNC_ENABLED = true;

    /**
     * When true, the dashboard loads today's exposure summary from the backend
     * instead of showing mock data. Requires BACKEND_SYNC_ENABLED = true.
     */
    public static final boolean LOAD_DASHBOARD_FROM_BACKEND = true;

    /**
     * Fixed worker ID for the current session.
     * In a real login flow this would come from the authenticated session.
     */
    public static final String CURRENT_WORKER_ID = "WRK-001";
    public static final String CURRENT_BADGE_ID  = "H2S-BDG-001";

    private ApiConfig() {}
}
