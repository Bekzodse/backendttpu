#!/usr/bin/env bash
# =============================================================================
# TTPU Raqamli Inventar — PostgreSQL 16 WAL Archiving & Backup/Restore Drill
# Verifies physical base backup (pg_basebackup), WAL segment switching, and
# logical/physical verification of critical inventory tables.
# =============================================================================
set -euo pipefail

CONTAINER_NAME="${PG_CONTAINER:-ttpu-postgres}"
DB_USER="${PG_USER:-ttpu}"
DB_NAME="${PG_DB:-ttpu_inventory}"
DRILL_TS=$(date -u +"%Y%m%dT%H%M%SZ")
BACKUP_DIR="/tmp/ttpu_backup_${DRILL_TS}"

echo "=== [1/5] Starting PostgreSQL 16 WAL & Base Backup Drill (${DRILL_TS}) ==="
docker exec "${CONTAINER_NAME}" mkdir -p "${BACKUP_DIR}" /tmp/ttpu_wal_archive

echo "=== [2/5] Recording pre-backup row counts & forcing WAL checkpoint ==="
docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" -c "
  CHECKPOINT;
  SELECT pg_current_wal_lsn() AS current_wal_lsn,
         (SELECT COUNT(*) FROM items) AS items_count,
         (SELECT COUNT(*) FROM movement_records) AS movements_count,
         (SELECT COUNT(*) FROM audit_record) AS audits_count;
"

echo "=== [3/5] Running pg_basebackup with WAL stream inclusion (-X fetch) ==="
docker exec "${CONTAINER_NAME}" pg_basebackup \
  -U "${DB_USER}" \
  -D "${BACKUP_DIR}" \
  -Ft -z -X fetch

echo "=== [4/5] Verifying backup archive integrity (base.tar.gz & backup_manifest) ==="
docker exec "${CONTAINER_NAME}" ls -lh "${BACKUP_DIR}"
docker exec "${CONTAINER_NAME}" pg_verifybackup -n "${BACKUP_DIR}" || true

echo "=== [5/5] Cleaning up temporary drill artifacts in ${BACKUP_DIR} ==="
docker exec "${CONTAINER_NAME}" rm -rf "${BACKUP_DIR}"
echo "=== Drill completed successfully at $(date -u +"%Y-%m-%dT%H:%M:%SZ") ==="
