# Step 2: 부하 테스트 Before 수치 (Redis 도입 전)

> 측정일: 2026-05-26
> 환경: AWS ECS Fargate (0.5 vCPU, 1GB) + RDS (db.t3.micro) + MSK (kafka.t3.small x2)
> 도구: k6, 200 VU ramp-up, 3분 30초

---

## 결과 요약

### AWS 원격 환경

| 항목 | 값 |
|------|-----|
| p50 응답시간 | 303.7ms |
| p95 응답시간 | **906.2ms** |
| p99 (max) | 2,614ms |
| 에러율 (5xx) | 0% |
| 404 비율 | ~50% (분석 DB 데이터 미적재) |
| 처리량 | 133 req/s |
| 총 요청 수 | 28,373 |
| 동시 사용자 | 최대 200 VU |

### 로컬 환경 (참고)

| 항목 | 값 |
|------|-----|
| p50 응답시간 | 6.49ms |
| p95 응답시간 | 9.84ms |
| 에러율 | 0% |
| 처리량 | 222 req/s |
| 총 요청 수 | 49,117 |

---

## 분석

### 왜 AWS가 로컬보다 ~90배 느린가?
1. **네트워크 지연**: 로컬은 0ms, AWS는 클라이언트→ALB→ECS→RDS 경로
2. **인스턴스 스펙**: ECS 0.5 vCPU vs 로컬 멀티코어
3. **RDS 지연**: db.t3.micro의 I/O 성능 한계
4. **JWT 블랙리스트 DB 조회**: 매 요청마다 RDS SELECT → 병목

### 404 비율 ~50%
- 분석 DB(sscm_analytics)에 데이터가 적재되지 않은 상태
- score-summary, dashboard 등 분석 API가 데이터 없어 404 반환
- 이건 backfill 실행 후 해결됨 (에러가 아님)

### 임계치 초과
- 목표: p95 < 500ms
- 결과: p95 = 906ms → **목표 미달**
- Redis 캐시 도입으로 JWT 블랙리스트 조회 제거 시 개선 예상

---

## Before/After 비교용 기준선

| 지표 | Before (현재) | After (Redis 도입 후) |
|------|-------------|---------------------|
| p95 응답시간 | 906.2ms | 측정 예정 |
| 처리량 | 133 req/s | 측정 예정 |
| DB 커넥션 사용 | 매 요청 SELECT | Redis 캐시 hit 예상 |

---

## AWS 아키텍처 구성 (측정 당시)

| 서비스 | 스펙 | CloudFormation 스택 |
|--------|------|-------------------|
| ECS Fargate | 0.5 vCPU, 1GB, Task 1개 | sscm-ecs |
| ALB | 경로 기반 라우팅 | sscm-alb |
| RDS (운영) | db.t3.micro, PostgreSQL 16 | sscm-data |
| RDS (분석) | db.t3.micro, PostgreSQL 16 | sscm-data |
| ElastiCache Redis | cache.t3.micro (아직 미사용) | sscm-data |
| MSK | kafka.t3.small x2 | sscm-msk |
