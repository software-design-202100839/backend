#!/usr/bin/env bash
# SSCM PostgreSQL 백업 스크립트
# 용도: pg_dump → gzip → S3 업로드
# 실행: ./backup.sh  또는  cron: 0 2 * * * /opt/sscm/infra/backup.sh
# 필수 환경변수: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, S3_BUCKET

set -euo pipefail

: "${DB_HOST:?DB_HOST 환경변수가 필요합니다}"
: "${DB_PORT:=5432}"
: "${DB_NAME:?DB_NAME 환경변수가 필요합니다}"
: "${DB_USER:?DB_USER 환경변수가 필요합니다}"
: "${DB_PASSWORD:?DB_PASSWORD 환경변수가 필요합니다}"
: "${S3_BUCKET:?S3_BUCKET 환경변수가 필요합니다}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="/tmp/sscm_${DB_NAME}_${TIMESTAMP}.sql.gz"
S3_KEY="backups/sscm_${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "[$(date -Iseconds)] 백업 시작: ${DB_NAME}"

# pg_dump → gzip
PGPASSWORD="${DB_PASSWORD}" pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --no-owner \
  --no-privileges \
  -F plain \
  | gzip > "${BACKUP_FILE}"

echo "[$(date -Iseconds)] 덤프 완료: ${BACKUP_FILE}"

# S3 업로드
aws s3 cp "${BACKUP_FILE}" "s3://${S3_BUCKET}/${S3_KEY}" \
  --server-side-encryption AES256

echo "[$(date -Iseconds)] S3 업로드 완료: s3://${S3_BUCKET}/${S3_KEY}"

# 로컬 임시 파일 삭제
rm -f "${BACKUP_FILE}"

# 30일 이상 된 S3 백업 삭제 (비용 절감)
CUTOFF=$(date -d "30 days ago" +%Y-%m-%d 2>/dev/null || date -v-30d +%Y-%m-%d)
aws s3 ls "s3://${S3_BUCKET}/backups/" \
  | awk -v cutoff="${CUTOFF}" '$1 < cutoff {print $4}' \
  | while read -r key; do
      aws s3 rm "s3://${S3_BUCKET}/backups/${key}"
      echo "[$(date -Iseconds)] 만료 백업 삭제: ${key}"
    done

echo "[$(date -Iseconds)] 백업 완료"
