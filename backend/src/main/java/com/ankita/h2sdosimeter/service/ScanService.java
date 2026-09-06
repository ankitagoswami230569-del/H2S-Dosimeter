package com.ankita.h2sdosimeter.service;

import com.ankita.h2sdosimeter.dto.ExposureSummaryResponse;
import com.ankita.h2sdosimeter.dto.ScanRecordResponse;
import com.ankita.h2sdosimeter.dto.ScanSubmitRequest;
import com.ankita.h2sdosimeter.entity.ScanRecord;
import com.ankita.h2sdosimeter.entity.Worker;
import com.ankita.h2sdosimeter.exception.ResourceNotFoundException;
import com.ankita.h2sdosimeter.repository.ScanRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ScanService {

    private final ScanRecordRepository scanRepo;
    private final WorkerService        workerService;
    private final CalibrationService   calibService;

    public ScanService(ScanRecordRepository scanRepo,
                       WorkerService workerService,
                       CalibrationService calibService) {
        this.scanRepo       = scanRepo;
        this.workerService  = workerService;
        this.calibService   = calibService;
    }

    // -------------------------------------------------------------------
    // Submit scan
    // -------------------------------------------------------------------

    public ScanRecordResponse submitScan(ScanSubmitRequest req) {
        Worker worker = workerService.findWorker(req.getWorkerId());

        ScanRecord scan = new ScanRecord();
        scan.setWorker(worker);
        scan.setBadgeId(req.getBadgeId());
        scan.setShiftName(req.getShiftName());
        scan.setShiftId(req.getShiftId());
        scan.setImageRef(req.getImageRef());

        // Parse timestamp
        LocalDateTime ts = parseTimestamp(req.getScanTimestamp());
        scan.setScanTimestamp(ts);
        scan.setScanDate(ts.toLocalDate());

        // Analysis status
        scan.setAnalysisStatus(parseAnalysisStatus(req.getAnalysisStatus()));
        scan.setAnalysisStatusMessage(req.getAnalysisStatusMessage());

        // RGB values
        scan.setRawSensorR(req.getRawSensorR());
        scan.setRawSensorG(req.getRawSensorG());
        scan.setRawSensorB(req.getRawSensorB());
        scan.setReferenceR(req.getReferenceR());
        scan.setReferenceG(req.getReferenceG());
        scan.setReferenceB(req.getReferenceB());
        scan.setCorrectedSensorR(req.getCorrectedSensorR());
        scan.setCorrectedSensorG(req.getCorrectedSensorG());
        scan.setCorrectedSensorB(req.getCorrectedSensorB());

        scan.setColourDifference(req.getColourDifference());
        scan.setBrightness(req.getBrightness());
        scan.setSharpness(req.getSharpness());
        scan.setImageQualityLabel(req.getImageQualityLabel());

        // --- Calibration / ppm.hr ---
        // The Android app may send a pre-computed estimate from its local
        // calibration. We also run the server-side calibration and prefer
        // server-side if available (more authoritative / centrally managed).
        resolveCalibration(scan, req);

        // Derive exposure category from ppm.hr (if available)
        deriveExposureCategory(scan);

        return ScanRecordResponse.from(scanRepo.save(scan));
    }

    /**
     * Resolves calibration: tries server-side calibration first using the
     * "default" version. Falls back to the Android-submitted estimate if the
     * server has insufficient calibration data. If both are unavailable,
     * marks the scan as UNCALIBRATED.
     */
    private void resolveCalibration(ScanRecord scan, ScanSubmitRequest req) {
        if (scan.getAnalysisStatus() != ScanRecord.AnalysisStatus.SUCCESS
                || scan.getColourDifference() == null) {
            scan.setCalibrationStatus(ScanRecord.CalibrationStatus.UNCALIBRATED);
            return;
        }

        // Attempt server-side conversion (using "default" version)
        CalibrationService.ConversionResult serverResult =
                calibService.convert(scan.getColourDifference(), "default");

        if (serverResult.ppmHr != null) {
            // Server calibration succeeded — use it
            scan.setEstimatedPpmHr(serverResult.ppmHr);
            scan.setCalibrationVersion("server-default");
            scan.setCalibrationStatus(
                    "EXTRAPOLATED".equals(serverResult.calibrationStatus)
                    ? ScanRecord.CalibrationStatus.EXTRAPOLATED
                    : ScanRecord.CalibrationStatus.CALIBRATED);
            return;
        }

        // Fall back to Android-submitted estimate
        if (req.getEstimatedPpmHr() != null
                && req.getCalibrationStatus() != null
                && !"UNCALIBRATED".equalsIgnoreCase(req.getCalibrationStatus())) {
            scan.setEstimatedPpmHr(req.getEstimatedPpmHr());
            scan.setCalibrationVersion(
                    req.getCalibrationVersion() != null
                    ? req.getCalibrationVersion() : "device-local");
            scan.setCalibrationStatus(
                    "EXTRAPOLATED".equalsIgnoreCase(req.getCalibrationStatus())
                    ? ScanRecord.CalibrationStatus.EXTRAPOLATED
                    : ScanRecord.CalibrationStatus.CALIBRATED);
            return;
        }

        // No calibration available
        scan.setCalibrationStatus(ScanRecord.CalibrationStatus.UNCALIBRATED);
    }

    private void deriveExposureCategory(ScanRecord scan) {
        if (scan.getEstimatedPpmHr() == null) {
            scan.setExposureCategory(ScanRecord.ExposureCategory.UNKNOWN);
            return;
        }
        double ppm = scan.getEstimatedPpmHr();
        // Thresholds below are illustrative. Real thresholds depend on site
        // safety standards and must be set by a qualified safety officer.
        if (ppm < 1.0) {
            scan.setExposureCategory(ScanRecord.ExposureCategory.LOW);
        } else if (ppm < 5.0) {
            scan.setExposureCategory(ScanRecord.ExposureCategory.ELEVATED);
        } else {
            scan.setExposureCategory(ScanRecord.ExposureCategory.HIGH);
        }
    }

    // -------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ScanRecordResponse getById(Long id) {
        ScanRecord s = scanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found: " + id));
        return ScanRecordResponse.from(s);
    }

    @Transactional(readOnly = true)
    public List<ScanRecordResponse> getByWorker(String workerId) {
        return scanRepo.findByWorker_WorkerIdOrderByScanTimestampDesc(workerId)
                .stream().map(ScanRecordResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScanRecordResponse> getByWorkerAndDateRange(
            String workerId, LocalDate from, LocalDate to) {
        return scanRepo.findByWorker_WorkerIdAndScanDateBetweenOrderByScanTimestampDesc(
                workerId, from, to)
                .stream().map(ScanRecordResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScanRecordResponse> getByBadge(String badgeId) {
        return scanRepo.findByBadgeIdOrderByScanTimestampDesc(badgeId)
                .stream().map(ScanRecordResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScanRecordResponse getLatest(String workerId) {
        ScanRecord s = scanRepo.findTopByWorker_WorkerIdOrderByScanTimestampDesc(workerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No scans found for worker: " + workerId));
        return ScanRecordResponse.from(s);
    }

    // -------------------------------------------------------------------
    // Exposure summary
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ExposureSummaryResponse getDailySummary(String workerId, LocalDate date) {
        List<ScanRecord> scans = scanRepo.findByWorker_WorkerIdAndScanDate(workerId, date);

        int count = scans.size();
        long calibratedCount = scans.stream()
                .filter(s -> s.getCalibrationStatus() != ScanRecord.CalibrationStatus.UNCALIBRATED
                        && s.getEstimatedPpmHr() != null)
                .count();

        Double total = scans.stream()
                .filter(s -> s.getEstimatedPpmHr() != null)
                .mapToDouble(ScanRecord::getEstimatedPpmHr)
                .sum();

        if (calibratedCount == 0) total = null;

        String calibStatus;
        if (count == 0 || calibratedCount == 0) {
            calibStatus = "UNCALIBRATED";
        } else if (calibratedCount < count) {
            calibStatus = "PARTIAL";
        } else {
            calibStatus = "CALIBRATED";
        }

        return new ExposureSummaryResponse(
                workerId, date, null, count, total, calibStatus);
    }

    // -------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(ts, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(ts, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e2) {
                return LocalDateTime.now();
            }
        }
    }

    private ScanRecord.AnalysisStatus parseAnalysisStatus(String s) {
        if (s == null) return ScanRecord.AnalysisStatus.ERROR;
        try {
            return ScanRecord.AnalysisStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ScanRecord.AnalysisStatus.ERROR;
        }
    }
}
