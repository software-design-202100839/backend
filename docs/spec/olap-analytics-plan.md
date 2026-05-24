# OLAP Analytics + AI Chatbot 구현 계획서

> 작성일: 2026-05-21
> 목적: 교수님 추가 요구사항 (OLAP) 구현을 위한 설계 문서

---

## 1. 요구사항 정리

### 교수님 원문
- 서비스용 데이터와 분석용 데이터를 분리할 것
- 운영 DB 데이터(출결, 평가, 피드백 등)를 분석용 DB로 주기적으로 적재
- 학생별·과목별 학습 현황을 집계하여 대시보드에서 조회
- Kafka 같은 메시지 스트림 활용 시 가점
- 선택사항: AI 챗봇

### 우리의 결정
| 항목 | 결정 | 이유 |
|------|------|------|
| 이벤트 스트림 | **Apache Kafka** | 교수님이 가점 명시 |
| DB 분리 | **별도 PostgreSQL 인스턴스** | 물리적 분리 = OLAP 개념에 충실, 면접 어필 |
| AI 챗봇 | **Spring AI + Claude API (Tool Use)** | 취업 트렌드, 에이전트 패턴 |

---

## 2. OLAP란?

### 핵심 개념
- **OLTP** (On-Line Transaction Processing): 서비스 운영용. 한 건씩 빠르게 읽기/쓰기 (예: 성적 등록)
- **OLAP** (On-Line Analytical Processing): 분석용. 대량 데이터 집계/조회 (예: 학생별 평균 성적 추이)

### 왜 분리하는가?
분석가가 "전교생의 과목별 평균 성적과 등급 분포"를 조회하면, 복잡한 JOIN + GROUP BY 쿼리가 실행됨.
이게 운영 DB에서 돌면 → 다른 사용자의 성적 등록이 느려짐 (자원 경합).
그래서 분석용 데이터를 **별도 DB에 미리 집계해두고**, 대시보드는 집계 테이블만 조회.

### ETL 파이프라인
```
Extract (추출)     → Transform (변환/집계)     → Load (적재)
운영 DB에서 데이터    평균, 건수, 분포 계산       분석 DB에 저장
```

우리는 ETL 대신 **이벤트 기반**: 데이터가 변경될 때마다 Kafka를 통해 실시간 반영.

---

## 3. 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Spring Boot Application                      │
│                                                                       │
│  ┌──────────────┐    ApplicationEvent    ┌──────────────────────┐    │
│  │ Domain Service│──────────────────────▶│ AnalyticsEventBridge │    │
│  │ (ScoreService │    (Spring 내부 이벤트) │ (Spring→Kafka 변환)  │    │
│  │  등)          │                        └──────────┬───────────┘    │
│  └──────┬───────┘                                    │               │
│         │                                     KafkaTemplate.send()   │
│         │ NotificationEvent (기존)                    │               │
│         ▼                                            ▼               │
│  ┌──────────────┐                          ┌─────────────────┐      │
│  │ Notification  │                          │     Kafka       │      │
│  │ EventListener │                          │  (Docker)       │      │
│  └──────────────┘                          └────────┬────────┘      │
│                                                      │               │
│                                              @KafkaListener          │
│                                                      │               │
│                                              ┌───────▼──────────┐   │
│                                              │ Analytics Consumer│   │
│                                              │ (집계 로직)       │   │
│                                              └───────┬──────────┘   │
│                                                      │               │
└──────────────────────────────────────────────────────┼───────────────┘
                                                       │
                    ┌──────────────────────────────────┼────────┐
                    │                                  ▼        │
   ┌────────────┐  │  ┌────────────────┐   ┌──────────────┐   │
   │ 운영 DB     │  │  │ 운영 DB에서     │   │ 분석 DB       │   │
   │ (port 5432) │◀─┤  │ SELECT 집계    │──▶│ (port 5433)  │   │
   │ public 스키마│  │  └────────────────┘   │ 집계 테이블들  │   │
   └────────────┘  │                        └──────────────┘   │
                    │              Consumer 내부 흐름            │
                    └──────────────────────────────────────────┘
