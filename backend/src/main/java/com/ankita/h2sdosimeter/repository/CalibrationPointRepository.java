package com.ankita.h2sdosimeter.repository;

import com.ankita.h2sdosimeter.entity.CalibrationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalibrationPointRepository extends JpaRepository<CalibrationPoint, Long> {

    /** Active points for a given version, sorted ascending by colour difference. */
    List<CalibrationPoint> findByCalibrationVersionAndActiveTrueOrderByColourDifferenceAsc(
            String calibrationVersion);

    /** All points for a given version (for management UI). */
    List<CalibrationPoint> findByCalibrationVersionOrderByColourDifferenceAsc(
            String calibrationVersion);

    /** All active points across all versions. */
    List<CalibrationPoint> findByActiveTrueOrderByCalibrationVersionAscColourDifferenceAsc();

    /** Count active points for a calibration version. */
    long countByCalibrationVersionAndActiveTrue(String calibrationVersion);
}
