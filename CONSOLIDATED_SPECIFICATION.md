# TTPU "Raqamli Inventar" — Cross-Prompt Analysis & Master Consolidated Specification (Milestones 1–6)

## Part 1: Cross-Prompt Analysis — Inconsistencies, Contradictions, Missing Requirements & Resolutions

Below is the comprehensive analysis of all prompts submitted across **Milestones 1–6 (Prompts 1.1 through 6.2)**:

| # | Issue Category & Contradiction / Gap Identified | Affected Prompts & Modules | Architectural & Code Resolution Implemented |
|---|---|---|---|
| **1** | **16 Declared Modules vs. 14 Detailed Module Prompts**: The global architecture prompt and Prompt 1.1 mandate 16 domain modules (`common`, `security`, `references`, `asset`, `workflow`, `request`, `receipt`, `movement`, `repair`, `inventory`, `finance`, `report`, `notification`, `integration`, `file`, `audit`), whereas Prompts 1.2–5.3 only provided explicit task prompts for 14 of them, leaving `request` (requisition requests referenced by `WorkflowDocumentType.SERVICE_REQUEST`) and `file` (MinIO/document attachments referenced by signed PDFs and repair acts) unprompted. | Global Rules, Prompt 1.1, Prompt 3.1, Prompt 3.3, Prompt 5.2, Prompt 6.1 (`request`, `file`, `workflow`, `repair`, `integration`) | Implemented full 4-layer modules (`api`, `service`, `domain`, `repository`) for `request` (`AssetRequest`, `AssetRequestService`, `AssetRequestController`) and `file` (`StoredFileRecord`, `FileStorageService`, `FileController`) backed by Flyway migration `V13__request_and_file_modules.sql`. |
| **2** | **Multi-Module Gradle vs. Spring Modulith Single-Application Modular Monolith**: The global prompt defines a "modular monolith using Java 21, Spring Boot 3, and Spring Modulith" under base package `uz.ttpu.inventory`, while Prompt 1.1 says "Initialize the gradle (Kotlin DSL) multi-module project... Create the base package structure (`uz.ttpu.inventory`) with the 16 empty domain modules". | Global Rules, Prompt 1.1, Prompt 6.1 | Standardized on a Spring Modulith modular monolith (`ApplicationModules.of(InventoryApplication.class).verify()`) with 16 top-level domain module packages under `uz.ttpu.inventory`, each strictly divided into `api`, `service`, `domain`, and `repository` layers, enforced by both Spring Modulith and ArchUnit (`ArchUnitModularityTest`). |
| **3** | **Strict Module Encapsulation & ArchUnit Rules vs. Cross-Module Enum/Service Contracts**: Global Rule 1 and Prompt 6.1 require that JPA entities never leave their module, `api` layers never depend on other `api` layers, and modules only communicate through explicit API interfaces, DTO records, or domain events in `.api`. Previously, `AssetService` and `AssetRequestService` imported `workflow.domain.WorkflowDocumentType` and `WorkflowState` to inspect `WorkflowCompletedEvent`. | Global Rules, Prompt 3.1, Prompt 6.1 (`workflow`, `asset`, `request`, and all 16 modules) | Added semantic helper methods (`isWriteOffDocument()`, `isServiceRequestDocument()`, `isApproved()`, `isRejected()`, `documentTypeName()`, `finalStateName()`) directly onto `WorkflowCompletedEvent` and `WorkflowReturnedForReworkEvent` in `workflow.api`, removing all cross-module `domain` imports. Enforced via `ArchUnitModularityTest`. |
| **4** | **Centralized `ItemStateService` Enforcement vs. Multi-Module State Transitions**: Prompt 2.2 mandates that `ItemState` transitions (`OMBORDA`, `YOLDA`, `FOYDALANISHDA`, `VAQTINCHA_TASHQARIDA`, `TAMIRDA`, `QIDIRUVDA`, `HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN`, `HISOBDAN_CHIQARILGAN`) must **only** happen inside `ItemStateService.transition(Item, ItemEvent, Object)` against a strict matrix. Meanwhile, Prompts 3.1 (write-off workflow), 3.2 (issue/transfer), 3.3 (vendor repair), and 4.2 (inventory reconciliation -> `QIDIRUVDA`) trigger state changes from other modules. | Prompt 2.2, 3.1, 3.2, 3.3, 4.2 (`asset`, `workflow`, `movement`, `repair`, `inventory`) | Protected `Item.applyStateTransition(ItemState)` via `StackWalker` caller inspection so only `ItemStateService` can mutate `Item.state`. Routed all cross-module state transitions through `AssetService` (`AssetLogisticsApi` methods and `@EventListener` handlers for `ReceiptApprovedEvent`, `ItemMovedToVendorRepairEvent`, `ItemRepairCompletedEvent`, and `WorkflowCompletedEvent`), which delegate exclusively to `ItemStateService.transition(...)`. |
| **5** | **Generic Workflow Engine vs. TOTP / E-IMZO & Downstream Document State Updates**: Prompt 3.1 specifies workflow approvals via E-IMZO or TOTP for `SERVICE_REQUEST`, `TRANSFER`, and `WRITE_OFF`, but did not explicitly specify how workflow completion updates the target `AssetRequest` or `Item` write-off state. | Prompt 1.3, 2.2, 3.1, 5.2 (`workflow`, `asset`, `request`, `integration`) | `WorkflowService` validates step conditions (`minAmountCondition`, `maxAmountCondition`, `conditionAttributes`) and verification methods (`TOTP` 6-digit code or `E_IMZO` PKCS#7 payload) and publishes `WorkflowCompletedEvent`. `AssetService`, `AssetRequestService`, `NotificationService`, and `OutboxWorkerService` listen to `WorkflowCompletedEvent` to transition `HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN` -> `HISOBDAN_CHIQARILGAN` (or `FOYDALANISHDA` on rejection), update `AssetRequest` to `APPROVED`/`REJECTED`, send notifications, and enqueue 1C outbox messages. |
| **6** | **1C External Depreciation Sync (Prompt 5.2) vs. Finance Straight-Line Depreciation Forecast (Prompt 5.3)**: Prompt 5.2 receives actual accounting depreciation updates from 1C (`OneCConnector`), whereas Prompt 5.3 requires calculating straight-line depreciation forecasts `(initialValue - salvageValue) / usefulLifeYears` in the `finance` module. | Prompt 5.2, 5.3 (`integration`, `finance`, `asset`) | Harmonized both capabilities: `OneCConnectorService.receiveDepreciationData(...)` updates actual `Item.residualValue` (`NUMERIC(18,2)`) and enqueues an outbox acknowledgment, while `DepreciationService` calculates and persists multi-year straight-line depreciation schedules (`DepreciationRecord` + `YearlyDepreciationForecastDto`) with final-year penny-rounding reconciliation. |
| **7** | **Role Naming Consistency (`ROLE_*` Prefix) & Method Security**: `RoleName` enum values use the `ROLE_` prefix (`ROLE_ADMIN`, `ROLE_WAREHOUSE_MANAGER`, etc.), while Spring Security's `hasAnyRole(...)` checks for `ROLE_<name>` authorities and some `@PreAuthorize` expressions used alias names (`INVENTORY_COMMISSION` vs `COMMISSION_MEMBER`, `RECTORATE` vs `RECTOR`/`VICE_RECTOR`). | Prompt 1.3, All Controllers (`security` and all `api` layers) | Updated `SecurityConfig.extractAuthorities` to grant both the raw role claim and `ROLE_`-prefixed authority, and aligned `@PreAuthorize` expressions across all controllers to accept both canonical `RoleName` values and role group aliases. |
| **8** | **Audit Aspect on Bulk Exports & ShedLock Proxy Constraints**: Prompt 2.3 requires `@Aspect` logging of every read, change, and export of an item card to `audit_record`, while Prompt 5.3 exports 10,000 rows in a single XLSX stream. Logging `Arrays.toString(args)` on a 10,000-row list or exceeding `VARCHAR(255)` on `target_id` would bloat or fail the audit insert; additionally, `@SchedulerLock` requires `void` return types on `@Scheduled` proxy methods. | Prompt 2.3, 3.3, 5.1, 5.2, 5.3 (`audit`, `asset`, `repair`, `integration`, `report`) | Annotated both single-item and bulk XLSX export methods with `@AuditableItemAction(AuditActionType.EXPORT)`, summarized collection arguments in `ItemCardAuditAspect` (`Collection(size=N)`), truncated `targetId`/`methodName`/`actorUsername` to `<= 255` chars in `AuditService`, and separated `void` `@Scheduled @SchedulerLock` entrypoints from testable service methods. |
| **9** | **Login Rate Limiting (Prompt 6.1) vs. 5-Attempt Account Lockout (Prompt 1.3) & k6 Load Testing (Prompt 6.2)**: Prompt 1.3 locks a user account after 5 consecutive failed logins, while Prompt 6.1 enforces strict Bucket4j rate limiting on login endpoints (`/api/v1/auth/login` and `/api/v1/auth/refresh`), and Prompt 6.2 simulates 200 concurrent users in k6 (`load-test.js`). If 200 VUs all called `/api/v1/auth/login` on startup from the same load-generator IP, Bucket4j would throttle them with HTTP 429. | Prompt 1.3, Prompt 6.1, Prompt 6.2 (`security`, `load-test.js`) | Configured `LoginRateLimiterService` (`capacity = 10` requests per minute per client IP) so that 5-attempt per-user account lockout (`LoginAttemptService`) triggers at attempt 5 while IP-level brute-force floods are blocked at attempt 11 with HTTP `429 Too Many Requests` + `Retry-After`. Designed `load-test.js` `setup()` to authenticate once before VUs launch and share the JWT token and discovered QR codes across all 200 VUs. |
| **10** | **AOP Advisor Ordering & HikariCP Connection Pool Starvation Under 200 Concurrent QR Scans (Prompt 2.3 & Prompt 6.2)**: `AssetService.getPublicItemCard` is annotated with `@Transactional(readOnly = true)` and `@AuditableItemAction(AuditActionType.READ)`, while `AuditService.recordItemAction` uses `@Transactional(propagation = Propagation.REQUIRES_NEW)`. Without explicit `@Order` on `ItemCardAuditAspect`, the aspect ran *inside* `TransactionInterceptor`, causing each request to hold its outer read-only JDBC connection while requesting a second JDBC connection for `REQUIRES_NEW` — deadlocking HikariCP when concurrent requests reached the pool size. | Prompt 2.3, Prompt 6.2 (`audit`, `asset`, `application.yml`) | Annotated `ItemCardAuditAspect` with `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` so it wraps *outside* `TransactionInterceptor` (`Ordered.LOWEST_PRECEDENCE`), ensuring the read transaction commits and releases its JDBC connection before `AuditService.recordItemAction` acquires a connection, and tuned `spring.datasource.hikari.maximum-pool-size=30`. Verified with a 200-concurrent-virtual-user load test in `InventoryApplicationTests`. |

---

## Part 2: Consolidated Master Prompt Specification

Below is the single, complete, self-contained master prompt consolidating all requirements from **Milestones 1–6 (Prompts 1.1 through 6.2)** without contradictions:

```markdown
# Master Prompt: TTPU "Raqamli Inventar" Modular Monolith Backend API

## 1. Role, Context & Core Technology Stack
You are an expert Java Backend Architect building the **"Raqamli Inventar"** API for Turin Polytechnic University in Tashkent (TTPU). This single backend API serves the Web application, Android app, and iOS app, and acts as the sole gateway to all external systems (1C Accounting, HEMIS, E-IMZO, Email SMTP, Telegram Bot API, and Firebase Cloud Messaging Push).

### Technology Stack
- **Language & Build**: Java 21 (toolchain release 21, `-parameters`), Gradle (Kotlin DSL).
- **Framework & Architecture**: Spring Boot 3.4.3, Spring Modulith 1.3.3 (strict modular monolith).
- **Database & Persistence**: PostgreSQL 16, Spring Data JPA (Hibernate 6), Hibernate Envers (`@Audited` reference history), Flyway migrations (`V1` through `V14`).
- **Mapping & Boilerplate**: MapStruct 1.6.3 (`componentModel = "spring"`), Lombok (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor` — **never** use `@Data` on JPA entities).
- **Security, Rate Limiting & Cryptography**: Spring Security 6 OAuth2 Resource Server (RSA-2048 Nimbus JWT), Argon2id password hashing (`Argon2PasswordEncoder` with BouncyCastle `bcprov-jdk18on`), mandatory 2FA TOTP (`dev.samstevens.totp`), SHA-256 hashed refresh tokens, Bucket4j (`bucket4j_jdk17-core`) token-bucket rate limiting, HTTP security headers (HSTS, `X-Frame-Options: DENY`, `Content-Security-Policy`).
- **Real-Time, Scheduling & Resilience**: Spring WebSocket with STOMP (`/ws/inventory`), ShedLock (`shedlock-spring` + `shedlock-provider-jdbc-template`), Resilience4j (`CircuitBreaker` + `Retry`).
- **Notifications & Reporting**: Spring Mail, TelegramBots Spring Boot Starter, Firebase Admin SDK, Apache POI 5.4.0 (`poi-ooxml` with streaming `SXSSFWorkbook`).
- **Testing, Architecture Verification & Load Testing**: JUnit 5 (including parameterized tests), AssertJ, Mockito, Spring Modulith Test, ArchUnit (`archunit-junit5`), Testcontainers (PostgreSQL 16), k6 load testing (`load-test.js`), Docker Compose (PostgreSQL 16, Redis 7, MinIO), GitHub Actions (`.github/workflows/build.yml`).