```

### 핵심 포인트
1. **기존 코드 최소 변경**: 도메인 서비스에 이벤트 발행 1줄만 추가
2. **Kafka는 중간 다리**: 운영 서비스와 분석 시스템을 느슨하게 연결 (loose coupling)
3. **Consumer가 핵심**: 이벤트를 받아서 운영 DB에서 집계 쿼리 실행 → 결과를 분석 DB에 저장

---

## 4. 구현 단계 (6 Phases)

### Phase 1: 인프라 셋업
**목표**: Kafka + 분석 DB를 Docker로 띄우고, Spring Boot에서 연결

| 작업 | 파일 | 설명 |
|------|------|------|
| Kafka/Zookeeper 추가 | `docker-compose.yml` | Confluent 이미지 사용 |
| 분석 DB 추가 | `docker-compose.yml` | PostgreSQL 16, port 5433 |
| Kafka 의존성 추가 | `build.gradle.kts` | spring-kafka |
| Kafka/DB 설정 | `application-dev.yml` | bootstrap-servers, datasource |
| 분석 DB 스키마 | `analytics-schema.sql` | 6개 집계 테이블 |
| Kafka 토픽 설정 | `KafkaConfig.java` | 4개 토픽 생성 |
| 분석 DB 연결 | `AnalyticsDataSourceConfig.java` | JdbcTemplate 빈 |

**배울 것**: Kafka 기본 개념 (Broker, Topic, Producer, Consumer, Partition)

---

### Phase 2: 이벤트 파이프라인 (Producer)
**목표**: 도메인 데이터 변경 시 Kafka로 이벤트 발행

| 작업 | 파일 | 설명 |
|------|------|------|
| 이벤트 클래스 | `ScoreChangedEvent.java` 등 | 도메인 이벤트 정의 |
| 이벤트 페이로드 | `ScoreEventPayload.java` 등 | Kafka 메시지 본문 |
| 이벤트 브릿지 | `AnalyticsEventBridge.java` | Spring Event → Kafka 변환 |
| 서비스 수정 | `ScoreService.java` 등 | 이벤트 발행 코드 1줄 추가 |

**배울 것**: 이벤트 기반 아키텍처 (EDA), Spring ApplicationEvent, Kafka Producer

**기존 vs 추가 흐름:**
```
기존: ScoreService → NotificationEvent → 알림 발송
추가: ScoreService → ScoreChangedEvent → Kafka → Analytics Consumer
```

---

### Phase 3: Kafka Consumer + 집계 로직
**목표**: Kafka 이벤트를 수신하여 분석 DB에 집계 데이터 저장

| 작업 | 파일 | 설명 |
|------|------|------|
| JDBC Repository | `AnalyticsJdbcRepository.java` | INSERT ON CONFLICT DO UPDATE (upsert) |
| Score Consumer | `ScoreAnalyticsConsumer.java` | 성적 이벤트 → 성적 요약/과목 통계 갱신 |
| Feedback Consumer | `FeedbackAnalyticsConsumer.java` | 피드백 이벤트 → 피드백 요약 갱신 |
| Record Consumer | `RecordAnalyticsConsumer.java` | 학생부 이벤트 → 출결/기록 요약 갱신 |
| Counseling Consumer | `CounselingAnalyticsConsumer.java` | 상담 이벤트 → 상담 요약 갱신 |
| 초기 데이터 로더 | `AnalyticsDataLoader.java` | 기존 데이터 일괄 집계 (backfill) |

**배울 것**: Kafka Consumer Group, Upsert 패턴, JdbcTemplate, 집계 쿼리

---

### Phase 4: 대시보드 REST API
**목표**: 분석 DB의 집계 데이터를 조회하는 API 제공

| 엔드포인트 | 설명 | 접근 권한 |
|-----------|------|-----------|
| `GET /api/v1/analytics/students/{id}/dashboard` | 학생 종합 대시보드 | 교사, 관리자, 본인, 학부모 |
| `GET /api/v1/analytics/students/{id}/score-summary` | 성적 요약 | 동일 |
| `GET /api/v1/analytics/students/{id}/score-trend` | 학기별 성적 추이 | 동일 |
| `GET /api/v1/analytics/students/{id}/attendance-summary` | 출결/기록 요약 | 교사, 관리자 |
| `GET /api/v1/analytics/students/{id}/feedback-summary` | 피드백 요약 | 교사, 관리자 |
| `GET /api/v1/analytics/students/{id}/counseling-summary` | 상담 요약 | 교사, 관리자 |
| `GET /api/v1/analytics/subjects/{id}/statistics` | 과목 통계 | 교사, 관리자 |
| `GET /api/v1/analytics/subjects/statistics` | 전체 과목 통계 | 교사, 관리자 |
| `POST /api/v1/analytics/admin/backfill` | 초기 데이터 적재 | 관리자 |

**배울 것**: DTO 설계, 역할 기반 접근제어, 분석 데이터 조회 패턴

---

### Phase 5: AI 챗봇 (Spring AI + Tool Use)
**목표**: 자연어로 학생 분석 데이터를 질의하는 AI 챗봇

```
사용자: "김철수 학생의 이번 학기 학습 현황을 요약해줘"
  ↓
Spring AI가 Claude API에 질문 + Tool 정의 전달
  ↓
Claude가 필요한 Tool 선택: getStudentDashboard(studentId=5, year=2026, semester=1)
  ↓
Spring AI가 Tool 실행 → 분석 DB 조회 → 결과를 Claude에게 반환
  ↓
Claude가 자연어 응답 생성:
"김철수 학생은 이번 학기 평균 85점으로 B+ 등급이며,
 전 학기 대비 7점 상승했습니다. 출결은 양호하고..."
