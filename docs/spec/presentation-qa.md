# 발표 Q&A 대비 — 기술 트레이드오프 정리

> 발표 10분 + 질의 30분 대비
> 원칙: 모든 기술 선택에 대안 비교 + 수치 근거. "러닝커브" 절대 이유 불가.

---

## 1. 아키텍처 선택

### Q: 왜 ECS Fargate? (vs K8s, vs EC2)

| 대안 | 선택하지 않은 이유 (수치) |
|------|--------------------------|
| EKS (K8s) | control plane $0.10/hr 추가. 모놀리식 1개 서비스에 K8s는 순수 오버헤드. 노드 관리 필요 |
| EC2 직접 | 배포 시 수동 blue-green 필요. Fargate는 rolling update 기본 제공. 인스턴스 패치 불필요 |
| **ECS Fargate** | 서버리스. Auto Scaling (CPU 70% 기준, 1→3). zero-downtime 배포. 시간당 ~$0.03/Task |

### Q: 왜 ALB? (vs Nginx, vs API Gateway)

| 대안 | 선택하지 않은 이유 |
|------|-------------------|
| Nginx | EC2에서는 적합하지만, ECS에서는 ALB가 경로 라우팅 + 로드밸런싱을 동시에 해줌. 역할 중복 |
| API Gateway | REST API에는 과잉. WebSocket 지원 제한. 요청당 과금 ($3.50/백만 요청) |
| **ALB** | 경로 기반 라우팅 (/api/*, /ws/*, /grafana/*). ECS 타겟 자동 등록. $0.02/hr |

### Q: 왜 S3 + CloudFront? (vs ECS로 React 서빙)

| 항목 | ECS 서빙 | S3 + CloudFront |
|------|---------|-----------------|
| 비용 | ~$0.03/hr (컨테이너) | 거의 무료 (1TB/월 무료) |
| 레이턴시 | 서울 리전 단일 | CDN 엣지 400+ 리전 |
| 자원 | CPU/메모리 소비 | 없음 (정적 파일) |

React는 빌드하면 HTML/JS/CSS. 이걸 컨테이너에서 서빙하는 건 자원 낭비.

---

## 2. OLAP / Kafka

### Q: 왜 DB를 분리했는가? (vs 같은 DB)

> "분석 쿼리(GROUP BY + 다중 JOIN)가 운영 트랜잭션과 자원 경합합니다.
> 성적 입력 API 응답시간이 분석 쿼리 실행 중 증가하는 걸 방지하기 위해 물리적으로 분리했습니다."

실측: 부하 테스트에서 분석 DB 쿼리 ~300ms. 이게 운영 DB에서 돌면 성적 입력도 300ms 지연.

### Q: 왜 Kafka? (vs 직접 INSERT)

```
직접 INSERT: 서비스 → 분석 DB INSERT
  → 분석 DB timeout 시 서비스도 실패 (장애 전파)

Kafka: 서비스 → Kafka → Consumer → 분석 DB
  → 분석 DB 장애 시 서비스 정상 (장애 격리)
  → backfill API로 복구 가능
```

### Q: 왜 Kafka? (vs CDC/Debezium)

| 항목 | Kafka (이벤트 기반) | CDC (Debezium) |
|------|-------------------|----------------|
| 이벤트 의미 | "성적이 등록됨" (비즈니스 컨텍스트) | "scores 테이블 row 변경" |
| Consumer 로직 | 이벤트 타입별 분기 가능 | row 변경만 알 수 있음 |
| 설정 복잡도 | Spring Kafka (코드 레벨) | Debezium + Kafka Connect 별도 운영 |

### Q: 왜 MSK? (vs 자체 Kafka 운영)

| 항목 | 자체 운영 | MSK |
|------|----------|-----|
| 가용성 | 직접 관리 | 99.9% SLA |
| 브로커 패치 | 수동 | 자동 |
| 스토리지 | 수동 확장 | 자동 확장 |
| 비용 | EC2 인스턴스 | $0.15/hr (kafka.t3.small x2) |

Kafka는 stateful 워크로드. 브로커 장애 시 파티션 리밸런싱, 스토리지 관리를 AWS에 위임.

### Q: Kafka가 죽으면?

> "운영 서비스는 정상 동작합니다. 이벤트만 유실되고, backfill API(`POST /analytics/admin/backfill`)로 운영 DB에서 재집계하면 복구됩니다. 전교생 30명 기준 복구 시간 ~수 초."

### Q: 실시간이 아닌데?

> "Eventual consistency입니다. 대시보드는 수 초~분 지연을 허용하는 도메인입니다. Consumer lag이 0이면 준실시간."

---

## 3. Redis

### Q: 왜 Redis를 도입했는가?

> "JWT 블랙리스트 체크가 매 API 요청마다 발생합니다. DB 조회 시 ~2-5ms + 커넥션 점유. 동시 접속자가 늘면 커넥션 풀 고갈 위험이 있습니다."

| 항목 | PostgreSQL | Redis |
|------|-----------|-------|
| 조회 시간 | ~2-5ms | <1ms (공식 벤치마크) |
| 초당 처리량 | 커넥션 풀 한계 (20개) | 100,000+ ops/sec |
| 장애 시 | 서비스 전체 영향 | DB fallback (느려질 뿐) |

### Q: 부하 테스트에서 Redis 효과가 미미하던데?

> "200 VU 테스트에서 병목이 분석 DB 쿼리(~300ms)에 있어 JWT 캐시(2-5ms→1ms)의 효과가 체감되지 않았습니다. Redis 도입은 현재 성능 개선보다 **확장성 확보** 목적입니다. 커넥션 풀 고갈 방지 + 다중 인스턴스 블랙리스트 공유."

### Q: 응답 캐시는 왜 안 했어?

> "도입해봤습니다. 하지만 0.5 vCPU 환경에서 ObjectMapper 직렬화 오버헤드가 DB 절약분보다 커서 p95가 791ms → 1,400ms로 오히려 악화됐습니다. 측정 결과를 보고 제거했습니다. **캐시가 항상 답은 아닙니다.**"

---

## 4. 모니터링

### Q: 모니터링에서 뭘 봐요?

**3계층 19개 패널**로 구성된 Grafana 대시보드에서 봅니다:

1. **앱 계층 (10패널)** — API p95 응답시간, HTTP 처리량, 5xx 에러, JVM Heap, GC 일시정지, HikariCP 커넥션풀, Kafka Consumer Lag, 스레드 수, Uptime, CPU
2. **비즈니스 계층 (5패널)** — 성적 등록, 상담 생성, 피드백, 알림, AI 챗봇 사용 (도메인 이벤트 기반 커스텀 메트릭)
3. **인프라 계층 (4패널)** — CloudWatch 연동: ECS CPU/메모리, RDS 커넥션, Redis 메모리 사용량

부하를 올리면서 특히 주목하는 지표:
- **API p95 응답시간** — 사용자 95%의 체감 속도. 급등 지점 = 한계점
- **Kafka Consumer Lag** — 처리 못한 메시지 수. 계속 쌓이면 Consumer 스케일아웃 필요
- **DB 커넥션 풀 사용률** — HikariCP active/max. 90% 이상이면 커넥션 부족

### Q: 왜 3계층으로 나눴어요?

> "각 계층이 다른 관점을 커버합니다. 앱 계층은 개발자가 디버깅할 때, 비즈니스 계층은 서비스가 정상 동작하는지 확인할 때, 인프라 계층은 AWS 리소스 한계를 판단할 때 봅니다. 알림도 계층별로 다릅니다 — 5xx 에러는 즉시, Consumer Lag은 추세로 판단합니다."

### Q: 5개 알림 규칙은?

| 규칙 | 조건 | 의미 |
|------|------|------|
| 5xx 에러 | count > 0 | 서버 에러 발생 → 즉시 확인 |
| Heap 포화 | JVM Heap > 90% | 메모리 부족 → GC 튜닝 또는 증설 |
| 커넥션풀 고갈 | active > 90% | DB 커넥션 부족 → pool-size 또는 Redis 캐시 |
| Consumer 지연 | Lag 지속 증가 | Kafka 처리 못 따라감 → Consumer 스케일아웃 |
| 응답 급등 | p95 임계값 초과 | 성능 저하 → 쿼리 최적화, 스펙업 |

### Q: 지표가 이상하면 뭘 하는데?

| 지표 | 이상 | 대응 |
|------|------|------|
| p95 > 500ms | 응답 느림 | DB 쿼리 최적화, 캐시, 인스턴스 스펙업 |
| Consumer Lag 증가 | 처리 지연 | Consumer Task 수 증가, 파티션 추가 |
| 커넥션 풀 > 90% | 고갈 직전 | pool-size 조정, Redis 캐시로 DB 요청 감소 |
| JVM Heap > 90% | 메모리 부족 | GC 튜닝, 메모리 증설 |
| 5xx > 0 | 서버 에러 | CloudWatch 로그 확인 → 버그 수정 |

---

## 5. 멀티테넌시

### Q: 다중 학교는 어떻게 지원하나요?

> "Shared-Database, Discriminator-Column 전략입니다. users, classes 테이블에 school_id FK를 추가했습니다. JWT에 schoolId claim을 포함하고, TenantContext(ThreadLocal)로 서비스 계층에 전파합니다. 교차 학교 접근 시 403 차단합니다."

```
JWT → JwtAuthenticationFilter → TenantContext.set(schoolId)
  → 서비스 메서드에서 TenantContext.get()으로 school_id 접근
  → Repository에서 school_id 기반 필터링
  → 다른 학교 데이터 접근 시 AccessDeniedException (403)
```

### Q: 왜 Database-per-tenant이 아닌 Discriminator Column을 선택했나요?

| 전략 | 장점 | 단점 |
|------|------|------|
| Database-per-tenant | 완전한 데이터 격리 | 학교당 DB 인스턴스 필요. 수십~수백 학교면 비용 비현실적 |
| Schema-per-tenant | 중간 수준 격리 | 커넥션 풀 관리 복잡. 마이그레이션 N번 실행 |
| **Discriminator Column** | 기존 스키마 최소 변경. 비용 일정 | 쿼리에 WHERE school_id 필수 |

> "학교 수가 수십~수백 수준에서 DB-per-tenant은 RDS 인스턴스 비용이 학교 수에 비례합니다. school_id FK 추가만으로 기존 스키마를 최소 변경했고, TenantContext로 서비스 메서드 시그니처 변경 없이 전파합니다."

### Q: TenantContext 누수 위험은?

> "ThreadLocal 기반이므로 요청 종료 시 반드시 clear해야 합니다. JwtAuthenticationFilter의 finally 블록에서 TenantContext.clear()를 호출합니다. Kafka Consumer 등 비동기 컨텍스트에서는 이벤트에 schoolId를 포함하여 전달합니다."

---

## 6. AI 챗봇

### Q: AI 챗봇은 어떻게 구현했나요?

> "Spring AI + Gemini 2.5 Flash 기반입니다. Function Calling으로 13개 Tool을 연결했습니다. 역할별 접근 제어를 적용하여 교사는 13개 tool 전체, 학생/학부모는 5개 tool만 사용 가능합니다. 대화 히스토리는 인메모리로 관리하며 sessionId + 30분 TTL입니다."

### Q: 왜 Function Calling 방식인가요?

| 방식 | 설명 | 단점 |
|------|------|------|
| RAG (검색 증강) | DB 내용을 벡터화하여 검색 | 벡터 DB 추가 운영. 실시간 데이터 반영 어려움 |
| 직접 SQL 생성 | LLM이 SQL을 생성 | SQL Injection 위험. 스키마 노출 |
| **Function Calling** | LLM이 미리 정의된 Tool을 호출 | 안전. 기존 서비스 레이어 재활용. 접근 제어 적용 가능 |

> "Function Calling은 LLM이 기존 서비스 메서드를 호출하는 방식이라 접근 제어가 자연스럽게 적용됩니다. 새 기능 추가 시 Tool만 등록하면 됩니다."

### Q: 역할별 접근 제어는 어떻게?

| 역할 | 사용 가능 Tool 수 | 예시 |
|------|-------------------|------|
| 교사/관리자 | 13개 | 성적 조회/등록, 상담 기록, 분석 통계, 학생부 조회 등 전체 |
| 학생 | 5개 | 본인 성적 조회, 본인 피드백 조회 등 |
| 학부모 | 5개 | 자녀 성적 조회, 자녀 피드백 조회 등 |

> "교사용 Tool에는 전체 학생 데이터 접근이 포함되므로 학생/학부모에게 노출하면 프롬프트 조작으로 다른 학생 데이터 유출 가능성이 있습니다. Tool 등록 시점에 역할별 필터링을 적용합니다."

### Q: 대화 히스토리 관리는?

> "인메모리 ConcurrentHashMap에 sessionId별로 저장합니다. 30분 TTL로 만료됩니다. 영속화하지 않는 이유는 대화 내용에 학생 개인정보가 포함될 수 있어, 서버 재시작 시 자동 삭제되는 것이 보안상 유리하기 때문입니다."

---

## 7. 부하 테스트 수치

### 테스트 환경
- ECS Fargate 0.5 vCPU, 1GB
- RDS db.t3.micro x2
- ElastiCache cache.t3.micro
- MSK kafka.t3.small x2
- k6: 200 VU, 3분 30초

### 결과

| 항목 | Before (Redis 없음) | 최종 (Redis + 데이터) |
|------|---------------------|---------------------|
| p50 | 303.7ms | 297.4ms |
| p95 | 906.2ms | **791.2ms (13% 개선)** |
| 처리량 | 133 req/s | **140 req/s (5% 향상)** |
| 에러율 | 0% | 0% |

### 병목 분석
- 현재 병목: Analytics DB 쿼리 (~300ms)
- RDS db.t3.micro의 I/O 성능 한계
- 개선 방안: 인스턴스 스펙업, Read Replica, 쿼리 최적화

---

## 8. 보안

### Q: JWT 블랙리스트는 왜 필요?

> "Stateless JWT는 로그아웃 시 토큰을 무효화할 수 없습니다. 블랙리스트에 등록하여 로그아웃된 토큰의 재사용을 방지합니다."

### Q: 분석 데이터 접근제어는?

| 역할 | 접근 범위 |
|------|-----------|
| 교사/관리자 | 모든 학생 데이터 |
| 학생 | 본인 데이터만 |
| 학부모 | 자녀 데이터만 |

`AnalyticsAccessChecker`에서 역할별 검증. 8개 단위 테스트로 검증.

### Q: AI 챗봇 보안은?

> "역할별 Tool 접근 제어를 적용했습니다. 교사는 13개 Tool 전체 사용 가능하고, 학생/학부모는 본인/자녀 데이터만 접근 가능한 5개 Tool로 제한됩니다. Function Calling 방식이라 LLM이 직접 DB에 접근하지 않고, 기존 서비스 레이어의 접근 제어가 그대로 적용됩니다."

---

## 9. 확장성

### Q: 확장하려면?

1. **Backend**: ECS Auto Scaling (CPU 70%, 1→3 Task). 코드 변경 없음
2. **Kafka**: 파티션 수 증가 + Consumer Task 추가. 코드 변경 없음
3. **DB**: Read Replica 추가 또는 인스턴스 스펙업
4. **WebSocket**: 현재 인메모리 브로커 → 외부 브로커(RabbitMQ/Amazon MQ) 전환 필요

### Q: Auto Scaling 설정은?

> "CPU 평균 70% 초과 시 자동 스케일아웃, 최대 3 Task. 스케일아웃 쿨다운 60초, 스케일인 120초. CloudWatch Alarm이 자동 생성되어 모니터링됩니다."