### Global Architectural Rules
1. **16 Domain Modules**: Under base package `uz.ttpu.inventory`, maintain exactly 16 domain modules:
   `common`, `security`, `references`, `asset`, `workflow`, `request`, `receipt`, `movement`, `repair`, `inventory`, `finance`, `report`, `notification`, `integration`, `file`, `audit`.
2. **4-Layer Internal Structure**: Inside every module, use strictly 4 sub-packages:
   - `api`: REST controllers, Java `record` DTOs, cross-module public interfaces, and domain events (`@NamedInterface("api")`).
   - `service`: `@Service` transactional business logic, MapStruct `@Mapper` interfaces, scheduled workers, and event listeners.
   - `domain`: JPA `@Entity` classes, enums, and domain exceptions.
   - `repository`: Spring Data JPA repositories.
3. **Strict Encapsulation & Boundary Verification**: JPA entities never leave their module. `api` layers never depend on other modules' `api` layers. Cross-module calls must use explicit public interfaces, Java `record` DTOs, or domain event records in `<module>.api`, verified by both `ApplicationModules.of(InventoryApplication.class).verify()` and ArchUnit (`ArchUnitModularityTest`).
4. **Data Precision & Timezones**: All timestamps are `java.time.Instant` in UTC stored as PostgreSQL `TIMESTAMPTZ`. All monetary amounts are `java.math.BigDecimal` stored as `NUMERIC(18,2)`.
5. **API Versioning & Injection**: Version all REST endpoints under `/api/v1/...`, plus `/a/{id}` (and `/api/v1/a/{id}`) for public QR item cards. Use constructor injection via `@RequiredArgsConstructor`. Every domain error must map cleanly to RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`.

---

## 2. Milestone-by-Milestone Functional & Technical Requirements

### Milestone 1: Project Skeleton, Core Schema & Security (`common`, `references`, `security`)
1. **DevOps & Project Setup**:
   - Configure `build.gradle.kts`, `docker-compose.yml` (PostgreSQL 16, Redis 7, MinIO), and `.github/workflows/build.yml`.
2. **Base Entities & Reference Schema (`V1__init_schema.sql`)**:
   - In `common`, create `BaseEntity` (`UUID id`, `Instant createdAt`, `Instant updatedAt`, `Long version`) and `BaseReferenceEntity` (adds `boolean active = true` for soft deletion) plus scope marker interfaces `DepartmentScoped` and `BuildingScoped`.
   - In `references`, create JPA entities for `Building`, `Floor`, `Room`, `Department`, `Employee`, and `Category` (with a `JSONB` `customFields` column).
3. **Security & Auth Module (`security`)**:
   - Configure stateless JWT authentication using Spring Security OAuth2 Resource Server (`NimbusJwtEncoder` / `NimbusJwtDecoder`).
   - Implement `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, and `/api/v1/auth/2fa/setup`.
   - Hash passwords with Argon2id; enforce mandatory 2FA TOTP verification (`TotpService` & `TotpVerificationApi`).
   - Lock user accounts for 15 minutes (`lockedUntil`) on the 5th consecutive failed login attempt (`LoginAttemptService`).
   - Store refresh tokens as SHA-256 hashes (`TokenHashUtil`) with token rotation on refresh.
   - Implement `ScopedPermissionEvaluator` (`PermissionEvaluator`) to enforce method-level access control by `GLOBAL`, `DEPARTMENT`, and `BUILDING` scopes.

