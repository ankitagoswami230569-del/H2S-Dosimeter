package com.ankita.h2sdosimeter.api;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * ApiClient — lightweight HttpURLConnection-based HTTP client for the backend.
 *
 * Uses only Android SDK classes (no external library needed).
 * All calls are SYNCHRONOUS — must be invoked from a background thread.
 * Never throws; network/parse failures are returned as ApiResult.failure.
 */
public class ApiClient {

    private static final String TAG = "H2S-ApiClient";

    // -----------------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------------

    public static class ApiResult {
        public final boolean    success;
        public final int        httpCode;   // 0 = network/IO error
        public final JSONObject body;       // parsed JSON body, may be null
        public final String     errorMsg;

        private ApiResult(boolean success, int code, JSONObject body, String err) {
            this.success  = success;
            this.httpCode = code;
            this.body     = body;
            this.errorMsg = err;
        }

        public static ApiResult ok(int code, JSONObject body) {
            return new ApiResult(true, code, body, null);
        }

        public static ApiResult failure(String msg) {
            return new ApiResult(false, 0, null, msg);
        }

        public static ApiResult httpError(int code, String msg) {
            return new ApiResult(false, code, null, "HTTP " + code + ": " + msg);
        }

        /** Safe accessor — returns null if body or data field is absent. */
        public JSONObject data() {
            if (body == null) return null;
            return body.optJSONObject("data");
        }
    }

    // -----------------------------------------------------------------------
    // HTTP verbs
    // -----------------------------------------------------------------------

    /** GET request. */
    public static ApiResult get(String path) {
        String url = ApiConfig.BASE_URL + path;
        Log.d(TAG, "GET " + url);
        return execute(url, "GET", null);
    }

    /** POST request with JSON body. */
    public static ApiResult post(String path, JSONObject body) {
        String url = ApiConfig.BASE_URL + path;
        Log.d(TAG, "POST " + url);
        return execute(url, "POST", body.toString());
    }

    // -----------------------------------------------------------------------
    // Private: execute HTTP call
    // -----------------------------------------------------------------------

    private static ApiResult execute(String urlStr, String method, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(ApiConfig.READ_TIMEOUT_MS);

            // Write body for POST/PUT
            if (jsonBody != null) {
                conn.setDoOutput(true);
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Response " + code + " <- " + method + " " + urlStr);

            // Read body (use error stream if HTTP error)
            java.io.InputStream stream = code >= 400
                    ? conn.getErrorStream() : conn.getInputStream();

            String raw = "";
            if (stream != null) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    raw = sb.toString();
                }
            }

            JSONObject json;
            try {
                json = new JSONObject(raw.isEmpty() ? "{}" : raw);
            } catch (JSONException e) {
                return ApiResult.httpError(code,
                        "Non-JSON: " + raw.substring(0, Math.min(150, raw.length())));
            }

            if (code >= 200 && code < 300) {
                return ApiResult.ok(code, json);
            } else {
                String err = json.optString("error",
                        json.optString("message", "HTTP error " + code));
                Log.w(TAG, "HTTP error " + code + ": " + err);
                return ApiResult.httpError(code, err);
            }

        } catch (IOException e) {
            Log.e(TAG, "Network error on " + method + " " + urlStr + ": " + e.getMessage());
            return ApiResult.failure("Network error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
