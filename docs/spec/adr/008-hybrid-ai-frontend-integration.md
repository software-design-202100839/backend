# Hybrid AI 프론트엔드 통합 구현 결과

> 작성: 2026-05-28
> 목적: Hybrid AI 백엔드(007)를 프론트엔드 UI와 연결하고, 발표 데모가 가능한 상태로 완성

---

## 1. 구현 범위

### 백엔드 변경 (8개 파일)

| 파일 | 변경 내용 |
|------|----------|
| `ChatResponse.java` | `reportId` 필드 추가 — 보고서 생성 시 ID를 프론트에 전달 |
| `ReportIdHolder.java` | ThreadLocal holder — Tool 실행 → ChatbotService 간 reportId 전달 |
| `ReportGenerationService.java` | `GeneratedKeyHolder`로 INSERT 후 reportId 반환, `maxTokens(4096)` 설정 |
| `AnalyticsTools.java` | `generateStudentReport` Tool에서 `ReportIdHolder.set()` 호출 |
| `ChatbotService.java` | `ReportIdHolder` 3중 안전 처리 (호출전 clear → 호출후 getAndClear → finally clear), 학생 이름 검색 우선 프롬프트 |
| `ReportController.java` | `GET /{reportId}` 추가 + 역할별 접근 제어 (TEACHER/STUDENT/PARENT) |
| `EmbeddingService.java` | pgvector 바인딩을 `?::vector` 문자열 캐스팅으로 변경 (HikariCP+JDBC 42.7 호환) |
| `AnalyticsDataSourceConfig.java` | `@Primary` JdbcTemplate 명시 등록 (운영DB/분석DB JdbcTemplate 혼동 방지) |

### 프론트엔드 변경 (3개 파일)

| 파일 | 변경 내용 |
|------|----------|
| `analyticsService.ts` | `ChatMessageResponse`(reportId 포함), `getReport()`, `saveReportEdit()` 추가 |
| `AiChatWidget.tsx` | `ReportCard` 컴포넌트 (초안 본문 + 참고 근거 접기/펼치기 + 교사 수정 UI) |
| `DashboardPage.tsx` | `RiskAlertCard` 컴포넌트 (SYSTEM 알림 필터 + 4개 규칙 유형 아이콘) |

---

## 2. 해결한 문제

### pgvector `Unknown type vector` 에러
- **원인**: pgvector-java 0.1.6의 `PGvector` 객체를 `PreparedStatement.setObject()`로 바인딩할 때, HikariCP proxy + PostgreSQL JDBC 42.7.5 환경에서 타입 등록이 전파되지 않음
- **해결**: `PGvector` 객체 대신 `toVectorLiteral(float[])` → String으로 변환 후 SQL에서 `?::vector` 캐스팅
- **영향**: 검색(SELECT)과 저장(INSERT) 모두 동일 방식 적용

### JdbcTemplate 운영DB/분석DB 혼동
- **원인**: `analyticsJdbc` 빈과 Spring Boot 자동 생성 JdbcTemplate이 공존하면서, `@Qualifier` 없이 주입 시 분석DB JdbcTemplate이 주입됨
- **해결**: `AnalyticsDataSourceConfig`에서 운영DB `JdbcTemplate`을 `@Primary`로 명시 등록
- **영향**: `EmbeddingService`, `ReportGenerationService`, `AiAuditService`, `ReportEditService` 모두 운영DB로 정상 연결

### GeneratedKeyHolder 다중 키 반환
- **원인**: `Statement.RETURN_GENERATED_KEYS`가 PostgreSQL에서 모든 컬럼을 반환 → `getKey()` 실패
- **해결**: `conn.prepareStatement(sql, new String[]{"id"})` — id 컬럼만 반환하도록 지정

### 보고서 초안 70자 잘림
- **원인**: `application-dev.yml`의 `max-tokens: 1024`가 보고서 생성 LLM 호출에도 적용되어 출력이 잘림
- **해결**: `ReportGenerationService`에서 `OpenAiChatOptions.builder().maxTokens(4096)` 명시 지정
- **결과**: 70자 → 876자 충실한 초안 생성

### 학생 이름-ID 혼동
- **원인**: "학생2"라고 질문하면 AI가 이름의 숫자 "2"를 student_id=2로 해석 → 관리자 데이터 반환
- **해결**: 시스템 프롬프트에 "이름에 포함된 숫자를 ID로 사용하지 마세요. 반드시 searchStudentByName을 먼저 호출하세요" 규칙 추가

---

## 3. 임베딩 시드

- 실행: `POST /api/v1/dev/seed/embeddings` (ADMIN 권한)
- 결과: 피드백 544건 + 상담 271건 = 815건, 에러 0건
- 모델: `gemini-embedding-001` (3072차원)
- 저장: `feedback_embeddings`, `counseling_embeddings` 테이블 (운영DB)

---

## 4. 검증 결과

| 데모 프롬프트 | 결과 |
|-------------|------|
| "학생2 성적 추이 알려줘" | 성공 — 학생2(ID:3) 정확 매핑, 6학기 추이 반환, reportId=null |
| "2026년 1학기 수업 태도 문제 있는 학생 찾아줘" | 성공 — RAG 유사도 0.76/0.72, 학생 ID 13/11 반환 |
| "학생2 종합 의견 작성해줘" | 성공 — reportId=8, 876자 초안, references 5건 (피드백3+상담2) |
| GET /reports/{reportId} | 성공 — 초안+근거+수정이력 반환, 다른 학교 교사 → 404 |
| POST /reports/{reportId}/edit | 성공 — 교사 수정본 저장, edit_distance 기록 |
| 대시보드 위험 알림 | 성공 — SYSTEM 알림 필터, empty state 표시 |

---

## 5. API 목록

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/analytics/chat` | AI 챗봇 (응답에 reportId 포함) |
| GET | `/api/v1/analytics/reports/{reportId}` | 보고서 + references + 수정이력 조회 |
| POST | `/api/v1/analytics/reports/{reportId}/edit` | 교사 수정본 저장 |
| POST | `/api/v1/dev/seed/embeddings` | 임베딩 시드 (ADMIN) |

---

## 6. 한계 및 향후 과제

- **종합 의견 관리 페이지 없음**: 현재는 챗봇 내에서만 보고서 확인/수정 가능. 학생별 종합 의견 목록 페이지가 있으면 실용성 향상
- **PDF 보고서 미지원**: 성적표+차트+의견을 PDF로 자동 생성하는 기능은 이번 범위 외
- **RAG 검색 결과 카드화 제한**: AI가 마크다운 텍스트로 응답하므로, 프론트에서 구조화된 카드로 표시하려면 백엔드 응답 구조 변경 필요
- **임베딩 자동화 미구현**: 피드백/상담 등록 시 자동 임베딩은 Kafka Consumer로 확장 가능하나 현재는 수동 시드
