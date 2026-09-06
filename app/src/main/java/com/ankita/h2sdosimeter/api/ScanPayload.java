package com.ankita.h2sdosimeter.api;

import com.ankita.h2sdosimeter.model.ColourAnalysisResult;
import com.ankita.h2sdosimeter.calibration.CalibrationCurve;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ScanPayload - builds the JSON request body for POST /api/scans.
 *
 * Converts Android-side ColourAnalysisResult + calibration output
 * into the format expected by the Spring Boot backend.
 *
 * Usage (future Android → backend sync):
 *   JSONObject json = ScanPayload.build(workerId, badgeId, shiftName,
 *                                       analysisResult, conversionResult);
 *   // then POST json.toString() to ApiConfig.BASE_URL + "/scans"
 */
public class ScanPayload {

    private ScanPayload() { /* utility class */ }

    /**
     * Builds the scan submit JSON payload.
     *
     * @param workerId       Worker ID (e.g. "WRK-001")
     * @param badgeId        Badge ID  (e.g. "H2S-BDG-001")
     * @param shiftName      Current shift name (e.g. "Morning Shift")
     * @param result         ColourAnalysisResult from the pipeline
     * @param conversion     CalibrationCurve.ConversionResult (may be null if uncalibrated)
     * @return JSONObject ready to be posted to the backend
     */
    public static JSONObject build(String workerId,
                                    String badgeId,
                                    String shiftName,
                                    ColourAnalysisResult result,
                                    CalibrationCurve.ConversionResult conversion)
            throws JSONException {

        JSONObject json = new JSONObject();

        // Context
        json.put("workerId",   workerId);
        json.put("badgeId",    badgeId);
        json.put("shiftName",  shiftName != null ? shiftName : JSONObject.NULL);
        json.put("scanTimestamp",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Analysis pipeline output
        json.put("analysisStatus", result.getStatus().name());
        json.put("analysisStatusMessage", result.getStatusMessage());

        if (result.getStatus() == ColourAnalysisResult.Status.SUCCESS) {
            json.put("rawSensorR", result.getRawSensorR());
            json.put("rawSensorG", result.getRawSensorG());
            json.put("rawSensorB", result.getRawSensorB());
            json.put("referenceR", result.getReferenceR());
            json.put("referenceG", result.getReferenceG());
            json.put("referenceB", result.getReferenceB());
            json.put("correctedSensorR", result.getCorrectedSensorR());
            json.put("correctedSensorG", result.getCorrectedSensorG());
            json.put("correctedSensorB", result.getCorrectedSensorB());
            json.put("colourDifference", result.getColourDifference());
            json.put("brightness",       result.getBrightness());
            json.put("sharpness",        result.getSharpness());
            json.put("imageQualityLabel", result.getImageQualityLabel());
        }

        // Calibration output
        if (conversion != null && conversion.success) {
            json.put("calibrationStatus",
                    conversion.confidence == CalibrationCurve.Confidence.HIGH
                    ? "CALIBRATED" : "EXTRAPOLATED");
            json.put("estimatedPpmHr",    conversion.estimatedPpmHr);
            json.put("calibrationVersion", "device-local");
        } else {
            json.put("calibrationStatus", "UNCALIBRATED");
        }

        return json;
    }
}
