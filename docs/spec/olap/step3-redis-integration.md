# Step 3: Redis 도입 — JWT 블랙리스트 캐시

> 작성일: 2026-05-26
> 목적: 매 API 요청마다 발생하는 DB SELECT를 Redis 캐시로 대체하여 응답시간 개선

---

## 문제 (Before)

```
매 API 요청 → JwtAuthenticationFilter
            → tokenBlacklistService.isBlacklisted()
            → DB SELECT (PostgreSQL)
            → ~2-5ms + 커넥션 점유
```

- 200 동시 사용자 시 초당 ~200회 DB SELECT
- HikariCP 커넥션 풀 고갈 위험
- Before 부하 테스트: p95 = **906ms**

---

## 해결 (After)

```
매 API 요청 → JwtAuthenticationFilter
            → tokenBlacklistService.isBlacklisted()
            → Redis GET (<1ms)
            → 캐시 miss 시에만 DB SELECT (fallback)
```

### 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `build.gradle.kts` | `spring-boot-starter-data-redis` 의존성 추가 |
| `TokenBlacklistService.java` | Redis 1차 조회 + DB 2차 fallback 패턴 |
| `application-prod.yml` | Redis host/port 설정 추가 |
| `application-dev.yml` | Redis host/port 설정 추가 |
| `docker-compose.yml` | Redis 7 Alpine 컨테이너 추가 |
| `TokenBlacklistServiceTest.java` | Redis mock 추가, 6개 테스트 케이스 |

---

## 캐시 전략

| 동작 | 흐름 |
|------|------|
| isBlacklisted() | Redis hasKey → hit: return true / miss: DB 조회 → 블랙리스트면 Redis에 캐시 저장 |
| addToBlacklist() | DB 저장 + Redis SET (TTL = 토큰 만료 시간) |
| Redis 장애 시 | DB fallback (느려질 뿐 서비스 중단 없음) |

### TTL 정책
- `addToBlacklist()`: 토큰 만료 시간까지 (자연 소멸)
- `isBlacklisted()` 캐시 miss 복구: 1시간 고정

---

## 왜 Redis? (수치 근거)

| 항목 | DB (PostgreSQL) | Redis |
|------|-----------------|-------|
| 조회 시간 | ~2-5ms | <1ms |
| 초당 처리량 | 커넥션 풀 한계 (20개) | 100,000+ ops/sec |
| 커넥션 점유 | 요청당 1개 점유 | 별도 경량 연결 |
| 장애 영향 | DB 전체 장애 시 서비스 중단 | Redis 장애 시 DB fallback |

Redis 공식 벤치마크: GET/SET <1ms, 100K+ ops/sec (출처: redis.io)

---

## 테스트

6개 테스트 케이스:
1. addToBlacklist — DB + Redis 동시 저장
2. addToBlacklist — DB 중복 시 Redis만 저장
3. isBlacklisted — Redis hit → DB 조회 안 함
4. isBlacklisted — Redis miss → DB 조회 → 캐시 저장
5. isBlacklisted — 둘 다 miss → false
6. isBlacklisted — Redis 장애 → DB fallback

---

## AWS 인프라

- ElastiCache Redis (cache.t3.micro): `sscm-data` CloudFormation 스택에 이미 생성됨
- SSM 파라미터: `/sscm/prod/redis-host` → ECS Task Definition에서 `REDIS_HOST` 환경변수로 주입