### Milestone 2: Reference Auditing, Asset State Machine, Receipts, QR & Audit (`references`, `asset`, `receipt`, `audit`)
1. **Reference Data CRUD & Hibernate Envers (`V2__envers_and_assets.sql`)**:
   - Annotate `Building`, `Floor`, `Room`, `Department`, `Employee`, and `Category` with `@Audited` (backed by `revinfo` and `*_aud` tables).
   - Provide `/api/v1/references/...` CRUD and `/revisions` endpoints using MapStruct DTO records. `DELETE` endpoints must only set `active = false`.
   - Expose `ReferenceSyncApi` for upserting departments and employees from HEMIS.
2. **Asset Module & Strict Item State Machine (`asset`)**:
   - Create `Item` entity (`inventoryNumber`, `serialNumber`, `qrCode`, `name`, `categoryId`, `state`, `purchasePrice`, `residualValue`, `commissionedAt`, `warrantyExpiresAt`, `expectedReturnAt`, location/custodian IDs, `JSONB customAttributes`, `oneCRefId`).
   - Implement `ItemState` with 8 states: `OMBORDA`, `YOLDA`, `FOYDALANISHDA`, `VAQTINCHA_TASHQARIDA`, `TAMIRDA`, `QIDIRUVDA`, `HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN`, `HISOBDAN_CHIQARILGAN`.
   - Implement `ItemEvent` with 12 events: `DISPATCH`, `PUT_INTO_USE`, `RETURN_TO_WAREHOUSE`, `TAKE_OUTSIDE`, `RETURN_FROM_OUTSIDE`, `SEND_TO_REPAIR`, `COMPLETE_REPAIR`, `MARK_MISSING`, `FOUND_IN_USE`, `RECOMMEND_WRITE_OFF`, `REJECT_WRITE_OFF`, `APPROVE_WRITE_OFF`.
   - Enforce state transitions exclusively inside `ItemStateService.transition(Item, ItemEvent, Object)` against the 29-valid / 67-invalid transition matrix (blocking direct mutation via `StackWalker`), persist `ItemStateHistory`, and publish `ItemStateChangedEvent`.
   - Test all 96 `(ItemState x ItemEvent)` combinations with parameterized JUnit 5 tests.
