# 🚛 FleetOps Vehicle Service

The Vehicle Service manages the core **fleet catalog and real-time telemetry/KPIs** for the FleetOps Vehicle Maintenance Platform. 

## 🛠️ Tech Stack
*   **Framework:** Spring Boot 3.4
*   **Database:** PostgreSQL (uses `vehicle_db`)
*   **Caching:** Redis 7 (for fast dashboard KPI retrieval)
*   **Authentication:** Stateless JWT (Validated via `JwtAuthenticationFilter`)

## 🎯 Responsibilities
*   **Fleet Catalog:** Full CRUD operations on the vehicle list (managed by ADMINs).
*   **Lifecycle Management:** Tracks statuses like `ACTIVE`, `BREAKDOWN`, `IN_SERVICE`, and `RETIRED`.
*   **KPI Computation:** Calculates total active vehicles, breakdown counts, expiring insurance, and due service alerts dynamically.
*   **Caching:** Uses Redis to temporarily cache the intensive KPI dashboard computations.

## 📡 API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/vehicles` | Public | List all vehicles (optional `?driverId=` or `?status=`) |
| `GET` | `/vehicles/dashboard` | MANAGER/ADMIN | Fetch fleet KPIs (cached) |
| `GET` | `/vehicles/{id}` | Public | Get single vehicle details |
| `POST` | `/vehicles` | ADMIN | Register new vehicle |
| `PUT` | `/vehicles/{id}` | ADMIN | Update vehicle |
| `DELETE` | `/vehicles/{id}` | ADMIN | Delete/Retire vehicle |
| `PATCH` | `/vehicles/{id}/status` | Authenticated | Update vehicle lifecycle state |

## 🚀 Running Locally

### Prerequisites
*   Java 21
*   Maven
*   PostgreSQL running locally (with `vehicle_db` created)
*   Redis running locally on port 6379

### Environment Variables
```bash
export JWT_SECRET=your-super-secret-key-minimum-32-chars
export REDIS_HOST=localhost
export REDIS_PORT=6379
./mvnw spring-boot:run
```

## 🐳 Docker

```bash
docker build -t fleetops-vehicle-service:v1.0.0 .
```
