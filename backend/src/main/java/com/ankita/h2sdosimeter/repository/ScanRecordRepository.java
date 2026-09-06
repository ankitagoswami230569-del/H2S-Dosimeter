package com.ankita.h2sdosimeter.repository;

import com.ankita.h2sdosimeter.entity.ScanRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScanRecordRepository extends JpaRepository<ScanRecord, Long> {

    // --- By worker ---
    List<ScanRecord> findByWorker_WorkerIdOrderByScanTimestampDesc(String workerId);

    List<ScanRecord> findByWorker_WorkerIdAndScanDateBetweenOrderByScanTimestampDesc(
            String workerId, LocalDate from, LocalDate to);

    List<ScanRecord> findByWorker_WorkerIdAndScanDateOrderByScanTimestampDesc(
            String workerId, LocalDate date);

    // --- By badge ---
    List<ScanRecord> findByBadgeIdOrderByScanTimestampDesc(String badgeId);

    List<ScanRecord> findByBadgeIdAndScanDateBetweenOrderByScanTimestampDesc(
            String badgeId, LocalDate from, LocalDate to);

    // --- By shift ---
    List<ScanRecord> findByWorker_WorkerIdAndShiftNameOrderByScanTimestampDesc(
            String workerId, String shiftName);

    // --- Today's scans ---
    List<ScanRecord> findByWorker_WorkerIdAndScanDate(String workerId, LocalDate date);

    // --- Latest scan for a worker ---
    Optional<ScanRecord> findTopByWorker_WorkerIdOrderByScanTimestampDesc(String workerId);

    // --- Exposure summary: sum of ppm.hr for a worker on a date ---
    @Query("SELECT COALESCE(SUM(s.estimatedPpmHr), 0.0) " +
           "FROM ScanRecord s " +
           "WHERE s.worker.workerId = :workerId " +
           "  AND s.scanDate = :date " +
           "  AND s.estimatedPpmHr IS NOT NULL")
    Double sumEstimatedPpmHrByWorkerAndDate(
            @Param("workerId") String workerId,
            @Param("date") LocalDate date);

    // --- Count scans for a worker on a date ---
    long countByWorker_WorkerIdAndScanDate(String workerId, LocalDate date);
}