3. **Receipt Module (`receipt`, `V3__receipt_module.sql`)**:
   - Create `Receipt` (contract, invoice, supplier, warehouse building, status `DRAFT`/`CONFIRMED`) and `ReceiptLine` (name, categoryId, serialNumber, priceWithoutVat, quantity).
   - Enforce case-insensitive uniqueness of `serialNumber` within `categoryId` both in-memory per request and in PostgreSQL (`DuplicateSerialNumberException` -> HTTP 409).
   - Approving a receipt (`POST /api/v1/receipts/{id}/approve`) transitions it to `CONFIRMED` and publishes `ReceiptApprovedEvent`.
4. **QR Public ID & Item Card Audit Aspect (`asset`, `audit`, `V4__audit_record_table.sql`)**:
   - `QrIdService` generates a random, non-guessable 10-character Base32 (`A-Z2-7`) public ID for each item created upon `ReceiptApprovedEvent`.
   - Expose public endpoint `GET /a/{id}` (and `/api/v1/a/{id}`) returning `PublicItemCardDto` (`qrId`, `name`, `inventoryNumber`, `"TTPU mulki"`, `contactPhone`).
   - Implement `ItemCardAuditAspect` (`@Aspect`) in `audit` intercepting `@AuditableItemAction` to log every `READ`, `CHANGE`, and `EXPORT` action to `audit_record` via `AuditService` (`Propagation.REQUIRES_NEW`).

