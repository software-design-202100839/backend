# 백업/복구 절차

> Last updated: 2026-04-09

## 대상

| 서비스 | 백업 필요 | 이유 |
|--------|-----------|------|
| RDS PostgreSQL | **O** | 사용자/성적/상담 등 영구 데이터 |
| ElastiCache Redis | X | JWT 토큰 캐시 전용, TTL 자동 만료. 유실 시 재로그인으로 복구 |
| ECS (컨테이너) | X | 상태 없음. ECR 이미지 + CloudFormation으로 재생성 |
| ALB/CloudFormation | X | 템플릿(`infra/`)이 코드로 관리됨 |

## 1. RDS 자동 백업

### 설정
- `BackupRetentionPeriod: 7` (7일 보존)
- `PreferredBackupWindow: 18:00-19:00` (UTC, 한국 새벽 3~4시)
- 스토리지: DB 크기까지 무료 (현재 20GB 할당)

### 확인
```bash
aws rds describe-db-instances \
  --db-instance-identifier sscm-db \
  --query 'DBInstances[0].{Backup:BackupRetentionPeriod,Window:PreferredBackupWindow,Latest:LatestRestorableTime}' \
  --output table
```

## 2. 수동 스냅샷 (배포 전)

### 생성
```bash
# 배포 전 스냅샷 생성
aws rds create-db-snapshot \
  --db-instance-identifier sscm-db \
  --db-snapshot-identifier sscm-db-pre-deploy-$(date +%Y%m%d-%H%M)

# 완료 대기
aws rds wait db-snapshot-available \
  --db-snapshot-identifier sscm-db-pre-deploy-$(date +%Y%m%d-%H%M)
```

### 스냅샷 목록 확인
```bash
aws rds describe-db-snapshots \
  --db-instance-identifier sscm-db \
  --query 'DBSnapshots[].{ID:DBSnapshotIdentifier,Status:Status,Created:SnapshotCreateTime,Size:AllocatedStorage}' \
  --output table
```

## 3. 복구 절차

### 3a. 자동 백업에서 특정 시점 복구 (Point-in-Time Recovery)
```bash
# 새 인스턴스로 복구 (기존 인스턴스를 덮어쓰지 않음)
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier sscm-db \
  --target-db-instance-identifier sscm-db-restored \
  --restore-time "2026-04-09T03:00:00Z" \
  --db-instance-class db.t3.micro \
  --no-publicly-accessible

# 복구 인스턴스 준비 대기
aws rds wait db-instance-available \
  --db-instance-identifier sscm-db-restored
```

### 3b. 스냅샷에서 복구
```bash
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier sscm-db-restored \
  --db-snapshot-identifier sscm-db-pre-deploy-20260409-1200 \
  --db-instance-class db.t3.micro \
  --no-publicly-accessible

aws rds wait db-instance-available \
  --db-instance-identifier sscm-db-restored
```

### 3c. 복구 후 전환
```bash
# 1. 복구된 인스턴스의 엔드포인트 확인
aws rds describe-db-instances \
  --db-instance-identifier sscm-db-restored \
  --query 'DBInstances[0].Endpoint.Address' --output text

# 2. Parameter Store의 DB_URL 업데이트
aws ssm put-parameter \
  --name /sscm/prod/db-url \
  --value "jdbc:postgresql://<복구-엔드포인트>:5432/sscm" \
  --overwrite

# 3. ECS 서비스 재시작 (새 DB 연결)
aws ecs update-service --cluster sscm-cluster --service sscm-backend --force-new-deployment

# 4. 확인 후 기존 인스턴스 삭제
aws rds delete-db-instance \
  --db-instance-identifier sscm-db \
  --skip-final-snapshot
```

## 4. Flyway 마이그레이션 롤백

Flyway는 기본적으로 롤백을 지원하지 않으므로, **되돌리기 마이그레이션**으로 처리:

```bash
# 현재 마이그레이션 상태 확인
./gradlew flywayInfo

# 롤백이 필요하면: RDS 스냅샷 복구가 가장 안전
# 또는: V{N+1}__rollback_{description}.sql 작성
```

## 5. 전체 인프라 재구축

모든 인프라가 CloudFormation 코드로 관리되므로 전체 재구축 가능:

```bash
# 순서: data → alb → ecs → monitoring
# 상세: infra/README.md 참조
```

**주의**: RDS를 삭제하면 데이터가 사라짐. 반드시 스냅샷 생성 후 삭제할 것.
