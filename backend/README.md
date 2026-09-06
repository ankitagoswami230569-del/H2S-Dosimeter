# H2S Dosimeter Backend

Spring Boot REST API backend for the H2S Dosimeter Android application.

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21+ |
| MySQL | 8.0+ |
| Gradle | via wrapper (no install needed) |

## 1. MySQL Setup

```sql
-- Run in MySQL as root
CREATE DATABASE h2s_dosimeter CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'h2s_user'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON h2s_dosimeter.* TO 'h2s_user'@'localhost';
FLUSH PRIVILEGES;
```

Hibernate will auto-create all tables on first start (`spring.jpa.hibernate.ddl-auto=update`).

## 2. Environment Variables

**Never hardcode passwords.** Set these before starting the server:

### Windows (Command Prompt)
```cmd
set DB_URL=jdbc:mysql://localhost:3306/h2s_dosimeter?useSSL=false^&serverTimezone=UTC^&allowPublicKeyRetrieval=true
set DB_USERNAME=h2s_user
set DB_PASSWORD=your_secure_password
```

### Windows (PowerShell)
```powershell
$env:DB_URL      = "jdbc:mysql://localhost:3306/h2s_dosimeter?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "h2s_user"
$env:DB_PASSWORD = "your_secure_password"
```

### Linux / macOS
```bash
export DB_URL="jdbc:mysql://localhost:3306/h2s_dosimeter?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export DB_USERNAME=h2s_user
export DB_PASSWORD=your_secure_password
```

## 3. Build

```cmd
# From the backend/ directory:
gradlew.bat build       # Windows
./gradlew build         # Linux/macOS
```

## 4. Run

```cmd
# Set environment variables first (see step 2), then:
gradlew.bat bootRun     # Windows
./gradlew bootRun       # Linux/macOS

# Or run the JAR directly:
java -jar build/libs/h2s-dosimeter-backend-1.0.0.jar
```

Server starts on: `http://localhost:8080/api`

## 5. Run Tests (no MySQL required)

Tests use an H2 in-memory database:
```cmd
gradlew.bat test
```

## 6. Verify the Server

```
GET http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "H2S Dosimeter Backend",
  "version": "1.0.0"
}
```

---

## REST API Endpoints

All endpoints are prefixed with `/api`.

### Workers

| Method | Endpoint | Description |
|---|---|---|
| POST | `/workers` | Register a new worker |
| GET | `/workers` | List all active workers |
| GET | `/workers/{workerId}` | Get worker by ID |
| GET | `/workers/by-badge/{badgeId}` | Get worker assigned to a badge |
| PUT | `/workers/{workerId}` | Update worker details |
| PATCH | `/workers/{workerId}/badge` | Assign badge to worker |
| DELETE | `/workers/{workerId}` | Deactivate worker (soft delete) |

### Badges

| Method | Endpoint | Description |
|---|---|---|
| POST | `/badges` | Register a new badge |
| GET | `/badges` | List all badges |
| GET | `/badges/{badgeId}` | Get badge details |
| GET | `/badges/{badgeId}/validate` | Validate badge (status, expiry) |

### Scans

| Method | Endpoint | Description |
|---|---|---|
| POST | `/scans` | Submit scan result from Android |
| GET | `/scans/{id}` | Get scan by ID |
| GET | `/scans/worker/{workerId}` | Get all scans for a worker |
| GET | `/scans/worker/{workerId}?from=&to=` | Filter by date range |
| GET | `/scans/badge/{badgeId}` | Get all scans for a badge |
| GET | `/scans/worker/{workerId}/latest` | Get most recent scan |
| GET | `/scans/worker/{workerId}/today` | Today's exposure summary |
| GET | `/scans/worker/{workerId}/summary?date=` | Daily exposure summary |

### Calibration

| Method | Endpoint | Description |
|---|---|---|
| POST | `/calibration/points` | Add a calibration reference point |
| GET | `/calibration/points?version=default` | Get active calibration points |
| GET | `/calibration/points/all?version=default` | Get all points (incl. inactive) |
| PUT | `/calibration/points/{id}` | Update a calibration point |
| DELETE | `/calibration/points/{id}` | Delete a calibration point |
| PATCH | `/calibration/points/{id}/deactivate` | Soft-deactivate a point |
| GET | `/calibration/status?version=default` | Check calibration status |
| GET | `/calibration/convert?diff=42.5` | Test: convert diff → ppm.hr |

### Health

| Method | Endpoint | Description |
|---|---|---|
| GET | `/health` | Server health check |

---

## Database Schema

Tables auto-created by Hibernate:

| Table | Key Columns |
|---|---|
| `workers` | `worker_id` (unique), `badge_id`, `email` (unique), `role`, `active` |
| `badges` | `badge_id` (unique), `expiry_date`, `status`, `batch_number` |
| `scan_records` | `worker_id` (FK), `badge_id`, `scan_date`, `colour_difference`, `estimated_ppm_hr`, `calibration_status` |
| `calibration_points` | `colour_difference`, `known_ppm_hr`, `calibration_version`, `active` |

---

## Android Integration

The Android app connects to this backend via the API service layer:

- `app/src/main/java/com/ankita/h2sdosimeter/api/ApiConfig.java` — server URL
- `app/src/main/java/com/ankita/h2sdosimeter/api/ScanPayload.java` — builds scan POST body

To enable backend sync:
1. Set `ApiConfig.BASE_URL` to your server address.
2. Set `ApiConfig.BACKEND_SYNC_ENABLED = true`.
3. Call `ScanPayload.build(...)` and POST to `BASE_URL + "/scans"` after each real scan.

**The Android camera/capture/calibration flows work fully offline.** The backend adds:
- Persistent scan history (survives app reinstall)
- Centralized server-side calibration
- Multi-device access to history
- Safety officer dashboard data

---

## Safety Notice

All `estimatedPpmHr` values returned by the API are **estimates** derived from a
calibration curve. They are **not** validated occupational-health measurements.
Every API response includes a `safetyNotice` field that must be displayed to users.
`estimatedPpmHr` is `null` (not zero) when `calibrationStatus = UNCALIBRATED`.
