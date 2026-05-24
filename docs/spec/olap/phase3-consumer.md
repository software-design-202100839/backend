# Phase 3: Kafka Consumer + Analytics Repository

> 작성일: 2026-05-24
> 상태: 완료

## 목표

Kafka 토픽의 이벤트를 수신해서, 운영 DB에서 집계 쿼리를 실행하고, 결과를 분석 DB에 저장한다.

## 왜 이 작업이 필요한가?

Phase 2에서 도메인 서비스가 Kafka에 이벤트를 발행하도록 했다.
하지만 아직 **아무도 그 메시지를 받아서 처리하지 않는다.**
Consumer가 메시지를 수신하고, 운영 DB에서 집계한 결과를 분석 DB에 저장해야 대시보드에서 조회할 수 있다.

---

## 전체 흐름

```
Kafka 토픽 (sscm.scores)
  │
  └── @KafkaListener ──▶ ScoreAnalyticsConsumer.consume()
                            │
                            ├── analyticsRepo.upsertStudentScoreSummary()
                            │     ├── 운영 DB: SELECT AVG, SUM, COUNT ... FROM scores
                            │     └── 분석 DB: INSERT ... ON CONFLICT DO UPDATE
                            │
                            ├── analyticsRepo.upsertSubjectStatistics()
                            │     ├── 운영 DB: SELECT AVG, STDDEV, 등급분포 FROM scores
                            │     └── 분석 DB: INSERT ... ON CONFLICT DO UPDATE
                            │
                            └── analyticsRepo.upsertStudentDashboard()
                                  ├── 분석 DB: 다른 요약 테이블에서 읽기
                                  └── 분석 DB: 종합 대시보드 테이블에 upsert
```

---

## 변경 파일 목록

### 신규 파일

| 파일 | 역할 |
|------|------|
| `analytics/repository/AnalyticsJdbcRepository.java` | 운영 DB 집계 + 분석 DB upsert (핵심 SQL 로직) |
| `analytics/consumer/ScoreAnalyticsConsumer.java` | 성적 이벤트 수신 → 성적요약 + 과목통계 + 대시보드 갱신 |
| `analytics/consumer/FeedbackAnalyticsConsumer.java` | 피드백 이벤트 수신 → 피드백요약 + 대시보드 갱신 |
| `analytics/consumer/RecordAnalyticsConsumer.java` | 학생부 이벤트 수신 → 기록요약 + 대시보드 갱신 |
| `analytics/consumer/CounselingAnalyticsConsumer.java` | 상담 이벤트 수신 → 상담요약 + 대시보드 갱신 |
| `analytics/service/AnalyticsDataLoader.java` | 기존 운영 데이터를 분석 DB에 일괄 적재 (backfill) |

---

## 핵심 개념 정리

### Upsert (INSERT ... ON CONFLICT DO UPDATE)

"없으면 넣고, 있으면 갱신"을 한 번의 SQL로 처리하는 PostgreSQL 문법.

```sql
INSERT INTO student_score_summary
    (student_id, academic_year, semester, average_score, ...)
VALUES (5, 2026, 1, 85.00, ...)
ON CONFLICT (student_id, academic_year, semester)   -- 이미 있으면?
DO UPDATE SET                                        -- 갱신!
    average_score = EXCLUDED.average_score,
    updated_at = NOW()
```

- `EXCLUDED`: 새로 넣으려고 했던 값을 참조하는 키워드
- `UNIQUE` 제약조건이 있어야 `ON CONFLICT`가 동작함 (Phase 1에서 만들어둠)

왜 INSERT + 별도 UPDATE가 아닌가?
→ 동시에 같은 row를 처리할 때 race condition 방지. 하나의 SQL이라 원자적(atomic).

### @KafkaListener

```java
@KafkaListener(topics = "sscm.scores", groupId = "sscm-analytics")
public void consume(AnalyticsEvent<LinkedHashMap<String, Object>> event) { ... }
```

- `topics`: 어떤 Kafka 토픽을 구독할지
- `groupId`: Consumer Group 이름 (application-dev.yml에도 설정했지만, 어노테이션에서 명시적으로 지정)
- 메서드 파라미터로 메시지가 자동으로 역직렬화(JSON → Java 객체)되어 들어옴

### LinkedHashMap 사용 이유