### Milestone 3: Workflows, Requests, Movements, Repairs, Files & Notifications (`workflow`, `request`, `movement`, `repair`, `file`, `notification`)
1. **Generic Workflow Approval Engine (`workflow`, `V5__workflow_module.sql`)**:
   - Create `WorkflowDefinition` and ordered `WorkflowStepDefinition` entities supporting amount thresholds (`minAmountCondition`, `maxAmountCondition`), JSONB `conditionAttributes`, and `VerificationMethod` (`TOTP`, `E_IMZO`, `EITHER`).
   - Create `WorkflowInstance` (`DRAFT`, `IN_AGREEMENT`, `APPROVED`, `REJECTED`) and `WorkflowStepDecision` (`APPROVED`, `REJECTED`, `RETURNED_FOR_REWORK`).
   - Publish `WorkflowCompletedEvent` on final approval/rejection and `WorkflowReturnedForReworkEvent` when returned to `DRAFT`.
   - Connect `WorkflowCompletedEvent` to `AssetService` (transitioning `HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN` items on `WRITE_OFF` completion) and `AssetRequestService` (updating `SERVICE_REQUEST` status).
2. **Asset Requisition Requests (`request`, `V13__request_and_file_modules.sql`)**:
   - Manage `AssetRequest` (`/api/v1/requests`) through `DRAFT` -> `SUBMITTED` -> `APPROVED`/`REJECTED` -> `FULFILLED` with `NUMERIC(18,2)` unit and total cost calculation.
