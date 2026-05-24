# Phase 2: 이벤트 파이프라인 (Producer)

> 작성일: 2026-05-24
> 상태: 완료

## 목표

도메인 데이터(성적, 피드백, 학생부, 상담)가 변경될 때 Kafka로 이벤트를 발행하는 구조를 만든다.

## 왜 이 작업이 필요한가?

Phase 1에서 Kafka 인프라를 준비했지만, 아직 아무도 메시지를 보내지 않는다.
도메인 서비스에서 "데이터가 바뀌었다"는 신호를 Kafka로 보내야 Consumer가 받아서 분석 DB에 집계할 수 있다.

## 설계 핵심: 왜 직접 Kafka를 호출하지 않는가?

```
❌ 나쁜 방법: ScoreService → KafkaTemplate.send() (직접 호출)
✅ 좋은 방법: ScoreService → Spring 이벤트 → AnalyticsEventBridge → Kafka
```

직접 호출하면 **ScoreService가 Kafka에 의존**하게 된다.
Kafka가 죽으면 성적 등록도 실패할 수 있다.

중간에 Bridge를 두면:
- 서비스는 Spring 내부 이벤트만 발행 (Kafka 몰라도 됨)
- Bridge가 @Async로 Kafka에 전송 (비동기, 실패해도 서비스에 영향 없음)
- Kafka를 나중에 다른 것으로 교체해도 서비스 코드 변경 불필요

이것을 **느슨한 결합 (Loose Coupling)** 이라고 한다.

---

## 전체 흐름

```
도메인 서비스                     AnalyticsEventBridge              Kafka
────────────                     ───────────────────              ─────
ScoreService.createScore()
  │
  ├─ publishScoreNotification()    (기존: 알림 발송)
  │
  └─ publishScoreAnalyticsEvent()
       │
       └─ eventPublisher.publishEvent(ScoreChangedEvent)
              │
              └──── @EventListener + @Async ────▶ onScoreChanged()
                                                    │
                                                    └─ kafkaTemplate.send(
                                                         topic: "sscm.scores",
                                                         key: "5",        ← studentId
                                                         value: AnalyticsEvent {
                                                           eventType: "SCORE_CREATED",
                                                           timestamp: "2026-05-24T14:30:00",
                                                           payload: ScoreEventPayload {
                                                             scoreId: 1,
                                                             studentId: 5,
                                                             subjectId: 2,
                                                             score: 85.00,
                                                             ...
                                                           }
                                                         }
                                                       )
```

---

## 변경 파일 목록

### 신규 파일

| 파일 | 역할 |
|------|------|
| `analytics/event/payload/ScoreEventPayload.java` | 성적 이벤트 데이터 (Kafka 메시지 본문) |
| `analytics/event/payload/FeedbackEventPayload.java` | 피드백 이벤트 데이터 |
| `analytics/event/payload/RecordEventPayload.java` | 학생부 이벤트 데이터 |
| `analytics/event/payload/CounselingEventPayload.java` | 상담 이벤트 데이터 |
| `analytics/event/AnalyticsEvent.java` | 이벤트 봉투 (eventType + timestamp + payload) |
| `analytics/event/ScoreChangedEvent.java` | Spring 내부 이벤트 (성적) |
| `analytics/event/FeedbackChangedEvent.java` | Spring 내부 이벤트 (피드백) |
| `analytics/event/RecordChangedEvent.java` | Spring 내부 이벤트 (학생부) |
| `analytics/event/CounselingChangedEvent.java` | Spring 내부 이벤트 (상담) |
| `analytics/event/AnalyticsEventBridge.java` | Spring 이벤트 → Kafka 전송 다리 |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `grade/service/ScoreService.java` | createScore, updateScore, deleteScore에 분석 이벤트 발행 추가 |
| `feedback/service/FeedbackService.java` | createFeedback에 분석 이벤트 발행 추가 |
| `student/service/StudentRecordService.java` | createRecord에 분석 이벤트 발행 추가 |
| `counsel/service/CounselingService.java` | createCounseling, updateCounseling에 분석 이벤트 발행 추가 (+ ApplicationEventPublisher 주입) |

---

## 핵심 개념 정리

### 이벤트 메시지 구조 (3계층)

```
┌─ AnalyticsEvent (봉투) ───────────────┐
│  eventType: "SCORE_CREATED"           │  ← 무슨 일이 일어났는지
│  timestamp: 2026-05-24T14:30:00       │  ← 언제 일어났는지
│  payload: ScoreEventPayload {         │  ← 상세 데이터
│    scoreId: 1,                        │
│    studentId: 5,                      │
│    subjectId: 2,                      │
│    score: 85.00,                      │
│    gradeLetter: "B+",                 │
│    ...                                │
│  }                                    │
└───────────────────────────────────────┘
```

- **AnalyticsEvent** = 봉투. 모든 도메인 이벤트를 같은 형태로 감쌈
- **eventType** = Consumer가 "생성인지 수정인지 삭제인지" 판단하는 기준
- **payload** = 도메인별로 다른 실제 데이터

### Spring 내부 이벤트 vs Kafka 이벤트

| | Spring ApplicationEvent | Kafka 메시지 |
|---|---|---|
| 범위 | 같은 JVM(앱) 안에서만 | 네트워크를 통해 다른 앱에도 전달 가능 |
| 영속성 | 메모리에만 존재 | Kafka 브로커에 저장 (디스크) |
| 용도 | 서비스 → Bridge 전달용 | Bridge → Consumer 전달용 |
| 실패 시 | 이벤트 유실 | 재시도 가능 (offset 관리) |

### @Async의 역할

```java
@Async
@EventListener
public void onScoreChanged(ScoreChangedEvent event) {
    kafkaTemplate.send(...);
}
```

- `@EventListener`: Spring 이벤트를 수신
- `@Async`: 별도 스레드에서 실행 → Kafka 전송이 느리거나 실패해도 원래 서비스(성적 등록)에 영향 없음
- 두 어노테이션을 합치면: "이벤트가 오면 비동기로 처리해라"

### 느슨한 결합 (Loose Coupling)

기존 알림 시스템도 같은 패턴을 이미 쓰고 있다:
```
ScoreService → NotificationEvent → NotificationEventListener (알림)
ScoreService → ScoreChangedEvent → AnalyticsEventBridge (분석)
```

서비스는 "이벤트를 던질 뿐", 누가 받아서 뭘 하는지 모른다.
이런 구조의 장점:
- 나중에 분석 외에 다른 시스템(예: 보고서 생성)을 추가할 때 서비스 코드 변경 불필요
- 이벤트를 받는 쪽(Listener)만 추가하면 됨

### 도메인별 이벤트 발행 시점

| 도메인 | 이벤트 발행 시점 | eventType |
|--------|----------------|-----------|
| Score | 생성, 수정, 삭제 | SCORE_CREATED, SCORE_UPDATED, SCORE_DELETED |
| Feedback | 생성 | FEEDBACK_CREATED |
| StudentRecord | 생성 | RECORD_CREATED |
| Counseling | 생성, 수정 | COUNSELING_CREATED, COUNSELING_UPDATED |

---

## 검증 방법

```bash
# 1. 컴파일 확인
./gradlew compileJava

# 2. docker-compose up 후 앱 시작

# 3. 성적 등록 API 호출 (예: Swagger UI)

# 4. Kafka 토픽에 메시지가 들어왔는지 확인
docker exec sscm-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sscm.scores \
  --from-beginning
```
