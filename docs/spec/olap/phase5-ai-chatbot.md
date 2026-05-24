# Phase 5: AI 챗봇 (Spring AI + Gemini Tool Use)

> 작성일: 2026-05-24
> 상태: 완료

## 목표

교사가 자연어로 학생 분석 데이터를 질의하면, AI가 분석 DB를 조회해서 답변하는 챗봇을 구현한다.

## 왜 이 작업이 필요한가?

대시보드 UI는 정해진 형태의 데이터만 보여준다.
"이 학생의 약점이 뭐야?", "성적이 떨어진 학생이 있어?" 같은 **자유로운 질문**에는 답할 수 없다.
AI 챗봇은 자연어 질문을 이해하고, 필요한 데이터를 스스로 조회해서 답변한다.

---

## 핵심 개념: Tool Use (Function Calling)

단순히 프롬프트에 데이터를 붙여넣는 방식이 아니라,
**AI가 어떤 함수를 호출할지 스스로 판단**하는 에이전트 패턴.

```
교사: "테스트학생의 2026년 1학기 성적이 어때?"
  ↓
Spring AI가 Claude/Gemini에게 전달:
  - 시스템 프롬프트 (역할 설명)
  - 사용자 질문
  - 사용 가능한 Tool 목록 (6개 함수)
  ↓
AI가 판단: "getStudentScoreSummary를 호출해야겠다"
  ↓
Spring AI가 함수 실행 → 분석 DB 조회 → 결과 반환
  ↓
AI가 결과를 자연어로 변환:
  "테스트학생은 6과목 평균 80.17점(B등급)이며,
   수학이 90점으로 가장 높고, 영어가 60점으로 가장 낮습니다."
```

이 패턴의 장점:
- 질문이 달라져도 AI가 적절한 함수를 선택
- 새 함수를 추가하면 AI가 자동으로 활용
- 현업에서 실제로 사용하는 AI 에이전트 패턴

---

## 기술 스택 선택

### 최초 계획: Claude API
- Spring AI Anthropic starter 사용
- 문제: Claude API 크레딧 결제 이슈로 사용 불가

### 최종 선택: Google Gemini (무료)
- Spring AI OpenAI starter + Google AI Studio OpenAI 호환 엔드포인트
- 모델: gemini-2.5-flash (무료 tier)
- API 키: https://aistudio.google.com/apikey 에서 무료 발급

### Spring AI의 장점
Spring AI는 LLM을 추상화하므로, 코드 변경 없이 모델 교체 가능:
```yaml
# Claude 사용 시
spring.ai.anthropic.api-key: ...

# Gemini 사용 시 (현재)
spring.ai.openai.api-key: ...
spring.ai.openai.base-url: https://generativelanguage.googleapis.com/v1beta/openai

# OpenAI 사용 시
spring.ai.openai.api-key: ...
```
ChatbotService 코드는 변경 불필요.

---

## 전체 흐름

```
POST /api/v1/analytics/chat
  ↓
ChatbotController (교사/관리자 권한 확인)
  ↓
ChatbotService
  ↓
ChatClient.create(chatModel)
  .prompt()
  .system(SYSTEM_PROMPT)       ← AI의 역할과 규칙 정의
  .user(question)              ← 사용자 질문
  .functions(                  ← AI가 호출 가능한 함수 6개
      "getStudentDashboard",
      "getStudentScoreSummary",
      "getStudentScoreTrend",
      "getStudentFeedbackSummary",
      "getStudentCounselingSummary",
      "getAllSubjectStatistics"
  )
  .call()
  .content()
  ↓
Gemini API (Google AI Studio)
  ↓ AI가 필요한 Tool 선택 → Spring AI가 함수 실행 → 결과를 AI에게 반환
  ↓
자연어 응답 생성
  ↓
ChatResponse { answer: "..." }
```

---

## 변경 파일 목록

### 신규 파일

| 파일 | 역할 |
|------|------|
| `chatbot/dto/ChatRequest.java` | 요청 DTO (question 필드) |
| `chatbot/dto/ChatResponse.java` | 응답 DTO (answer 필드) |
| `chatbot/tools/AnalyticsTools.java` | AI가 호출할 수 있는 함수 6개 정의 (@Bean Function) |
| `chatbot/service/ChatbotService.java` | ChatClient로 AI 호출 + Tool Use 실행 |
| `chatbot/controller/ChatbotController.java` | REST 엔드포인트 (교사/관리자 전용) |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `build.gradle.kts` | spring-ai-openai-spring-boot-starter 추가, Spring AI BOM, swagger 버전 충돌 해결 |
| `application-dev.yml` | Gemini API 연결 설정 (OpenAI 호환 엔드포인트) |

