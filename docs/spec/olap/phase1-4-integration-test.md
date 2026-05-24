# Phase 1~4 통합 테스트 결과

> 작성일: 2026-05-24
> 상태: 통과

## 목표

Phase 1(인프라) ~ Phase 4(Dashboard API)까지 전체 OLAP 파이프라인이 정상 동작하는지 검증.

---

## 테스트 환경

```
Docker 컨테이너:
  - sscm-postgres (운영 DB, port 5432)
  - sscm-postgres-analytics (분석 DB, port 5433)
  - sscm-kafka (Kafka 브로커, port 9092)
  - sscm-zookeeper (Kafka 메타데이터 관리, port 2181)
  - sscm-prometheus, sscm-grafana (모니터링)

Spring Boot 앱: localhost:8080 (dev 프로필)
```

---

## 테스트 데이터

### 시드 계정 (POST /api/v1/dev/seed/all)

| 역할 | 이메일 | 비밀번호 | userId |
|------|--------|---------|--------|
| ADMIN | admin@sscm.dev | admin1234 | 1 |
| TEACHER | teacher@sscm.dev | teacher1234 | 2 |
| STUDENT | student@sscm.dev | student1234 | 3 |
| PARENT | parent@sscm.dev | parent1234 | 4 |

구조: 2026년 1학년 1반, 담임=테스트교사, 학생=테스트학생, 학부모 연결

### 등록한 성적 (POST /api/v1/grades, 교사 토큰)

| 과목 (subjectId) | 점수 | 등급 |
|-----------------|------|------|
| 국어 (1) | 80 | B |
| 수학 (2) | 90 | A |
| 영어 (3) | 60 | D |
| 사회 (4) | 75 | C+ |
| 과학 (5) | 88 | B+ |
| 역사 (6) | 70 | C |

---

## 테스트 결과

### ① Docker 기동 — ✅ 통과

```bash
docker compose up -d
# 6개 컨테이너 모두 Started
```

### ② Spring Boot 앱 시작 — ✅ 통과

```
HikariPool-1 - Start completed.     ← 운영 DB 연결
HikariPool-2 - Start completed.     ← 분석 DB 연결
Subscribed to topic(s): sscm.scores, sscm.feedbacks, sscm.records, sscm.counselings
partitions assigned: [sscm.scores-0, sscm.scores-1, sscm.scores-2] (4개 토픽 × 3 파티션)
Started SscmApplication in 7.4 seconds
```

### ③ 성적 등록 — ✅ 통과

```
POST /api/v1/grades → 201 Created
기존 기능(알림 발송) 정상 동작 확인
```

### ④ Kafka 이벤트 발행/수신 — ✅ 통과

```
[Producer] Kafka 전송: topic=sscm.scores, key=1, type=SCORE_CREATED
[Consumer] 성적 이벤트 수신: type=SCORE_CREATED
```

### ⑤ 분석 DB 집계 — ✅ 통과

```
성적 요약 upsert: studentId=1, year=2026, semester=1
과목 통계 upsert: subjectId=6, year=2026, semester=1
대시보드 upsert: studentId=1, year=2026, semester=1, risk=LOW
성적 분석 완료: studentId=1, subjectId=6
```

### ⑥ Dashboard API 응답 — ✅ 통과

```
GET /api/v1/analytics/students/1/dashboard?year=2026&semester=1

{
  "status": "success",
  "data": {
    "studentId": 1,
    "studentName": "테스트학생",
    "academicYear": 2026,
    "semester": 1,
    "avgScore": 80.17,        ← (80+90+60+75+88+70) / 6
    "scoreTrend": null,        ← 이전 학기 데이터 없음
    "attendanceCount": 0,      ← StudentRecord 미등록
    "awardCount": 0,
    "totalFeedbackCount": 0,   ← Feedback 미등록
    "totalCounselCount": 0,    ← Counseling 미등록
    "lastCounselDate": null,
    "riskLevel": "LOW"         ← 평균 80점 이상, 피드백 5건 미만
  }
}
```

---

## 발견된 버그 및 수정 내역

### 버그 1: Flyway가 분석 DB에 연결되는 문제

**증상**: 앱 시작 시 Flyway가 운영 DB(5432)가 아닌 분석 DB(5433)에 연결되어 실패

**원인**: `AnalyticsDataSourceConfig`에서 `DataSource`를 `@Bean`으로 등록하면, Spring Boot가 "이미 DataSource가 있네" 하고 기본 DataSource 자동 생성을 건너뜀

**수정**: DataSource를 `@Bean`으로 노출하지 않고, JdbcTemplate 빈 내부에서만 생성
```java
// 변경 전: @Bean으로 DataSource 노출 → Spring Boot 자동설정 충돌
@Bean(name = "analyticsDataSource")
public DataSource analyticsDataSource() { ... }

// 변경 후: JdbcTemplate 내부에서만 DataSource 생성
@Bean(name = "analyticsJdbc")
public JdbcTemplate analyticsJdbcTemplate() {
    DataSource dataSource = DataSourceBuilder.create()...build();
    return new JdbcTemplate(dataSource);
}
```

### 버그 2: Consumer가 운영 DB 대신 분석 DB에서 집계 쿼리 실행

**증상**: `relation "scores" does not exist` — 분석 DB에는 scores 테이블이 없음

**원인**: `AnalyticsJdbcRepository`에서 `JdbcTemplate primaryJdbc`를 주입받을 때, JdbcTemplate 빈이 2개 있어서 Spring이 잘못된 것을 주입

**수정**: DataSource(Spring Boot가 `@Primary`로 자동 생성)를 직접 주입받아서 JdbcTemplate 생성
```java
// 변경 전: JdbcTemplate 직접 주입 → 어떤 것이 주입될지 불확실
public AnalyticsJdbcRepository(JdbcTemplate primaryJdbc, ...)

// 변경 후: @Primary DataSource로부터 직접 생성 → 확실히 운영 DB
public AnalyticsJdbcRepository(DataSource dataSource, ...) {
    this.primaryJdbc = new JdbcTemplate(dataSource);
}
```

### 버그 3: 컬럼명 불일치 (avg_score vs average_score)

**증상**: `column "avg_score" does not exist`

**원인**: `calculateScoreTrend()`에서 `student_score_summary` 테이블 조회 시 `avg_score`로 썼지만, 실제 컬럼명은 `average_score`

**수정**: SQL 컬럼명을 `average_score`로 수정

---

## 교훈

1. **DataSource 빈 등록 시 주의**: Spring Boot 자동 설정과 충돌할 수 있다. 커스텀 DataSource는 빈으로 노출하지 않는 것이 안전.
2. **JdbcTemplate 다중 빈**: 여러 DB를 사용할 때, 파라미터 이름 기반 주입은 불확실하다. `@Primary` DataSource를 직접 주입받는 것이 확실.
3. **SQL 컬럼명 검증**: JPA와 달리 JdbcTemplate은 컴파일 타임에 SQL을 검증하지 않으므로, 런타임 에러가 난다. 테이블 스키마와 SQL의 컬럼명을 꼼꼼히 대조해야 한다.
