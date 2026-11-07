# TTPU Raqamli Inventar — Administrator & Handover Guide (M7: Weeks 22–24)

This Administrator Guide provides the complete operational runbook for deploying, configuring, migrating pilot data, and performing PostgreSQL 16 Continuous WAL Archiving & Point-in-Time Recovery (PITR) drills for the **Turin Polytechnic University in Tashkent (TTPU) Digital Inventory Platform (`raqamli-inventar`)**.

---

## 1. System Architecture & Deployment Overview

```
                    +-------------------------------------------+
                    |      Next.js Web UI / KMP Mobile App      |
                    +---------------------+---------------------+
                                          | HTTPS / WSS (TLS 1.3)
                                          v
+-----------------------------------------------------------------------------------+
|                 Spring Boot 3.4 / Java 21 LTS Modular Monolith                    |
|  16 Spring Modulith Modules:                                                      |
|  common | security | references | asset | receipt | movement | inventory          |
|  workflow | request | repair | finance | report | notification | integration      |
|  file | audit                                                                     |
+-------------------+---------------------+---------------------+-------------------+
                    |                     |                     |
                    v                     v                     v
      +-----------------------+ +-------------------+ +-----------------------+
      |   PostgreSQL 16       | |   Redis 7         | |   MinIO S3 Storage    |
      |   + pg_trgm + Envers  | |   Cache & Session | |   Bucket:             |
      |   + WAL Archiving     | |   Store           | |   ttpu-inventory      |
      +-----------------------+ +-------------------+ +-----------------------+
```

### 1.1 Hardware & Software Prerequisites
| Component | Minimum Specification (Pilot) | Production Specification |
| :--- | :--- | :--- |
| **Runtime** | Eclipse Temurin JDK 21 LTS (`21.0.12+`) | Eclipse Temurin JDK 21 LTS |
| **Build Tool** | Apache Maven `3.9.9` (`./mvnw` wrapper included) | Apache Maven `3.9.9` |
| **Database** | PostgreSQL `16.x` with `pg_trgm` extension | PostgreSQL `16.x` (Primary + WAL Archive Volume) |
| **Cache** | Redis `7.2+` | Redis `7.2+` with AOF persistence |
| **Object Storage** | MinIO (`RELEASE.2024+`) | MinIO Distributed / S3-compatible storage |
| **CPU / RAM** | 4 vCPU / 8 GB RAM | 8 vCPU / 16 GB RAM |

---

## 2. Application Deployment Guide

### 2.1 Option A: Full Docker Compose Deployment (Recommended for On-Premise Server)
1. Clone the repository and navigate to the project root:
   ```bash
   git clone https://github.com/Bekzodse/backendttpu.git
   cd backendttpu
   ```
2. Start the infrastructure and application containers:
   ```bash
   docker compose up -d postgres redis minio minio-init
   ```
