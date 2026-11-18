#!/usr/bin/env bash
#
# Back up every service database, verify the dump is readable, and prune old dumps by age.
#
# This is the piece the deployment doc left to the operator: a scheduled job that produces a dump,
# proves it is not truncated, and keeps a bounded number of them. It is written to be run from cron
# on the host that runs the compose stack:
#
#   0 2 * * *  /srv/choco/scripts/backup-databases.sh >> /var/log/choco-backup.log 2>&1
#
# Configuration (all optional, sensible defaults):
#   BACKUP_DIR         where dumps are written            (default ./backups)
#   BACKUP_RETENTION   days to keep                       (default 14)
#   POSTGRES_CONTAINER the postgres container name        (default choco-postgres)
#   POSTGRES_USER      the superuser to dump as           (default choco_root)
#
# Exit status is non-zero on any failure, so cron or a wrapper can alert on it. A backup that cannot
# be read back is treated as a failure, not a success: an unreadable dump is worse than no dump,
# because it is trusted.

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
BACKUP_RETENTION="${BACKUP_RETENTION:-14}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-choco-postgres}"
POSTGRES_USER="${POSTGRES_USER:-choco_root}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump="${BACKUP_DIR}/choco-${timestamp}.sql"

mkdir -p "${BACKUP_DIR}"

if ! docker inspect "${POSTGRES_CONTAINER}" >/dev/null 2>&1; then
  echo "ERROR: container ${POSTGRES_CONTAINER} not found" >&2
  exit 1
fi

echo "Backing up all databases to ${dump}"
docker exec "${POSTGRES_CONTAINER}" pg_dumpall -U "${POSTGRES_USER}" > "${dump}"

# Prove the dump is readable and contains every service database. A truncated dump — the failure
# mode a full disk produces — would otherwise sit here looking like a backup. `pg_restore -l` is for
# custom-format dumps; this is plain SQL, so read the list of databases it will recreate.
if ! grep -q "CREATE DATABASE choco_identity" "${dump}"; then
  echo "ERROR: ${dump} does not contain the identity database — refusing to keep it" >&2
  rm -f "${dump}"
  exit 1
fi

for db in choco_identity choco_catalog choco_order choco_payment choco_fulfilment choco_notification choco_reporting; do
  if ! grep -q "CREATE DATABASE ${db}" "${dump}"; then
    echo "ERROR: ${dump} is missing ${db} — refusing to keep it" >&2
    rm -f "${dump}"
    exit 1
  fi
done

size="$(du -h "${dump}" | cut -f1)"
echo "Backup OK: ${dump} (${size}), all seven databases present"

# Prune by age. Retention is a count of days, and the dump filename carries a UTC date, so `find` on
# the file's own mtime is both simpler and more robust than parsing the name.
echo "Pruning dumps older than ${BACKUP_RETENTION} days in ${BACKUP_DIR}"
find "${BACKUP_DIR}" -maxdepth 1 -name 'choco-*.sql' -type f -mtime "+${BACKUP_RETENTION}" -print -delete

echo "Done. $(find "${BACKUP_DIR}" -maxdepth 1 -name 'choco-*.sql' -type f | wc -l) dump(s) retained."