3. **Internal Logistics: Issue & Transfer (`movement`, `V6__movement_module.sql`)**:
   - Issue Flow: Warehouse dispatches item (`OMBORDA` -> `YOLDA`, `ISSUE_DISPATCHED`). Recipient confirms with 6-digit TOTP (`TotpVerificationApi`) to move it to `FOYDALANISHDA` (`ISSUE_ACCEPTED`), or rejects it back to `OMBORDA` (`ISSUE_REJECTED`).
   - Transfer Flow: Room-to-room (`TRANSFER_ROOM_TO_ROOM`) or person-to-person (`TRANSFER_PERSON_TO_PERSON`) transfers move item `FOYDALANISHDA` -> `YOLDA` (`TRANSFER_INITIATED`), then upon recipient TOTP confirmation move to `FOYDALANISHDA` with updated location/custodian (`TRANSFER_ACCEPTED`), or on rejection return to `FOYDALANISHDA` with original custodian (`TRANSFER_REJECTED`).
   - Make `movements` strictly insert-only (`@Immutable` + PostgreSQL trigger `trg_movements_insert_only` raising an exception on `UPDATE` or `DELETE`).
4. **Repair, File Attachments, Multi-Channel Notifications & ShedLock Jobs (`repair`, `file`, `notification`, `V7__repair_notification_and_shedlock.sql`, `V13__request_and_file_modules.sql`)**:
   - `RepairTicket` (`/api/v1/repairs`) follows states `NEW` -> `IN_PROGRESS` -> `IN_SERVICE` -> `DONE` -> `CLOSED`. Moving a ticket to vendor repair (`IN_SERVICE`) publishes `ItemMovedToVendorRepairEvent`, which transitions the item to `TAMIRDA`. Completing vendor repair (`DONE`) publishes `ItemRepairCompletedEvent`, returning the item to `FOYDALANISHDA`.
   - `FileStorageService` (`/api/v1/files`) stores Base64 file payloads with SHA-256 checksums and MinIO bucket/key metadata (`StoredFileRecord`).
   - `NotificationService` (`/api/v1/notifications`) listens to repair, workflow, and alert domain events and dispatches across `EMAIL` (`JavaMailSender`), `TELEGRAM` (`AbsSender`), and `PUSH` (`FirebaseMessaging`), persisting `NotificationLog`.
   - Configure ShedLock (`shedlock` table) and `@Scheduled` jobs (`AssetScheduledAlertJob` and `RepairService`) to check daily for overdue temporary returns, overdue repair tickets, and warranties expiring within 30 days.

