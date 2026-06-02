# SSCM 인프라 & 배포

## 운영 원칙

| 구분 | 방식 | 도구 |
|------|------|------|
| 인프라 프로비저닝 | CloudFormation **수동 실행** (AWS CLI) | `infra/cfn-*.yml` |
| 애플리케이션 배포 | GitHub Actions **수동 workflow** | `.github/workflows/deploy-app.yml` |
| CI (테스트/분석) | push/PR 시 자동 실행 | `.github/workflows/ci.yml` |
| 시드/backfill | 배포 후 **수동 curl** | DevSeedController, AnalyticsAdminController |
| 비용 관리 | 제출 기간에만 AWS 인프라 운영. 이후 스택 삭제. | |

---

## 1. 인프라 프로비저닝 (CloudFormation 수동)

### 스택 구성 및 생성 순서

| 순서 | 스택 | 파일 | 소요 | 비고 |
|:---:|------|------|------|------|
| 1 | sscm-data | `cfn-data.yml` | ~15분 | RDS x2 + ElastiCache Redis |
| 2 | sscm-msk | `cfn-msk.yml` | ~30~45분 | MSK Kafka (병목) |
| 3 | sscm-alb | `cfn-alb.yml` | ~5분 | ALB + SSM Parameter Store |
| 4 | sscm-ecs | `cfn-ecs-cluster.yml` | ~5분 | ECS Backend Task |
| 5 | sscm-cdn | `cfn-cdn.yml` | ~5분 | S3 + CloudFront |
| 6 | sscm-monitoring | `cfn-monitoring.yml` | ~5분 | Prometheus + Grafana |

> Step 1과 2는 병렬 시작 가능. 상세 명령어는 `docs/spec/infra/teardown-recreation.md` 참조.

### 재생성 시 하드코딩 업데이트 (3곳)

| 파일 | 변경 값 |
|------|---------|
| `infra/monitoring/prometheus-prod.yml` | ALB DNS |
| `SecurityConfig.java` | CloudFront 도메인 (CORS) |
| 프론트엔드 `.env` 또는 빌드 설정 | API base URL |

### 스택 삭제 (역순)

```bash
aws cloudformation delete-stack --stack-name sscm-monitoring
aws cloudformation delete-stack --stack-name sscm-cdn
aws cloudformation delete-stack --stack-name sscm-ecs
aws cloudformation delete-stack --stack-name sscm-alb
aws cloudformation delete-stack --stack-name sscm-msk
aws cloudformation delete-stack --stack-name sscm-data
```

---

## 2. 애플리케이션 배포 (GitHub Actions 수동)

### deploy-app.yml

`workflow_dispatch`로 수동 실행. 배포 대상: `all`, `backend`, `frontend` 선택.

**Backend:**
```
Checkout → Docker build → ECR push → ECS force-new-deployment → Health check
```

**Frontend:**
```
Checkout frontend repo → npm build → S3 sync → CloudFront invalidation
```

---

## 3. CI (자동)

### ci.yml

push/PR to `develop`/`main` 시 자동 실행.

```
Gradle test → JaCoCo → SonarCloud → OWASP Dependency Check
```

---

## 4. 시드 / Backfill / 임베딩 (수동)

인프라 + 애플리케이션 배포 완료 후 순서대로 실행.

```bash
ALB_DNS=<ALB DNS>

# 1. 대규모 시드 (3개 학교, 3,000명, 성적 90,000건)
curl -X POST "http://$ALB_DNS/api/v1/dev/seed/large-scale?reset=true&key=sscm-seed-2026"

# 2. Analytics Backfill
TOKEN=$(curl -s -X POST "http://$ALB_DNS/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@sscm.dev","password":"admin1234"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

curl -X POST "http://$ALB_DNS/api/v1/analytics/admin/backfill" \
  -H "Authorization: Bearer $TOKEN"

# 3. 임베딩 생성 (RAG 데모용, 일부)
curl -X POST "http://$ALB_DNS/api/v1/dev/seed/embeddings?key=sscm-seed-2026"
```

---

## ALB 라우팅 규칙

| 우선순위 | 경로 | 대상 |
|----------|------|------|
| 5 | `/grafana/*` | Monitoring (3000) |
| 10 | `/api/*` | Backend (8080) |
| 20 | `/ws/*` | Backend (8080) |
| 30 | `/actuator/*` | Backend (8080) |
| default | `/*` | 404 Fixed Response |

> 프론트엔드는 CloudFront → S3로 서빙. ALB에 프론트엔드 라우팅 없음.

## Cross-Stack Reference

```
cfn-alb.yml
  ├── sscm-alb-sg-id        → cfn-ecs-cluster, cfn-monitoring
  ├── sscm-backend-tg-arn   → cfn-ecs-cluster
  └── sscm-http-listener-arn → cfn-monitoring
```
