package com.ankita.h2sdosimeter;

import com.ankita.h2sdosimeter.entity.CalibrationPoint;
import com.ankita.h2sdosimeter.entity.Worker;
import com.ankita.h2sdosimeter.repository.CalibrationPointRepository;
import com.ankita.h2sdosimeter.repository.WorkerRepository;
import com.ankita.h2sdosimeter.service.CalibrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using H2 in-memory database (no MySQL required for tests).
 */
@SpringBootTest
@ActiveProfiles("test")
class H2sDosimeterApplicationTests {

    @Autowired
    WorkerRepository workerRepo;

    @Autowired
    CalibrationPointRepository calibRepo;

    @Autowired
    CalibrationService calibService;

    @Test
    void contextLoads() {
        // Verifies the Spring context starts cleanly
    }

    @Test
    void workerPersistence() {
        Worker w = new Worker("WRK-TEST", "Test Worker", "test@example.com",
                "Test Dept", "Test Site", "123", Worker.Role.WORKER, "BDG-T01");
        w = workerRepo.save(w);
        assertThat(w.getId()).isNotNull();
        assertThat(workerRepo.findByWorkerId("WRK-TEST")).isPresent();
    }

    @Test
    void calibrationConversionInterpolated() {
        // Add two calibration points
        calibRepo.save(new CalibrationPoint(10.0, 1.0, "Low ref",  "test-v1", "test"));
        calibRepo.save(new CalibrationPoint(50.0, 5.0, "High ref", "test-v1", "test"));

        // Interpolate at the midpoint
        CalibrationService.ConversionResult result = calibService.convert(30.0, "test-v1");
        assertThat(result.ppmHr).isNotNull();
        assertThat(result.ppmHr).isEqualTo(3.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.calibrationStatus).isEqualTo("CALIBRATED");
    }

    @Test
    void calibrationInsufficientPoints() {
        CalibrationService.ConversionResult result = calibService.convert(30.0, "empty-version");
        assertThat(result.ppmHr).isNull();
        assertThat(result.calibrationStatus).isEqualTo("UNCALIBRATED");
    }

    @Test
    void calibrationExtrapolatedAbove() {
        calibRepo.save(new CalibrationPoint(10.0, 1.0, "Low",  "extrap-v1", "test"));
        calibRepo.save(new CalibrationPoint(50.0, 5.0, "High", "extrap-v1", "test"));

        // 100.0 is above the max calibration point of 50.0 → extrapolated
        CalibrationService.ConversionResult result = calibService.convert(100.0, "extrap-v1");
        assertThat(result.ppmHr).isNotNull();
        assertThat(result.calibrationStatus).isEqualTo("EXTRAPOLATED");
        assertThat(result.ppmHr).isGreaterThan(5.0);
    }
}