### Milestone 4: Inventory Sessions, STOMP WebSockets & Offline Sync Reconciliation (`inventory`)
1. **Inventory Sessions & Live STOMP Dashboard (`V8__inventory_module.sql`)**:
   - Create `InventorySession` (rector's order number/date, session date, chairman, commission members, rooms, status `DRAFT`/`IN_PROGRESS`/`COMPLETED`/`CANCELLED`, `packageVersion`).
   - Generate per-room expected item snapshots (`InventorySessionRoom`, `InventoryExpectedItem`) from active non-written-off assets.
   - Configure STOMP WebSocket endpoint `/ws/inventory` (`InventoryWebSocketConfig`) and push live `InventoryProgressDashboardDto` updates (per-room completion status, found/not-found counts, completion percentage) to `/topic/inventory/sessions/{sessionId}/progress` and `/topic/inventory/dashboard`.
2. **Offline Sync API & Reconciliation (`V9__offline_sync_and_reconciliation.sql`)**:
   - `GET /api/v1/sync/sessions/{id}/package`: Returns session metadata, per-room expected lists, and `version` for mobile offline caching.
   - `POST /api/v1/sync/batch`: Processes batch scan actions idempotently using `clientActionId` (`UUID` unique constraint on `inventory_scan_actions`), ignoring duplicate retries safely.
   - Reconciliation (`POST /api/v1/inventory/sessions/{sessionId}/reconcile` and `POST /api/v1/sync/sessions/{id}/reconcile`): Compares scanned results against expected lists, categorizing items into:
     - `JOYIDA` ("Joyida" — found in expected room)
     - `BOSHQA_XONADA_TOPILDI` ("Boshqa xonada topildi" — found in another room)
     - `TOPILMADI` ("Topilmadi" — not found; automatically transitions item state to `QIDIRUVDA`)
     - `ORTIQCHA` ("Ortiqcha" — surplus item not in session expected list)

### Milestone 5: Transactional Outbox, External Connectors, Depreciation & Streaming Reports (`integration`, `finance`, `report`)
1. **Transactional Outbox & Resilience4j Worker (`integration`, `V10__outbox_integration_module.sql`)**:
   - Create `OutboxMessage` (`integration_outbox`) with unique `idempotencyKey`, `ExternalSystemType` (`ONE_C`, `HEMIS`, `E_IMZO`), status (`PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`, `DEAD_LETTER`), retry count, and exponential backoff (`nextAttemptAt`).
   - `OutboxWorkerService` polls due messages (protected by ShedLock) and dispatches via `ExternalSystemGateway` decorated with Resilience4j `Retry` and `CircuitBreaker`. Automatically enqueue outbox messages on `ReceiptApprovedEvent` and `WorkflowCompletedEvent`.
2. **External Connectors: 1C, HEMIS, E-IMZO (`integration`, `V11__eimzo_signature_records.sql`)**:
   - `OneCConnector` (`OneCConnectorService`): Enqueues new item exports to 1C via the Resilience4j outbox and processes incoming 1C depreciation updates (`receiveDepreciationData`), updating `Item.residualValue` and enqueueing an outbox acknowledgment.
   - `HemisSyncService`: Runs daily (ShedLock + Resilience4j `Retry`/`CircuitBreaker`) to fetch department and employee updates from `HemisExternalClientStub` and upsert them via `ReferenceSyncApi`.
   - `EImzoService`: Accepts signed PDF blobs (`/api/v1/integrations/eimzo/verify-pdf`), computes SHA-256 hash, verifies PKCS#7 signatures via `EImzoExternalVerificationStub` wrapped in Resilience4j, and persists signer PINFL, TIN, full name, organization, and certificate serial in `EImzoSignatureRecord`.
3. **Straight-Line Depreciation & Streaming XLSX Reports (`finance`, `report`, `V12__finance_and_report_modules.sql`)**:
   - `DepreciationService` (`/api/v1/finance/depreciation`): Calculates straight-line depreciation forecasts `(initialValue - salvageValue) / usefulLifeYears`, generates full yearly schedules (`YearlyDepreciationForecastDto`) with final-year rounding reconciliation, and persists `DepreciationRecord`.
   - `SxssfInventoryReportService` (`/api/v1/reports/inventory.xlsx` and `/api/v1/reports/inventory/xlsx`): Uses Apache POI `SXSSFWorkbook` (streaming window size 200) to generate 10,000-row XLSX inventory reports in under 30 seconds, logging metrics to `ReportExportLog` and recording an `EXPORT` audit entry via `@AuditableItemAction`.

### Milestone 6: Security Hardening, ArchUnit Boundary Enforcement & k6 Load Testing (`security`, `architecture`, `load-test.js`)
1. **Security Hardening & Bucket4j Login Rate Limiting (`security`)**:
   - Enforce strict token-bucket rate limiting on `/api/v1/auth/login` and `/api/v1/auth/refresh` using Bucket4j (`LoginRateLimiterService` + `LoginRateLimitingFilter`), returning `X-Rate-Limit-Remaining` on allowed requests and HTTP `429 Too Many Requests` (`ProblemDetail` + `Retry-After` header) when exceeded.
   - Configure Spring Security headers in `SecurityConfig`:
     - `Strict-Transport-Security`: `max-age=31536000; includeSubDomains; preload`
     - `X-Frame-Options`: `DENY`
     - `Content-Security-Policy`: `default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'`
2. **ArchUnit Modularity Tests (`ArchUnitModularityTest`)**:
   - Enforce Spring Modulith and architectural boundaries using ArchUnit:
     - All 16 declared modules exist and every class inside a module resides strictly in `api`, `service`, `domain`, or `repository`.
     - `api` layers cannot depend on other modules' `api` layers.
     - Cross-module communication with non-`common` modules must only target explicit API interfaces, DTO records, or domain event records in `<module>.api`.
     - JPA `@Entity` classes reside strictly in `<module>.domain`, never leave their module, and are never referenced directly by `.api` layers.
     - `domain` layer classes never depend on `api`, `service`, or `repository` layers.
3. **k6 Load Testing (`load-test.js`)**:
   - Provide a k6 load-testing script simulating **200 concurrent users**:
     - `qr_public_scan`: **30 constant VUs** continuously hitting `GET /a/{id}`.
     - `authenticated_erp_users`: **170 VUs** executing concurrent ERP asset/QR operations.
   - Enforce SLA thresholds confirming response times remain under **2 seconds** (`< 2000 ms`, `p(95)<1500`, `p(99)<2000`, `max<2000`) and error rate `< 1%`.
```