```

**배울 것**: Spring AI 프레임워크, Function Calling (Tool Use), AI 에이전트 패턴

---

### Phase 6: 테스트 + 검증
- 기존 테스트 통과 확인
- Kafka Consumer 테스트 (@EmbeddedKafka)
- Dashboard API 통합 테스트
- 전체 흐름 E2E 검증

---

## 5. 분석 DB 테이블 설계

### 5-1. student_score_summary (학생 성적 요약)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| student_id | BIGINT | 학생 ID |
| student_name | VARCHAR(100) | 학생 이름 |
| academic_year | INTEGER | 학년도 |
| semester | INTEGER | 학기 (1, 2) |
| subject_count | INTEGER | 수강 과목 수 |
| total_score | DECIMAL(7,2) | 총점 |
| average_score | DECIMAL(5,2) | 평균 |
| highest_score | DECIMAL(5,2) | 최고점 |
| lowest_score | DECIMAL(5,2) | 최저점 |
| average_grade | VARCHAR(5) | 평균 등급 |
| UNIQUE | (student_id, academic_year, semester) | |

### 5-2. student_attendance_summary (학생 기록 요약)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| student_id | BIGINT | 학생 ID |
| academic_year, semester | INTEGER | 학기 |
| attendance_count | INTEGER | 출결 기록 수 |
| award_count | INTEGER | 수상 수 |
| volunteer_count | INTEGER | 봉사 수 |
| special_note_count | INTEGER | 세특 수 |
| general_opinion_count | INTEGER | 종합의견 수 |

### 5-3. student_feedback_summary (피드백 요약)
카테고리별(ACADEMIC, BEHAVIOR, ATTENDANCE, ATTITUDE, GENERAL) 건수 집계

### 5-4. student_counseling_summary (상담 요약)
카테고리별(ACADEMIC, CAREER, BEHAVIOR, PERSONAL, OTHER) 건수 + 마지막 상담일

### 5-5. subject_statistics (과목 통계)
과목별 학생수, 평균, 최고/최저, 표준편차, 등급(A/B/C/D/F) 분포

### 5-6. student_learning_dashboard (학생 종합 대시보드)
위 테이블들의 핵심 데이터를 하나로 통합 + 위험도(risk_level) 지표

---

## 6. Kafka 토픽 설계

| Topic | Partition Key | 발행 시점 |
|-------|--------------|-----------|
| `sscm.scores` | studentId | 성적 등록/수정/삭제 |
| `sscm.feedbacks` | studentId | 피드백 등록 |
| `sscm.records` | studentId | 학생부 등록 |
| `sscm.counselings` | studentId | 상담 등록/수정 |

Partition Key = studentId → 같은 학생의 이벤트는 같은 파티션에서 순서 보장

---

## 7. 새로 생성되는 파일 목록

```
src/main/java/com/sscm/analytics/
├── config/
│   ├── KafkaConfig.java
│   └── AnalyticsDataSourceConfig.java
├── event/
│   ├── AnalyticsEvent.java
│   ├── AnalyticsEventBridge.java
│   ├── ScoreChangedEvent.java
│   ├── FeedbackChangedEvent.java
│   ├── RecordChangedEvent.java
│   ├── CounselingChangedEvent.java
│   └── payload/ (4개 DTO)
├── consumer/ (4개 Consumer)
├── repository/
│   └── AnalyticsJdbcRepository.java
├── service/
│   ├── AnalyticsDashboardService.java
│   ├── AnalyticsAccessChecker.java
│   └── AnalyticsDataLoader.java
├── controller/ (3개 Controller)
├── dto/ (7개 DTO)
└── chatbot/
    ├── config/ChatbotConfig.java
    ├── controller/ChatbotController.java
    ├── service/ChatbotService.java
    └── dto/ (2개 DTO)
```

---

## 8. 용어 정리

| 용어 | 설명 |
|------|------|
| OLTP | 트랜잭션 처리. 서비스 운영용 DB 패턴 |
| OLAP | 분석 처리. 대량 집계/조회용 DB 패턴 |
| ETL | Extract-Transform-Load. 데이터 파이프라인 |
| CDC | Change Data Capture. 데이터 변경 감지 |
| Kafka | 분산 이벤트 스트리밍 플랫폼 |
| Topic | Kafka에서 메시지가 발행되는 채널 |
| Producer | Kafka에 메시지를 보내는 쪽 |
| Consumer | Kafka에서 메시지를 받는 쪽 |
| Consumer Group | 같은 토픽을 구독하는 Consumer들의 논리적 그룹 |
| Partition | 토픽 내 데이터 분할 단위. 같은 key는 같은 파티션 |
| Upsert | INSERT + UPDATE. 있으면 갱신, 없으면 삽입 |
| Tool Use | AI가 외부 함수를 호출하는 패턴 (Function Calling) |
| Spring AI | Spring 공식 AI 추상화 프레임워크 |
