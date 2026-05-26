# 병목 개선 시도: Analytics API 응답 캐시 (도입 후 제거)

> 작성일: 2026-05-26
> 결론: 도입 후 부하 테스트에서 성능 악화 확인 → 제거

---

## 배경

부하 테스트에서 병목이 Analytics DB 쿼리(~300ms)임을 확인.
분석 데이터는 Kafka 이벤트 시에만 변경되므로, 반복 조회 시 Redis 캐시로 DB 쿼리를 건너뛸 수 있다고 판단.

---

## 구현 내용

- `AnalyticsDashboardService`의 `getScoreSummary()`, `getStudentDashboard()`에 Redis 캐시 적용
- 패턴: Redis GET(캐시 hit) → 없으면 DB 조회 → ObjectMapper로 직렬화 → Redis SET(TTL 5분)
- DTO에 `@NoArgsConstructor` + `@AllArgsConstructor` 추가 (Jackson 역직렬화용)

---

## 부하 테스트 결과

| 항목 | 캐시 없음 (이전) | 응답 캐시 적용 | 변화 |
|------|-----------------|---------------|------|
| p50 | 297ms | 602ms | +103% (악화) |
| p95 | 791ms | 1,400ms | +77% (악화) |
| 처리량 | 140 req/s | 99 req/s | -29% (악화) |

---

## 원인 분석

### 1. 캐시 hit 불가
- k6 스크립트가 student ID=1로 조회
- bulk seed 데이터는 student ID 5~34에 존재
- ID=1에는 분석 데이터 없음 → 매번 캐시 miss → 캐시 이점 없음

### 2. 캐시 miss 경로가 더 무거움
```
기존: DB 조회 → 응답
캐시 적용: Redis GET(miss) → DB 조회 → ObjectMapper 직렬화 → Redis SET → 응답
```
- 매 요청마다 Redis 왕복 2회 + ObjectMapper 직렬화 추가

### 3. CPU 병목 (0.5 vCPU)
- `ObjectMapper.writeValueAsString()` / `readValue()`는 CPU 집약적
- ECS Fargate 0.5 vCPU에서 직렬화 오버헤드가 DB 절약분보다 큼
- CPU 스펙이 1 vCPU 이상이면 결과가 달랐을 수 있음

---

## 결정: 제거

- 현재 인프라 스펙(0.5 vCPU)에서는 응답 캐시가 역효과
- **측정 없이 캐시를 도입하면 안 된다는 교훈**
- 코드에 주석으로 검토 결과와 제거 사유 기록

---

## 발표 포인트

> "Analytics API 응답 캐시를 Redis에 도입해봤습니다. 하지만 0.5 vCPU 환경에서
> ObjectMapper 직렬화 오버헤드가 DB 쿼리 절약분보다 커서, p95가 791ms → 1,400ms로
> 오히려 악화되었습니다. 측정 결과를 보고 제거했습니다.
>
> 캐시는 항상 답이 아닙니다. **인프라 스펙과 접근 패턴에 따라 판단**해야 합니다.
> CPU 스펙을 올리거나, 직렬화 없는 캐시 방식(예: Spring Cache + ConcurrentHashMap)을
> 사용하면 효과를 볼 수 있습니다."