---

## AI Tool 목록 (6개)

AI가 질문에 따라 자동으로 선택하여 호출하는 함수들:

| Tool 이름 | 설명 | 입력 |
|-----------|------|------|
| `getStudentDashboard` | 학생 종합 학습 현황 | studentId, year, semester |
| `getStudentScoreSummary` | 학생 성적 요약 (평균, 총점 등) | studentId, year, semester |
| `getStudentScoreTrend` | 학기별 성적 추이 | studentId |
| `getStudentFeedbackSummary` | 피드백 카테고리별 건수 | studentId, year, semester |
| `getStudentCounselingSummary` | 상담 카테고리별 건수 | studentId, year, semester |
| `getAllSubjectStatistics` | 전체 과목별 통계 | year, semester |

`@Description` 어노테이션의 설명을 보고 AI가 어떤 함수를 호출할지 판단한다.

---

## 시스템 프롬프트

AI의 역할과 규칙을 정의:
- SSCM 교육 분석 AI 어시스턴트 역할
- 반드시 도구를 사용하여 실제 데이터를 조회한 후 답변
- 데이터가 없으면 "해당 데이터가 없습니다"
- 추측이나 가정 금지
- 한국어로 답변

---

## 접근 권한

- **TEACHER, ADMIN**: 챗봇 사용 가능
- **STUDENT, PARENT**: 사용 불가 (@PreAuthorize로 차단)

학생/학부모를 차단하는 이유:
프롬프트 조작(prompt injection)으로 "다른 학생의 데이터를 알려줘" 같은 요청을 할 수 있어서.

---

## 환경변수 설정

```bash
# .env 파일 (프로젝트 루트, .gitignore에 포함)
export GEMINI_API_KEY=AIza...

# 실행
source .env && ./gradlew bootRun --no-daemon
```

---

## 테스트 결과

### 요청
```json
POST /api/v1/analytics/chat
{
  "question": "테스트학생의 2026년 1학기 성적이 어때?"
}
```

### 응답
```json
{
  "status": "success",
  "data": {
    "answer": "테스트학생의 2026년 1학기 성적 요약입니다.\n\n- 수강 과목 수: 6개\n- 총점: 481점\n- 평균 점수: 80.17점\n- 최고 점수: 90점\n- 최저 점수: 60점\n- 평균 등급: B\n\n전반적으로 양호한 성적을 유지하고 있으나, 최저 점수가 60점인 과목에 대한 추가적인 분석을 통해 개선이 필요한 영역을 파악할 수 있습니다."
  }
}
```

AI가 `getStudentScoreSummary` Tool을 자동으로 선택하여 분석 DB를 조회하고, 자연어로 분석 결과를 응답했다.

---

## 발견된 이슈 및 해결

### 이슈 1: Claude API 크레딧 결제 불가
- 증상: Anthropic 콘솔에서 크레딧 구매 버튼 비활성화
- 해결: Google Gemini (무료)로 전환. Spring AI의 OpenAI 호환 starter 사용

### 이슈 2: Swagger 버전 충돌
- 증상: Spring AI가 가져온 swagger-core와 springdoc의 swagger-annotations 버전 불일치
- 해결: springdoc 2.8.8 + swagger-annotations/core 2.2.29 명시적 추가

### 이슈 3: Gemini 모델명 변경
- 증상: `gemini-2.0-flash` 모델이 더 이상 사용 불가
- 해결: `gemini-2.5-flash`로 변경

### 이슈 4: API 키 미전달
- 증상: `x-api-key header is required` 에러
- 해결: .env 파일에 `export` 키워드 추가 + Gradle 데몬 캐시 방지 (`--no-daemon`)

---

## 비용

| 항목 | 비용 |
|------|------|
| Google AI Studio (Gemini) | 무료 (분당 요청 제한 있지만 시연용으로 충분) |
| Claude API (향후 전환 시) | $5 최소 충전, 질문당 약 10~30원 |
