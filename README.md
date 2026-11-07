# TTPU "Raqamli Inventar" — Digital Inventory ERP Backend API

Unified Modular Monolith Backend API for **Turin Polytechnic University in Tashkent (TTPU)**. This single backend serves the Web ERP portal, Android application, and iOS application across **16 strict Spring Modulith domain modules**, acting as the sole gateway to **1C Accounting**, **HEMIS**, **E-IMZO (PKCS#7 digital signatures)**, **SMTP Email**, **Telegram Bot API**, and **Firebase Cloud Messaging (FCM)**.

---

## 1. Technology Stack

- **Language & Build**: Java 21, Apache Maven (`pom.xml`)
- **Framework & Modularity**: Spring Boot 3.4.3, Spring Modulith 1.3.3, ArchUnit 1.3.0
- **Database & Auditing**: PostgreSQL 16, Spring Data JPA (Hibernate 6), Hibernate Envers (`@Audited` reference revisions), Flyway (`V1`–`V14`)
- **Security & Rate Limiting**: Spring Security 6 OAuth2 Resource Server (RSA-2048 Nimbus JWT), Argon2id (`bcprov-jdk18on`), 2FA TOTP (`dev.samstevens.totp`), SHA-256 refresh tokens, Bucket4j IP rate limiting, HSTS / `X-Frame-Options: DENY` / CSP headers
- **Real-Time, Scheduling & Resilience**: Spring WebSocket (STOMP `/ws/inventory`), ShedLock (JDBC), Resilience4j (`CircuitBreaker` + `Retry`)
- **Reporting & API Docs**: Apache POI 5.4.0 (`SXSSFWorkbook` streaming XLSX), SpringDoc OpenAPI 3 / Swagger UI (`2.8.5`)
- **Testing & Load Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL 16), k6 (`load-test.js`)

---

## 2. Modular Monolith Architecture (`uz.ttpu.inventory`)

Every module strictly follows the 4-layer architecture (`api`, `service`, `domain`, `repository`) enforced by both `ApplicationModules.of(InventoryApplication.class).verify()` and [`ArchUnitModularityTest`](src/test/java/uz/ttpu/inventory/architecture/ArchUnitModularityTest.java):

| # | Module | Responsibility |
|---|---|---|
| 1 | `common` | Base entities (`BaseEntity`, `BaseReferenceEntity`), scope markers (`DepartmentScoped`, `BuildingScoped`), RFC 7807 `GlobalExceptionHandler`, `OpenApiConfig`, ShedLock/JPA config |
| 2 | `security` | OAuth2 Resource Server JWT, Argon2id hashing, 2FA TOTP, 5-attempt lockout, SHA-256 refresh tokens, `ScopedPermissionEvaluator`, Bucket4j login rate limiting |
| 3 | `references` | Hibernate Envers `@Audited` CRUD & revision history for `Building`, `Floor`, `Room`, `Department`, `Employee`, and `Category` (`JSONB customFields`) |
| 4 | `asset` | `Item` lifecycle, centralized 8-state x 12-event `ItemStateService` state machine, 10-character Base32 QR ID generator (`QrIdService`), public QR card (`/a/{id}`) |
| 5 | `receipt` | Warehouse receipts (`Receipt`, `ReceiptLine`), per-category serial number uniqueness enforcement, `ReceiptApprovedEvent` |
| 6 | `audit` | `@Aspect` (`ItemCardAuditAspect`) logging every `READ`, `CHANGE`, and `EXPORT` of item cards to `audit_record` |
| 7 | `workflow` | Generic multi-step conditional approval engine (`SERVICE_REQUEST`, `TRANSFER`, `WRITE_OFF`) with `TOTP` and `E_IMZO` step verification |
| 8 | `request` | Department and employee asset requisition requests (`DRAFT` -> `SUBMITTED` -> `APPROVED`/`REJECTED` -> `FULFILLED`) |
| 9 | `movement` | Internal logistics: Warehouse Issue & Room/Person Transfer flows with recipient 2FA TOTP confirmation and insert-only PostgreSQL trigger (`trg_movements_insert_only`) |
| 10 | `repair` | Repair ticket lifecycle (`NEW` -> `IN_PROGRESS` -> `IN_SERVICE` -> `DONE` -> `CLOSED`), automatic `TAMIRDA` item state transitions, ShedLock daily overdue alerts |
| 11 | `file` | File attachment metadata (`StoredFileRecord`), SHA-256 checksums, and MinIO bucket/object key storage |
| 12 | `notification` | Event-driven multi-channel notifications across `EMAIL` (Spring Mail), `TELEGRAM` (TelegramBots), and `PUSH` (Firebase Admin SDK) |
| 13 | `inventory` | Inventory sessions, per-room expected item snapshots, live STOMP WebSocket dashboard (`/ws/inventory`), mobile offline sync (`/api/v1/sync/batch`), and 4-category reconciliation (`JOYIDA`, `BOSHQA_XONADA_TOPILDI`, `TOPILMADI` -> `QIDIRUVDA`, `ORTIQCHA`) |
| 14 | `integration` | Transactional Outbox (`OutboxWorkerService`) with Resilience4j `Retry` + `CircuitBreaker`, `OneCConnector`, `HemisSyncService`, and `EImzoService` |
| 15 | `finance` | Straight-line depreciation forecast engine `(initialValue - salvageValue) / usefulLifeYears` with multi-year schedules |
| 16 | `report` | Apache POI `SXSSFWorkbook` streaming XLSX inventory report generator (10,000+ rows in `< 30s`) |

See [CONSOLIDATED_SPECIFICATION.md](CONSOLIDATED_SPECIFICATION.md) for the full cross-prompt analysis and master specification.

---

## 3. Running the Project

### Option A: Docker Compose (Full Stack: PostgreSQL 16, Redis 7, MinIO, Backend API)
```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

### Option B: Local Maven Run
```bash
./mvnw spring-boot:run
```

---

## 4. API Documentation & Live Endpoints

Once the server is running on `http://localhost:8080`:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html` (`http://localhost:8080/swagger-ui/index.html`)
- **OpenAPI 3.0 JSON**: `http://localhost:8080/v3/api-docs`
- **Health Check**: `http://localhost:8080/actuator/health`
- **Public QR Item Card**: `http://localhost:8080/a/TTPU000001`
- **STOMP WebSocket**: `ws://localhost:8080/ws/inventory`

---

## 5. Running Tests & k6 Load Testing

```bash
# Run all 140 unit, modularity, ArchUnit, and PostgreSQL integration tests
./mvnw test

# Build the executable Spring Boot JAR (target/app.jar)
./mvnw clean package -DskipTests

# Run the 200-concurrent-user k6 load test (30 constant QR scanners + 170 ERP users, < 2s SLA)
k6 run load-test.js
```