3. Build and start the Spring Boot backend:
   ```bash
   ./mvnw -B clean package -DskipTests
   java -XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC -Dfile.encoding=UTF-8 -jar target/app.jar
   ```
   *(Or run `docker build -t ttpu/raqamli-inventar:1.0.0 .` to build the multi-stage container image defined in [Dockerfile](file:///Users/macbookprom2pro/Downloads/backendttpu/Dockerfile)).*

### 2.2 Option B: Stoplight Prism OpenAPI Mock Server for Frontend / Mobile Teams
Frontend (Next.js) and Mobile (Kotlin Multiplatform) developers can run the contract-first mock server against [openapi.yaml](file:///Users/macbookprom2pro/Downloads/backendttpu/openapi.yaml):
```bash
./scripts/prism-mock.sh
# Or via Docker Compose profile:
docker compose --profile mock up -d prism-mock
```

### 2.3 Post-Deployment Verification Endpoints
| Endpoint | Purpose | Expected Response |
| :--- | :--- | :--- |
| `GET /actuator/health` | Liveness & Readiness probe (DB, Redis, Disk) | `HTTP 200 {"status":"UP"}` |
| `GET /actuator/prometheus` | Prometheus metrics scrape endpoint | `HTTP 200 text/plain` |
| `GET /swagger-ui.html` | Interactive Swagger UI API Explorer | `HTTP 200 text/html` |
| `GET /openapi.yaml` | Contract-First OpenAPI 3.0.1 YAML specification | `HTTP 200 application/vnd.oai.openapi` |
| `GET /a/JBSWY3DPEB` | Public QR Scan Card (no auth required) | `HTTP 200 {"ownershipLabel":"TTPU mulki",...}` |

---

## 3. Environment Variables Reference

Configure the following environment variables in `/etc/ttpu-inventory/env` or your Docker Compose / Kubernetes secret manifest before starting the service in production:

### 3.1 PostgreSQL 16 Database Configuration
| Environment Variable | Default (Local Dev) | Production Recommendation | Description |
| :--- | :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ttpu_inventory` | `jdbc:postgresql://db.internal.ttpu.uz:5432/ttpu_inventory?sslmode=require` | JDBC connection URL (all timestamps stored in `UTC`) |
| `SPRING_DATASOURCE_USERNAME` | `ttpu` | `ttpu_prod_app` | PostgreSQL application role |
| `SPRING_DATASOURCE_PASSWORD` | `ttpu_secret` | *(Strong 32+ char secret from Vault)* | PostgreSQL password |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `20` | `30` | HikariCP max connection pool size |
| `SPRING_FLYWAY_ENABLED` | `true` | `true` | Automatically applies Flyway migrations `V1`–`V15` |

### 3.2 Redis 7 Cache Configuration
| Environment Variable | Default (Local Dev) | Production Recommendation | Description |
| :--- | :--- | :--- | :--- |
| `SPRING_DATA_REDIS_HOST` | `localhost` | `redis.internal.ttpu.uz` | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | `6379` | Redis port |
| `SPRING_DATA_REDIS_PASSWORD` | *(empty)* | *(Strong 32+ char Redis ACL password)* | Redis authentication secret |

### 3.3 MinIO S3 Object Storage Configuration
| Environment Variable | Default (Local Dev) | Production Recommendation | Description |
| :--- | :--- | :--- | :--- |
| `MINIO_ENDPOINT` | `http://localhost:9000` | `https://s3.internal.ttpu.uz` | MinIO S3 API endpoint |
| `MINIO_ACCESS_KEY` | `minioadmin` | `ttpu-minio-prod-access-key` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin123` | *(Strong 40+ char secret key)* | MinIO secret key |
| `MINIO_BUCKET` | `ttpu-inventory` | `ttpu-inventory` | Bucket for signed PDFs, invoices, and inspection photos |

### 3.4 JWT, Argon2id, TOTP & Rate Limiting Security Configuration
| Environment Variable | Default | Production Recommendation | Description |
| :--- | :--- | :--- | :--- |
| `APP_SECURITY_JWT_ISSUER` | `https://inventory.ttpu.uz` | `https://inventory.ttpu.uz` | OAuth2 JWT `iss` claim |
| `APP_SECURITY_JWT_ACCESS_TOKEN_TTL` | `PT15M` | `PT15M` (15 minutes) | Access token lifetime |
| `APP_SECURITY_JWT_REFRESH_TOKEN_TTL` | `P7D` | `P7D` (7 days) | Refresh token lifetime (stored as SHA-256 hash) |
| `APP_SECURITY_MAX_FAILED_LOGIN_ATTEMPTS` | `5` | `5` | Consecutive failed attempts before account lockout |
| `APP_SECURITY_LOCKOUT_DURATION` | `PT15M` | `PT15M` | Account lockout duration after 5 failed attempts |
| `APP_SECURITY_LOGIN_RATE_LIMIT_CAPACITY` | `10` | `10` | Bucket4j max login requests per IP per window |
| `APP_SECURITY_LOGIN_RATE_LIMIT_WINDOW` | `PT1M` | `PT1M` | Bucket4j refill window (`HTTP 429` + `Retry-After`) |

### 3.5 External Integrations & Notification Channels
| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `APP_INTEGRATION_RETRY_MAX_ATTEMPTS` | `3` | Resilience4j retry attempts for 1C, HEMIS & E-IMZO |
| `APP_INTEGRATION_CB_FAILURE_RATE_THRESHOLD` | `50.0` | CircuitBreaker failure rate threshold (%) |
| `APP_NOTIFICATION_EMAIL_ENABLED` | `false` (`true` in prod) | Enables SMTP email alerts |
| `APP_NOTIFICATION_TELEGRAM_ENABLED` | `false` (`true` in prod) | Enables Telegram Bot operational alerts |
| `APP_NOTIFICATION_PUSH_ENABLED` | `false` (`true` in prod) | Enables Firebase Cloud Messaging (FCM) push alerts |
| `APP_PUBLIC_CARD_CONTACT_PHONE` | `+998 71 246-70-82` | Contact phone displayed on `/a/{id}` public QR cards |

---

## 4. Pilot Building 1C XLSX Data Migration Runbook (M7)

The `integration` module includes [`PilotDataMigrationService`](file:///Users/macbookprom2pro/Downloads/backendttpu/src/main/java/uz/ttpu/inventory/integration/service/PilotDataMigrationService.java) to migrate pilot building spreadsheets exported from 1C Accounting.

### 4.1 Step 1: Download the 15-Column 1C Pilot XLSX Template
```bash
curl -H "Authorization: Bearer <JWT_TOKEN>" \
     -o ttpu-1c-pilot-migration-template.xlsx \
     http://localhost:8080/api/v1/integrations/pilot-migration/template.xlsx
```

### 4.2 Step 2: Run Validation in Dry-Run Mode (`dryRun: true`)
Before writing any rows to PostgreSQL, run a dry-run validation to detect missing rooms (`MISSING_ROOM`), missing employees (`MISSING_EMPLOYEE`), missing categories (`MISSING_CATEGORY`), or invalid financial values (`INVALID_ROW_DATA`):
```bash
BASE64_XLSX=$(base64 -i pilot-building-b-main.xlsx | tr -d '\n')

curl -X POST http://localhost:8080/api/v1/integrations/pilot-migration/import-xlsx \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{
    \"xlsxBase64\": \"${BASE64_XLSX}\",
    \"defaultBuildingCode\": \"B-MAIN\",
    \"dryRun\": true,
    \"strictMode\": true,
    \"autoCreateMissingCategories\": false
  }"
```
Inspect the `mismatches` array in the JSON response and reconcile any missing rooms or employees in the Reference Module (`/api/v1/references/rooms` or `/api/v1/integrations/hemis/sync`).

### 4.3 Step 3: Execute Live Pilot Import (`dryRun: false`)
```bash
curl -X POST http://localhost:8080/api/v1/integrations/pilot-migration/import-xlsx \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{
    \"xlsxBase64\": \"${BASE64_XLSX}\",
    \"defaultBuildingCode\": \"B-MAIN\",
    \"dryRun\": false,
    \"strictMode\": false,
    \"autoCreateMissingCategories\": true
  }"
```
Each imported item automatically receives a unique 10-character Base32 QR code, has its `pg_trgm` search columns (`model`, `responsible_person`, `room`) populated, and—if assigned to a room—transitions cleanly through `ItemStateService` (`OMBORDA -> YOLDA -> FOYDALANISHDA`) with full `ItemStateHistory`.

---

## 5. PostgreSQL 16 WAL Archiving & Backup/Restore Drill (RTO <= 4h, RPO <= 24h)

To meet TTPU's disaster recovery SLA (**RTO $\le 4$ hours**, **RPO $\le 24$ hours** with near-zero transaction loss via continuous WAL archiving), PostgreSQL 16 must be configured with continuous Write-Ahead Log (WAL) archiving and periodic base backups.

### 5.1 Configuring PostgreSQL 16 for Continuous WAL Archiving
In `postgresql.conf` (or via Docker `command` arguments):
```ini
wal_level = replica
archive_mode = on
archive_command = 'test ! -f /var/lib/postgresql/wal_archive/%f && cp %p /var/lib/postgresql/wal_archive/%f'
archive_timeout = 300          # Force WAL segment switch at least every 5 minutes
max_wal_senders = 5
wal_keep_size = 512MB
```
Create the archive directory inside the PostgreSQL volume and reload configuration:
```bash
docker exec ttpu-postgres mkdir -p /var/lib/postgresql/wal_archive /var/lib/postgresql/base_backups
docker exec ttpu-postgres psql -U ttpu -d ttpu_inventory -c "ALTER SYSTEM SET archive_mode = 'on';"
docker exec ttpu-postgres psql -U ttpu -d ttpu_inventory -c "ALTER SYSTEM SET archive_command = 'cp %p /var/lib/postgresql/wal_archive/%f';"
```

### 5.2 Taking a Physical Base Backup + WAL Switch
Run `pg_basebackup` in compressed tar format with checkpointing and force a WAL log switch:
```bash
BACKUP_ID=$(date -u +"%Y%m%dT%H%M%SZ")
docker exec ttpu-postgres pg_basebackup \
  -U ttpu \
  -D "/var/lib/postgresql/base_backups/backup_${BACKUP_ID}" \
  -Ft -z -X fetch -P

# Force archival of the current WAL segment
docker exec ttpu-postgres psql -U ttpu -d ttpu_inventory -c "SELECT pg_switch_wal();"
```

### 5.3 Executing a Point-in-Time Recovery (PITR) Restore Drill
Run the automated drill script included in the repository:
```bash
./scripts/wal-backup-restore-drill.sh
```
Or perform the manual PITR restore steps during a scheduled quarterly maintenance drill:
1. **Record Target Recovery Timestamp**:
   ```bash
   RECOVERY_TARGET=$(date -u +"%Y-%m-%d %H:%M:%S UTC")
   ```
2. **Stop PostgreSQL & Extract Base Backup**:
   ```bash
   # On the recovery instance / drill container:
   rm -rf /var/lib/postgresql/data/*
   tar -xzf /var/lib/postgresql/base_backups/backup_<BACKUP_ID>/base.tar.gz -C /var/lib/postgresql/data
   tar -xzf /var/lib/postgresql/base_backups/backup_<BACKUP_ID>/pg_wal.tar.gz -C /var/lib/postgresql/data/pg_wal
   ```
3. **Configure `restore_command` and `recovery.signal`**:
   ```bash
   cat <<EOF >> /var/lib/postgresql/data/postgresql.auto.conf
   restore_command = 'cp /var/lib/postgresql/wal_archive/%f %p'
   recovery_target_time = '${RECOVERY_TARGET}'
   recovery_target_action = 'promote'
   EOF
   touch /var/lib/postgresql/data/recovery.signal
   ```
4. **Start PostgreSQL and Verify Data Integrity**:
   ```bash
   docker exec ttpu-postgres psql -U ttpu -d ttpu_inventory -c "
     SELECT COUNT(*) AS items_count FROM items;
     SELECT COUNT(*) AS movement_ledger_count FROM movement_records;
     SELECT COUNT(*) AS audit_records_count FROM audit_record;
   "
   ```
5. **Sign Off Drill Log**: Record the measured RTO (elapsed time from backup extraction to `/actuator/health` returning `UP`) and RPO in the IT Operations handover log.
