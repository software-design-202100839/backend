# Phase 1: 인프라 셋업 (Kafka + Analytics DB)

> 작성일: 2026-05-24
> 상태: 완료

## 목표

OLAP 시스템의 기반 인프라를 구성한다.
- Kafka (메시지 브로커) + Zookeeper (Kafka 메타데이터 관리)
- Analytics DB (분석 전용 PostgreSQL, 운영 DB와 물리적 분리)
- Spring Boot에서 위 인프라에 접속하기 위한 설정

## 왜 이 작업이 필요한가?

OLAP의 핵심은 **운영 데이터와 분석 데이터의 분리**다.
운영 DB에서 직접 집계 쿼리를 돌리면 서비스 성능에 영향을 준다.
그래서:
1. **Kafka**: 도메인 이벤트("성적이 바뀌었다")를 전달하는 다리
2. **Analytics DB**: 집계된 결과를 저장하는 별도 DB
3. **Spring 설정**: 앱이 Kafka와 Analytics DB에 접속할 수 있도록

---

## 변경 파일 목록

| 파일 | 작업 | 설명 |
|------|------|------|
| `docker-compose.yml` | 수정 | Zookeeper, Kafka, Analytics DB 컨테이너 추가 |
| `build.gradle.kts` | 수정 | spring-kafka 의존성 추가 |
| `application-dev.yml` | 수정 | Kafka, Analytics DB 연결 설정 추가 |
| `analytics-schema.sql` | 신규 | 분석 DB 테이블 6개 정의 |
| `KafkaConfig.java` | 신규 | Kafka 토픽 자동 생성 설정 |
| `AnalyticsDataSourceConfig.java` | 신규 | 분석 DB DataSource + JdbcTemplate 빈 등록 |

---

## 핵심 개념 정리

### Kafka 구성 요소

```
Producer (Spring Boot)  →  Kafka Broker  →  Consumer (Spring Boot)
    메시지 발행              메시지 저장         메시지 수신/처리
```

- **Broker**: 메시지를 저장하고 전달하는 서버 (Docker 컨테이너)
- **Topic**: 메시지의 카테고리 (예: sscm.scores = 성적 이벤트)
- **Partition**: 토픽 내부의 병렬 처리 통로. 같은 Key → 같은 파티션 → 순서 보장
- **Consumer Group**: 같은 그룹의 Consumer끼리 파티션을 나눠서 처리
- **Zookeeper**: Kafka 클러스터의 메타데이터(토픽 설정, 브로커 상태)를 관리

### 직렬화 (Serialization)

Java 객체를 네트워크로 보내려면 바이트로 변환해야 한다.
- Key: `StringSerializer` (studentId를 문자열로)
- Value: `JsonSerializer` (Java 객체 → JSON → 바이트)

이건 Kafka에만 해당하는 게 아니라, REST API(Jackson), WebSocket, DB 저장 등
**데이터가 프로세스 밖으로 나갈 때는 항상 직렬화가 필요**하다.
대부분의 프레임워크가 자동으로 해줘서 의식하지 못할 뿐.

### 운영 DB vs 분석 DB

| | 운영 DB (port 5432) | 분석 DB (port 5433) |
|---|---|---|
| DB명 | sscm | sscm_analytics |
| 용도 | 서비스 트랜잭션 (CRUD) | 집계 데이터 조회 (대시보드) |
| 접근 방식 | JPA (Hibernate) | JdbcTemplate (SQL 직접 실행) |
| 데이터 형태 | 개별 row (성적 1건 1건) | 집계 결과 (학생별 학기 평균) |

### @Qualifier — 같은 타입의 빈이 여럿일 때

JdbcTemplate이 2개 존재하므로 (운영 DB용, 분석 DB용)
`@Qualifier("analyticsJdbc")`로 이름표를 붙여 구분한다.

---

## Docker 포트 맵

```
5432  → 운영 DB (PostgreSQL, 기존)
5433  → 분석 DB (PostgreSQL, 신규)
9092  → Kafka Broker (신규)
2181  → Zookeeper (신규)
9090  → Prometheus (기존)
3000  → Grafana (기존)
8080  → Spring Boot 앱
```

---

## Analytics DB 테이블 설계

### 6개 테이블과 역할

```
운영 DB (개별 데이터)              Analytics DB (집계 데이터)
─────────────────                ──────────────────────────
scores (성적 1건 1건)        →   student_score_summary (학생별 학기 평균)
                              →   subject_statistics (과목별 평균/분포)
student_records (기록 1건)   →   student_attendance_summary (카테고리별 건수)
feedbacks (피드백 1건)       →   student_feedback_summary (카테고리별 건수)
counselings (상담 1건)       →   student_counseling_summary (카테고리별 건수)
위 4개 테이블 종합           →   student_learning_dashboard (한눈에 보기)
```

### UNIQUE 제약조건의 의미
모든 테이블에 `UNIQUE (student_id, academic_year, semester)` 제약이 있다.
이건 나중에 Consumer가 `INSERT ... ON CONFLICT DO UPDATE` (upsert)를 쓸 때,
"이미 같은 학생+학기 데이터가 있으면 새로 넣지 말고 기존 값을 갱신"하기 위한 기준.

---

## Kafka 토픽 설계

| Topic | Partition Key | 발행 시점 | Partitions |
|-------|--------------|-----------|------------|
| `sscm.scores` | studentId | 성적 등록/수정/삭제 | 3 |
| `sscm.feedbacks` | studentId | 피드백 등록 | 3 |
| `sscm.records` | studentId | 학생부 등록 | 3 |
| `sscm.counselings` | studentId | 상담 등록/수정 | 3 |

Partition Key로 studentId를 쓰는 이유:
같은 학생의 이벤트는 같은 파티션에 들어가서 **처리 순서가 보장**된다.

---

## 검증 방법

```bash
# 1. 인프라 기동
docker-compose up -d

# 2. 각 서비스 상태 확인
docker ps  # 5개 컨테이너 모두 Up 상태인지

# 3. Kafka 토픽 확인 (앱 시작 후)
docker exec sscm-kafka kafka-topics --bootstrap-server localhost:9092 --list

# 4. Analytics DB 테이블 확인
docker exec sscm-postgres-analytics psql -U sscm -d sscm_analytics -c "\dt"

# 5. 빌드 확인
./gradlew compileJava
```
