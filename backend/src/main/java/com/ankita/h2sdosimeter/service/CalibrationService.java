package com.ankita.h2sdosimeter.service;

import com.ankita.h2sdosimeter.dto.CalibrationPointRequest;
import com.ankita.h2sdosimeter.dto.CalibrationPointResponse;
import com.ankita.h2sdosimeter.entity.CalibrationPoint;
import com.ankita.h2sdosimeter.exception.ResourceNotFoundException;
import com.ankita.h2sdosimeter.repository.CalibrationPointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CalibrationService {

    /** Minimum points needed in a version to attempt ppm.hr conversion. */
    public static final int MIN_POINTS = 2;

    private final CalibrationPointRepository calibRepo;

    public CalibrationService(CalibrationPointRepository calibRepo) {
        this.calibRepo = calibRepo;
    }

    // -------------------------------------------------------------------
    // Add / update
    // -------------------------------------------------------------------

    public CalibrationPointResponse addPoint(CalibrationPointRequest req) {
        String version = req.getCalibrationVersion() != null
                ? req.getCalibrationVersion() : "default";

        CalibrationPoint p = new CalibrationPoint(
                req.getColourDifference(),
                req.getKnownPpmHr(),
                req.getLabel(),
                version,
                req.getAddedBy());
        return CalibrationPointResponse.from(calibRepo.save(p));
    }

    public CalibrationPointResponse updatePoint(Long id, CalibrationPointRequest req) {
        CalibrationPoint p = calibRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Calibration point not found: " + id));
        p.setColourDifference(req.getColourDifference());
        p.setKnownPpmHr(req.getKnownPpmHr());
        if (req.getLabel() != null) p.setLabel(req.getLabel());
        return CalibrationPointResponse.from(calibRepo.save(p));
    }

    public void deactivatePoint(Long id) {
        CalibrationPoint p = calibRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Calibration point not found: " + id));
        p.setActive(false);
        calibRepo.save(p);
    }

    public void deletePoint(Long id) {
        if (!calibRepo.existsById(id)) {
            throw new ResourceNotFoundException("Calibration point not found: " + id);
        }
        calibRepo.deleteById(id);
    }

    // -------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CalibrationPointResponse> getActivePoints(String version) {
        String v = version != null ? version : "default";
        return calibRepo.findByCalibrationVersionAndActiveTrueOrderByColourDifferenceAsc(v)
                .stream().map(CalibrationPointResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CalibrationPointResponse> getAllPoints(String version) {
        String v = version != null ? version : "default";
        return calibRepo.findByCalibrationVersionOrderByColourDifferenceAsc(v)
                .stream().map(CalibrationPointResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isCalibrated(String version) {
        String v = version != null ? version : "default";
        return calibRepo.countByCalibrationVersionAndActiveTrue(v) >= MIN_POINTS;
    }

    // -------------------------------------------------------------------
    // Conversion (used by ScanService)
    // -------------------------------------------------------------------

    /**
     * Returns a ppm.hr estimate for the given colour difference and version,
     * or null if calibration data is insufficient.
     * Also returns the CalibrationStatus string for the ScanRecord.
     */
    @Transactional(readOnly = true)
    public ConversionResult convert(double colourDifference, String version) {
        String v = version != null ? version : "default";
        List<CalibrationPoint> points = calibRepo
                .findByCalibrationVersionAndActiveTrueOrderByColourDifferenceAsc(v);

        if (points.size() < MIN_POINTS) {
            return new ConversionResult(null, "UNCALIBRATED",
                    "Need at least " + MIN_POINTS + " calibration points (have " + points.size() + ")");
        }

        int n = points.size();
        double dMin = points.get(0).getColourDifference();
        double dMax = points.get(n - 1).getColourDifference();

        double ppm;
        String status;

        if (colourDifference <= dMin) {
            ppm    = lerp(points.get(0), points.get(1), colourDifference);
            status = "EXTRAPOLATED";
        } else if (colourDifference >= dMax) {
            ppm    = lerp(points.get(n - 2), points.get(n - 1), colourDifference);
            status = "EXTRAPOLATED";
        } else {
            ppm    = 0;
            status = "CALIBRATED";
            for (int i = 0; i < n - 1; i++) {
                if (colourDifference >= points.get(i).getColourDifference()
                        && colourDifference <= points.get(i + 1).getColourDifference()) {
                    ppm = lerp(points.get(i), points.get(i + 1), colourDifference);
                    break;
                }
            }
        }

        ppm = Math.max(0.0, ppm);
        return new ConversionResult(ppm, status, null);
    }

    private static double lerp(CalibrationPoint lo, CalibrationPoint hi, double x) {
        double x0 = lo.getColourDifference(), y0 = lo.getKnownPpmHr();
        double x1 = hi.getColourDifference(), y1 = hi.getKnownPpmHr();
        if (Math.abs(x1 - x0) < 1e-9) return y0;
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    // -------------------------------------------------------------------
    // Inner result type
    // -------------------------------------------------------------------

    public static class ConversionResult {
        public final Double ppmHr;          // null = uncalibrated
        public final String calibrationStatus; // CALIBRATED / EXTRAPOLATED / UNCALIBRATED
        public final String message;

        ConversionResult(Double ppmHr, String status, String message) {
            this.ppmHr             = ppmHr;
            this.calibrationStatus = status;
            this.message           = message;
        }
    }
}
