package com.ankita.h2sdosimeter.util;

import com.ankita.h2sdosimeter.model.Badge;
import com.ankita.h2sdosimeter.model.ExposureRecord;
import com.ankita.h2sdosimeter.model.Shift;
import com.ankita.h2sdosimeter.model.Worker;

import java.util.ArrayList;
import java.util.List;

/**
 * MockDataHelper - supplies frontend-phase demo data.
 *
 * ALL values returned by this class are DEMO/MOCK data.
 * They are not real H2S measurements, not validated calibration data,
 * and must not be used for occupational health or safety decisions.
 *
 * This class will be replaced by real data sources when the backend
 * and calibration system are implemented.
 */
public class MockDataHelper {

    private MockDataHelper() { /* utility class - no instances */ }

    // ---------------------------------------------------------------
    // Worker
    // ---------------------------------------------------------------

    public static Worker getMockWorker() {
        return new Worker(
                "WRK-001",
                "Ankita Demo",
                "ankita.demo@refinery.com",
                "Process Unit A",
                "Refinery Block 3",
                "+91-98765-00001",
                Worker.Role.WORKER
        );
    }

    public static Worker getMockSafetyOfficer() {
        return new Worker(
                "SO-001",
                "Rajesh Officer",
                "rajesh.so@refinery.com",
                "HSE Department",
                "Refinery Block 3",
                "+91-98765-00002",
                Worker.Role.SAFETY_OFFICER
        );
    }

    public static List<Worker> getMockWorkerList() {
        List<Worker> list = new ArrayList<>();
        list.add(getMockWorker());
        list.add(new Worker("WRK-002", "Demo Worker 2", "w2@demo.com",
                "Process Unit B", "Block 2", "+91-98765-00003", Worker.Role.WORKER));
        list.add(new Worker("WRK-003", "Demo Worker 3", "w3@demo.com",
                "Maintenance", "Block 1", "+91-98765-00004", Worker.Role.WORKER));
        list.add(new Worker("WRK-004", "Demo Worker 4", "w4@demo.com",
                "Process Unit A", "Block 3", "+91-98765-00005", Worker.Role.WORKER));
        list.add(new Worker("WRK-005", "Demo Worker 5", "w5@demo.com",
                "Control Room", "Block 4", "+91-98765-00006", Worker.Role.WORKER));
        return list;
    }

    // ---------------------------------------------------------------
    // Shift
    // ---------------------------------------------------------------

    public static Shift getCurrentMockShift() {
        return new Shift(
                "SHF-001",
                "Morning Shift",
                "25-Aug-2026",
                "06:00",
                "14:00",
                "Refinery Block 3"
        );
    }

    // ---------------------------------------------------------------
    // Badge
    // ---------------------------------------------------------------

    public static Badge getMockBadge() {
        return new Badge(
                "H2S-BDG-001",
                "01-Aug-2026",
                "31-Aug-2026",
                Badge.BadgeStatus.VALID,
                true,
                true,
                true
        );
    }

    public static Badge getMockExpiredBadge() {
        return new Badge(
                "H2S-BDG-000",
                "01-Jun-2026",
                "30-Jun-2026",
                Badge.BadgeStatus.EXPIRED,
                false,
                true,
                false
        );
    }

    // ---------------------------------------------------------------
    // Exposure Records (all DEMO values - not real measurements)
    // ---------------------------------------------------------------

    /**
     * Returns the "current scan" mock result.
     * Value labelled DEMO - not a validated H2S measurement.
     */
    public static ExposureRecord getMockCurrentExposure() {
        return new ExposureRecord(
                "REC-001",
                "25-Aug-2026",
                "08:35",
                "Morning Shift",
                "H2S-BDG-001",
                "0.8 ppm.hr [ESTIMATED - DEMO]",
                ExposureRecord.ExposureCategory.LOW,
                Badge.BadgeStatus.VALID
        );
    }

    /**
     * Returns recent exposure history.
     * All values are DEMO/MOCK - not real measurements.
     */
    public static List<ExposureRecord> getMockExposureHistory() {
        List<ExposureRecord> history = new ArrayList<>();
        history.add(new ExposureRecord("REC-005", "21-Aug-2026", "14:10",
                "Morning Shift", "H2S-BDG-001",
                "0.6 ppm.hr [ESTIMATED - DEMO]",
                ExposureRecord.ExposureCategory.LOW, Badge.BadgeStatus.VALID));
        history.add(new ExposureRecord("REC-004", "20-Aug-2026", "14:05",
                "Morning Shift", "H2S-BDG-001",
                "1.2 ppm.hr [ESTIMATED - DEMO]",
                ExposureRecord.ExposureCategory.ELEVATED, Badge.BadgeStatus.VALID));
        history.add(new ExposureRecord("REC-003", "19-Aug-2026", "08:50",
                "Night Shift", "H2S-BDG-001",
                "0.4 ppm.hr [ESTIMATED - DEMO]",
                ExposureRecord.ExposureCategory.LOW, Badge.BadgeStatus.VALID));
        history.add(new ExposureRecord("REC-002", "18-Aug-2026", "14:00",
                "Morning Shift", "H2S-BDG-001",
                "0.9 ppm.hr [ESTIMATED - DEMO]",
                ExposureRecord.ExposureCategory.LOW, Badge.BadgeStatus.VALID));
        history.add(new ExposureRecord("REC-001", "17-Aug-2026", "08:45",
                "Morning Shift", "H2S-BDG-001",
                "1.8 ppm.hr [ESTIMATED - DEMO]",
                ExposureRecord.ExposureCategory.ELEVATED, Badge.BadgeStatus.VALID));
        return history;
    }

    /**
     * Chart data points (float array) for exposure trend visualization.
     * Values are DEMO only - labels on chart must indicate ESTIMATED/DEMO.
     */
    public static float[] getMockChartData() {
        // 7-day demo trend (units: arbitrary demo scale)
        return new float[]{0.6f, 0.4f, 0.9f, 1.2f, 0.4f, 0.8f, 0.6f};
    }

    public static String[] getMockChartLabels() {
        return new String[]{"17 Aug", "18 Aug", "19 Aug", "20 Aug",
                "21 Aug", "22 Aug", "25 Aug"};
    }
}
