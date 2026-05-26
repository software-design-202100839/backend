# Phase 6: OLAP 분석 시스템 테스트 코드

> 작성일: 2026-05-26
> 목적: OLAP 분석 시스템의 동작을 단위/통합 테스트로 증명

---

## 개요

OLAP Phase 1~5에서 구현한 코드에 대한 테스트 작성.
- **단위 테스트**: Mockito로 개별 클래스 로직 검증
- **컨트롤러 테스트**: MockMvc로 HTTP 요청/응답 + 인가 검증
- **통합 테스트**: EmbeddedKafka로 Producer → Consumer 흐름 검증

---

## 테스트 목록

| 테스트 클래스 | 유형 | 검증 내용 | 테스트 수 |
|--------------|------|-----------|-----------|
| AnalyticsAccessCheckerTest | Unit | 역할별(교사/관리자/학생/학부모) 접근 권한 판단 | 8 |
| AnalyticsEventBridgeTest | Unit | Spring Event → Kafka 토픽 전송 (토픽명, Key, 이벤트타입) | 5 |
| ScoreAnalyticsConsumerTest | Unit | Kafka 메시지 수신 → Repository 집계 메서드 호출 | 2 |
| AnalyticsDashboardServiceTest | Unit | JdbcTemplate 조회 결과 → DTO 매핑 + 데이터 없을 때 예외 | 4 |
| AnalyticsDashboardControllerTest | MockMvc | 학생 대시보드 API 인가 검증 (성공/403/401) | 4 |
| AnalyticsSubjectControllerTest | MockMvc | 과목 통계 API — 교사 허용, 학생 차단 | 3 |
| ChatbotControllerTest | MockMvc | AI 챗봇 API — 교사 허용, 학생 차단, 빈 질문 400 | 4 |
| KafkaIntegrationTest | Integration | Kafka 발행 → Consumer 수신 → 집계 호출 E2E | 1 |

**총 31개 테스트 케이스**

---

## 테스트 전략

### 단위 테스트 (Unit)
- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- 외부 의존성(DB, Kafka)은 모두 Mock
- 비즈니스 로직만 검증

### 컨트롤러 테스트 (MockMvc)
- `@WebMvcTest` — 컨트롤러 레이어만 로드
- `@Import(SecurityConfig.class)` — 실제 Security 설정 적용
- `SecurityMockMvcRequestPostProcessors.authentication()` — 역할별 인증 시뮬레이션
- 서비스 계층은 `@MockitoBean`

### 통합 테스트 (EmbeddedKafka)
- `@SpringBootTest` + `@EmbeddedKafka` — 내장 Kafka 브로커
- `@EnabledIfEnvironmentVariable(named = "CI", matches = "true")` — CI 환경에서만 실행
- Repository는 Mock으로 대체 (분석 DB 없이 Consumer 로직만 검증)
- `Awaitility`로 비동기 Consumer 수신 대기

---

## 기존 테스트 수정

OLAP 이벤트 발행 코드 추가 시 `ApplicationEventPublisher`가 서비스에 주입되면서
기존 테스트에 Mock이 누락된 부분 수정:

- `CounselingServiceTest.java` — `@Mock ApplicationEventPublisher` 추가
- `StudentRecordServiceTest.java` — `@Mock ApplicationEventPublisher` 추가

---

## 실행 결과

```
407 tests completed, 0 failed, 2 skipped
BUILD SUCCESSFUL
```

- skipped: KafkaIntegrationTest (CI 환경 아닐 때)

---

## 검증 방법

```bash
# 전체 테스트 실행
./gradlew test --no-daemon

# OLAP 테스트만 실행
./gradlew test --tests "com.sscm.analytics.*" --no-daemon

# CI에서 Kafka 통합 테스트 포함
CI=true ./gradlew test --no-daemon
```