Kafka에서 JSON을 역직렬화할 때, 제네릭 타입 정보가 런타임에 소거(type erasure)된다.
그래서 `AnalyticsEvent<ScoreEventPayload>`로 직접 받을 수 없고,
`AnalyticsEvent<LinkedHashMap<String, Object>>`로 받아서 필드를 직접 꺼낸다.

```java
var payload = event.getPayload();                    // LinkedHashMap
Long studentId = toLong(payload.get("studentId"));   // Map에서 꺼내기
```

### 두 개의 JdbcTemplate (핵심)

```java
public AnalyticsJdbcRepository(
        JdbcTemplate primaryJdbc,                          // 운영 DB (자동 주입)
        @Qualifier("analyticsJdbc") JdbcTemplate analyticsJdbc) {  // 분석 DB (수동 지정)
```

- `primaryJdbc`: Spring Boot가 자동 생성한 기본 JdbcTemplate → 운영 DB 연결
- `analyticsJdbc`: Phase 1에서 `@Qualifier("analyticsJdbc")`로 등록한 것 → 분석 DB 연결

Consumer 내부 흐름:
```
1. primaryJdbc로 운영 DB에서 SELECT 집계 (읽기)
2. analyticsJdbc로 분석 DB에 UPSERT (쓰기)
```

이것이 **물리적 DB 분리의 핵심**: 읽는 곳과 쓰는 곳이 다른 DB.

### Backfill (초기 데이터 적재)

Kafka Consumer는 이벤트가 발생할 때만 동작한다.
하지만 OLAP 도입 이전에 이미 쌓인 운영 데이터는 이벤트가 없으므로 분석 DB에 없다.

AnalyticsDataLoader가 하는 일:
```
1. 운영 DB에서 모든 학생+학기 조합을 조회
   SELECT DISTINCT student_id, year, semester FROM scores
   
2. 각 조합마다 집계 쿼리 실행 → 분석 DB에 upsert
   for (studentId, year, semester) {
       analyticsRepo.upsertStudentScoreSummary(studentId, year, semester);
   }
```

이건 Phase 4에서 관리자 API로 호출할 수 있게 할 예정.

---

## Consumer별 처리 내역

| Consumer | 토픽 | 갱신하는 분석 테이블 |
|----------|------|---------------------|
| ScoreAnalyticsConsumer | sscm.scores | student_score_summary, subject_statistics, student_learning_dashboard |
| FeedbackAnalyticsConsumer | sscm.feedbacks | student_feedback_summary, student_learning_dashboard |
| RecordAnalyticsConsumer | sscm.records | student_attendance_summary, student_learning_dashboard |
| CounselingAnalyticsConsumer | sscm.counselings | student_counseling_summary, student_learning_dashboard |

모든 Consumer가 마지막에 `upsertStudentDashboard()`를 호출한다.
→ 어떤 도메인이 변경되든 종합 대시보드는 항상 최신 상태 유지.

---

## 위험도(risk_level) 판정 기준

대시보드의 `risk_level`은 간단한 규칙으로 계산:

| 위험도 | 조건 |
|--------|------|
| HIGH | 평균 점수 60 미만 또는 피드백 5건 이상 |
| MEDIUM | 평균 점수 70 미만 |
| LOW | 그 외 |

이건 시연용 단순 로직이며, 실제로는 더 정교한 기준이 필요하다.

---

## 성적 추이(score_trend) 계산

이전 학기 평균과 현재 학기 평균을 비교:

| 추이 | 조건 |
|------|------|
| UP | 현재 평균 - 이전 평균 > 2점 |
| DOWN | 현재 평균 - 이전 평균 < -2점 |
| STABLE | 차이 2점 이내 |
| null | 이전 학기 데이터 없음 |

---

## 검증 방법

Phase 4 (Dashboard API) 완료 후 통합 테스트로 진행할 예정.

```bash
# 1. docker-compose up → Kafka, DB들 기동
# 2. Spring Boot 앱 시작
# 3. Swagger에서 성적 등록 API 호출
# 4. Kafka 토픽 메시지 확인
docker exec sscm-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sscm.scores --from-beginning

# 5. 분석 DB 직접 조회
docker exec sscm-postgres-analytics \
  psql -U sscm -d sscm_analytics \
  -c "SELECT * FROM student_score_summary"
```
