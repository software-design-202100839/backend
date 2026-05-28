# Hybrid AI Architecture 최종 구현 계획 (발표용 완성형)

> 작성: 2026-05-28
> 목적: Function Calling만 사용하던 챗봇을 **RAG + 보고서 생성 + Proactive 알림 + Human-in-the-Loop**를 갖춘 교육 업무 AI로 고도화
> 스코프: **최소 구현/향후 확장 없음. 전부 구현.**

---

## 1. 왜 이걸 하는가

### 현재 상태
- 챗봇이 13개 Function Calling Tool로 **정형 데이터만 조회**
- "김민수 평균 점수?" → 가능
- "수업 태도 문제 있는 학생?" → 불가능 (텍스트 의미 검색 안 됨)
- "학기말 종합 의견 작성해줘" → 불가능 (생성 기능 없음)
- 위험 학생 발생해도 교사가 직접 확인해야 함 (Proactive 알림 없음)

### 개선 후 상태
- **정형 데이터** → Function Calling (기존)
- **비정형 텍스트** → RAG + pgvector 의미 검색 (신규)
- **보고서 생성** → Function Calling + RAG + LLM 하이브리드 (신규)
- **위험 감지** → Kafka Consumer 기반 Proactive 알림 (신규)
- **품질 개선** → 교사 수정 로그 수집 → 프롬프트 개선 (신규)

### 발표 핵심 메시지
> "기존 챗봇은 데이터를 조회하는 AI였다. 개선 후 챗봇은 교사의 기록 업무를 줄이고, 위험 학생을 먼저 감지하고, 근거 기반으로 학생 이해를 돕는 교육 업무 AI가 되었다."

---

## 2. 기술 선택 (근거 포함)

### Vector DB: pgvector

| 후보 | 장점 | 단점 | 판정 |
|------|------|------|:----:|
| **pgvector** | 기존 PostgreSQL 확장, 인프라 $0, Spring AI 지원 | 전용 DB보다 대규모에서 느림 | ✅ 채택 |
| Chroma | 가볍고 빠름 | 별도 Docker 필요, Java 지원 약함 | ❌ |
| Pinecone | 관리형 SaaS | 유료, 외부 의존성 | ❌ |
| Milvus/Weaviate | 대규모에 강함 | 인프라 과잉 | ❌ |

**선택 이유**: 학교 피드백/상담 데이터는 수천~수만 건 규모. pgvector의 IVFFlat 인덱스로 충분. 별도 인프라 비용 $0.

### Embedding 모델: Gemini text-embedding-004

| 후보 | 비용 | 품질 | 판정 |
|------|------|------|:----:|
| **Gemini embedding** | 무료 (AI Studio) | 좋음 (3072차원) | ✅ 채택 |
| OpenAI text-embedding-3-small | $0.02/1M tokens | 최고 | ❌ 유료 |
| 로컬 sentence-transformers | 무료 | 중간 | ❌ GPU 필요 |

**선택 이유**: 이미 Gemini API 키 보유. 무료. 한국어 지원.

**⚠️ 주의**: Gemini embedding 기본 출력은 3072차원이지만 `output_dimensionality=768`로 지정 가능. DB `vector(n)` 차원과 반드시 일치시켜야 함. 실제 반환 차원 확인 후 스키마 설정.

### RAG 프레임워크: Spring AI 내장

| 후보 | 장점 | 단점 | 판정 |
|------|------|------|:----:|
| **Spring AI RAG** | 기존 스택과 통합, Advisor 패턴 | M6 마일스톤 | ✅ 채택 |
| LangChain4j | 기능 풍부 | 별도 프레임워크, Spring AI와 중복 | ❌ |
| LangChain (Python) | 커뮤니티 최대 | Java 프로젝트에 Python 혼합 비현실적 | ❌ |

**선택 이유**: 이미 Spring AI로 Function Calling 구현. 동일 프레임워크에서 VectorStore + EmbeddingModel 확장이 자연스러움.

---

## 3. 구현 상세 (7가지)

### 3-1. RAG + 메타데이터 필터링

**목적**: 피드백/상담 텍스트를 의미 기반으로 검색. "수업 태도 문제 있는 학생"처럼 표현이 달라도 의미가 유사한 기록을 찾음.

**DB 스키마** (V10 마이그레이션):
```sql
-- pgvector 확장
CREATE EXTENSION IF NOT EXISTS vector;

-- 피드백 임베딩
CREATE TABLE feedback_embeddings (
    id BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT NOT NULL REFERENCES feedbacks(id),
    student_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    academic_year INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    category VARCHAR(50),
    content_preview VARCHAR(200),  -- 검색 결과에 표시할 미리보기
    embedding vector(3072),         -- Gemini embedding 3072차원
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_feedback_emb_school ON feedback_embeddings(school_id);
-- IVFFlat 인덱스: 데이터 삽입 후 생성이 더 효율적 (pgvector 공식 권장)
-- 시드 데이터 삽입 후 별도로 CREATE INDEX 실행
-- CREATE INDEX idx_feedback_emb_vector ON feedback_embeddings USING ivfflat (embedding vector_cosine_ops);

-- 상담 임베딩 (동일 구조)
CREATE TABLE counseling_embeddings (
    id BIGSERIAL PRIMARY KEY,
    counseling_id BIGINT NOT NULL REFERENCES counselings(id),
    student_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    academic_year INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    category VARCHAR(50),
    content_preview VARCHAR(200),
    embedding vector(3072),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_counsel_emb_school ON counseling_embeddings(school_id);
-- IVFFlat 인덱스: 시드 데이터 삽입 후 생성
-- CREATE INDEX idx_counsel_emb_vector ON counseling_embeddings USING ivfflat (embedding vector_cosine_ops);
```

**메타데이터 필터링** (검색 전 필수 적용):
```sql
SELECT fe.*, 1 - (fe.embedding <=> :queryEmbedding) AS similarity
FROM feedback_embeddings fe
WHERE fe.school_id = :schoolId           -- 멀티테넌시 격리
  AND fe.academic_year = :year           -- 학년도 필터 (선택)
  AND fe.semester = :semester            -- 학기 필터 (선택)
  AND fe.student_id IN (:allowedIds)     -- 역할 기반 학생 범위
ORDER BY fe.embedding <=> :queryEmbedding
LIMIT 10;
```

**왜 메타데이터 필터가 필수인가**: 단순 벡터 유사도만 쓰면 다른 학교/다른 반 학생 데이터가 섞임. 학교 데이터는 민감하므로 **AI가 접근할 수 있는 범위를 사전에 제한**.

**새 Tool 2개**:
- `semanticSearchFeedback(query, year?, semester?)` — 피드백 의미 검색
- `semanticSearchCounseling(query, year?, semester?)` — 상담 의미 검색

**동작 예시**:
```
교사: "수업 태도 문제 있는 학생 찾아줘"
→ "수업 태도 문제"를 임베딩
→ school_id=현재학교 필터 + 벡터 유사도 검색
→ 유사한 피드백 10건 반환 (학생 이름, 날짜, 내용 미리보기)
→ AI가 요약해서 답변
```

---

### 3-2. 학기말 종합 의견 자동 생성

**목적**: 교사가 가장 시간 쓰는 업무 — 학기말 학생부 종합 의견 작성 — 을 AI가 초안으로 지원.

**파이프라인**:
```
generateStudentReport(studentId, year, semester)

Step 1: Function Calling — 정형 데이터 수집
  ├─ 성적 요약 (평균, 등급, 추이)
  ├─ 출결 현황
  ├─ 피드백 카테고리별 건수
  └─ 상담 카테고리별 건수

Step 2: RAG — 비정형 데이터 검색
  ├─ 해당 학생의 해당 학기 피드백 텍스트 (유사도 상위 5건)
  └─ 해당 학생의 해당 학기 상담 텍스트 (유사도 상위 3건)

Step 3: LLM — 초안 생성
  ├─ 수집된 정형+비정형 데이터를 컨텍스트로 전달
  ├─ 종합 의견 초안 생성
  └─ 근거 기록 ID 함께 반환
```

**근거 표시 (필수)**:
```json
{
  "draft": "수업 참여도가 높으며 최근 과제 수행 태도가 개선되고 있습니다...",
  "references": [
    { "type": "feedback", "id": 123, "date": "2026-04-12", "preview": "수업 중 적극적 참여" },
    { "type": "counseling", "id": 45, "date": "2026-05-03", "preview": "진로 목표 설정 후 학습 태도 개선" },
    { "type": "score", "summary": "평균 68.0 → 72.5 (4.5점 상승)" }
  ]
}
```

**왜 근거가 필수인가**: 근거 없이 AI가 "학습 태도가 좋습니다"라고만 하면 교사가 신뢰할 수 없음. 어떤 기록을 참고했는지 보여줘야 교사가 검수 가능.

---

### 3-3. Human-in-the-Loop + 수정 로그 분석

**목적**: AI 초안의 품질을 지속적으로 개선하기 위해 교사의 수정 패턴을 수집.

**DB 스키마**:
```sql
-- AI 생성 보고서
CREATE TABLE ai_generated_reports (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    academic_year INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,  -- 프롬프트 버전 추적
    model_name VARCHAR(50) NOT NULL,      -- gemini-2.5-flash
    draft_text TEXT NOT NULL,             -- AI 초안
    reference_ids JSONB,                  -- 참조 기록 ID 목록
    created_by BIGINT NOT NULL,           -- 요청한 교사
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 교사 수정본
CREATE TABLE teacher_report_edits (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES ai_generated_reports(id),
    final_text TEXT NOT NULL,              -- 교사 최종본
    edit_distance INTEGER,                 -- 수정 정도 (레벤슈타인 거리)
    edited_sections JSONB,                 -- 어떤 부분을 고쳤는지
    edited_by BIGINT NOT NULL,
    edited_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**수정 패턴 분석 → 프롬프트 개선 흐름**:
```
1. 데이터 수집: AI 초안 vs 교사 최종본 diff
2. 패턴 분석: "교사들이 성적 서술을 80% 확률로 줄인다"
3. 프롬프트 반영: "성적은 한 줄로 요약하세요" 추가
4. prompt_version 올림 (v1 → v2)
5. 다음 생성부터 개선된 프롬프트 사용
```

**이것은 Fine-tuning이 아님**: 모델 가중치를 재학습하는 것이 아니라, 프롬프트(지시문)를 더 잘 쓰는 것. Fine-tuning은 데이터 수백~수천 건 + GPU 비용 + 개인정보 동의가 필요하므로 현재 단계에서는 적절하지 않음.

---

### 3-4. AI 감사 로그

**목적**: AI가 어떤 학생 데이터를 조회했는지 기록. 권한 문제 추적 + 사용 패턴 분석.

**DB 스키마**:
```sql
CREATE TABLE ai_request_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    question TEXT NOT NULL,
    intent_type VARCHAR(30),          -- STRUCTURED_QUERY, SEMANTIC_SEARCH, REPORT_GENERATION
    used_tools TEXT[],                -- 사용된 Tool 이름 목록
    accessed_student_ids BIGINT[],    -- 접근한 학생 ID 목록
    response_summary VARCHAR(500),    -- 응답 요약 (전문 저장 X)
    latency_ms INTEGER,               -- 응답 시간
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_log_school ON ai_request_logs(school_id);
CREATE INDEX idx_ai_log_user ON ai_request_logs(user_id);
```

**발표용 설명**: "AI가 어떤 학생 데이터를 조회했는지 로그로 남겨, 추후 권한 문제나 오답 이슈를 추적할 수 있게 했습니다."

---

### 3-5. Proactive 알림 봇

**목적**: 교사가 질문하기 전에, 위험 패턴을 감지해서 먼저 알려줌.

**아키텍처**:
```
Kafka 이벤트
  ├─ ScoreCreated/Updated
  ├─ FeedbackCreated
  ├─ CounselingCreated
  └─ RecordCreated (출결)
       ↓
  RiskDetectionConsumer (새 Kafka Consumer)
       ↓
  위험 규칙 매칭
       ↓
  매칭 시 → Notification 생성 + 담임에게 알림
```

**위험 감지 규칙 4가지**:

| 규칙 | 조건 | 알림 메시지 |
|------|------|-----------|
| 성적 급락 | 직전 학기 대비 평균 10점+ 하락 | "⚠️ [김민수] 성적이 크게 하락했습니다 (80→68, -12점)" |
| 부정 피드백 누적 | 최근 30일 내 BEHAVIOR/ATTENDANCE 피드백 3건+ | "⚠️ [김민수] 부정 피드백이 누적되고 있습니다 (3건/30일)" |
| 상담 미진행 | 위험도 MEDIUM+ 인데 최근 30일 상담 0건 | "⚠️ [김민수] 위험 학생이지만 30일 이상 상담이 없습니다" |
| 출결 이상 | 최근 30일 결석/지각 3회+ | "⚠️ [김민수] 출결 이상이 감지되었습니다 (결석 2회, 지각 1회)" |

**오탐 방지**:
- 동일 학생 + 동일 규칙 → **7일 쿨다운** (반복 알림 차단)
- DB에 `last_alert_at` 저장하여 쿨다운 체크

**알림 끄기 기능**:
```sql
CREATE TABLE alert_suppressions (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    rule_type VARCHAR(30) NOT NULL,   -- SCORE_DROP, NEGATIVE_FEEDBACK, etc.
    suppressed_until TIMESTAMP,        -- NULL이면 영구 비활성
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(teacher_id, student_id, rule_type)
);
```

교사가 "이 학생의 성적 하락 알림 끄기" → `alert_suppressions`에 INSERT → Consumer가 알림 생성 전 체크.

**중복 이벤트 방지**: Kafka Consumer는 동일 이벤트를 재처리할 수 있으므로, notification 생성 전 `(student_id, rule_type, event_date)` 조합으로 중복 체크.

**발표용 설명**: "Kafka 이벤트 스트림을 OLAP 집계뿐 아니라 위험 학생 감지에도 활용했습니다. 하나의 이벤트를 여러 Consumer가 독립적으로 소비하는 것이 Kafka를 선택한 이유이기도 합니다."

---

### 3-6. 권한 필터링 강화

**원칙**: AI가 전체 데이터를 자유롭게 검색하지 않음. 현재 사용자가 접근 가능한 데이터 안에서만 검색.

| 역할 | Function Calling 범위 | RAG 검색 범위 |
|------|---------------------|-------------|
| TEACHER | 본인 학교 전체 학생 | 본인 학교 피드백/상담만 |
| STUDENT | 본인 데이터만 | 본인 피드백/상담만 |
| PARENT | 자녀 데이터만 | 자녀 피드백/상담만 |

**구현**: RAG 검색 쿼리에 `WHERE school_id = :schoolId AND student_id IN (:allowedIds)` 강제 적용.

---

### 3-7. 평가 세트

AI 품질 검증용 10개 테스트 케이스:

| # | 유형 | 질문 | 성공 기준 |
|---|------|------|----------|
| 1 | 정형 조회 | "학생2의 성적 추이 알려줘" | 6학기 데이터 반환 |
| 2 | 정형 조회 | "수학 상위 5명 알려줘" | 랭킹 정확 |
| 3 | 의미 검색 | "수업 태도 문제 있는 학생 찾아줘" | 관련 피드백 포함 학생 반환 |
| 4 | 의미 검색 | "교우관계 상담 있었던 학생" | 관련 상담 포함 학생 반환 |
| 5 | 보고서 | "학생2 종합 의견 작성해줘" | 성적+피드백+상담 반영 초안 |
| 6 | 근거 | 보고서의 근거 표시 | 참조 기록 ID 포함 |
| 7 | 권한 | 한빛중 교사 → 새별중 학생 검색 | 0건 또는 거절 |
| 8 | 권한 | 학부모 → 다른 학생 질문 | 거절 응답 |
| 9 | 시계열 | "최근 태도 변화 있는 학생" | 최근 학기 우선 검색 |
| 10 | 대화 기억 | "걔 상담 내역은?" | 이전 학생 기억 |

---

## 4. 파일 변경 목록

### 신규 파일 (7개)

| 파일 | 설명 |
|------|------|
| `src/main/resources/db/migration/V10__ai_rag_proactive.sql` | pgvector + embedding + 보고서 + 알림 억제 + 감사 로그 테이블 |
| `src/main/java/com/sscm/analytics/config/VectorStoreConfig.java` | PgVectorStore + EmbeddingModel 빈 설정 |
| `src/main/java/com/sscm/analytics/chatbot/service/EmbeddingService.java` | 임베딩 저장/검색 + 메타데이터 필터 |
| `src/main/java/com/sscm/analytics/chatbot/service/ReportGenerationService.java` | 종합 의견 파이프라인 |
| `src/main/java/com/sscm/analytics/chatbot/service/AiAuditService.java` | 감사 로그 저장 |
| `src/main/java/com/sscm/analytics/consumer/RiskDetectionConsumer.java` | Kafka 위험 감지 Consumer |
| `src/main/java/com/sscm/analytics/service/AlertSuppressionService.java` | 알림 끄기/켜기 |

### 수정 파일 (5개)

| 파일 | 변경 |
|------|------|
| `build.gradle.kts` | spring-ai-pgvector-store + spring-ai-gemini 의존성 |
| `AnalyticsTools.java` | RAG Tool 2개 + 보고서 Tool 1개 추가 (총 16개) |
| `ChatbotService.java` | 시스템 프롬프트 업데이트 + 감사 로그 연동 |
| `DevSeedController.java` | 시드 데이터 임베딩 생성 추가 |
| `KafkaConfig.java` | RiskDetection Consumer Group 설정 (선택) |

---

## 5. 구현 순서

| 순서 | 작업 | 예상 시간 |
|------|------|----------|
| 1 | V10 마이그레이션 + VectorStoreConfig | 1시간 |
| 2 | EmbeddingService (임베딩 저장/검색) | 2시간 |
| 3 | RAG Tool 2개 (semanticSearch) | 1시간 |
| 4 | ReportGenerationService + Tool | 2시간 |
| 5 | AiAuditService + ChatbotService 연동 | 1시간 |
| 6 | Human-in-the-Loop 테이블 + 저장 로직 | 1시간 |
| 7 | RiskDetectionConsumer + AlertSuppression | 2시간 |
| 8 | 시드 데이터 임베딩 + 테스트 | 1시간 |
| 9 | 평가 세트 10개 검증 | 1시간 |
| **합계** | | **~12시간** |

---

## 6. 검증 체크리스트

- [ ] "수업 태도 문제 있는 학생" → 의미 기반 피드백 검색 성공
- [ ] "학생2 종합 의견 작성해줘" → 근거 포함 초안 생성
- [ ] 보고서 근거에 참조 기록 ID 표시
- [ ] 교사 수정 후 저장 → teacher_report_edits에 기록
- [ ] 다른 학교 학생 RAG 검색 → 0건
- [ ] 성적 급락 → 담임에게 Proactive 알림 생성
- [ ] 알림 끄기 → 해당 학생 알림 중지
- [ ] AI 요청 → ai_request_logs에 기록
- [ ] 평가 세트 10개 중 8개 이상 통과
- [ ] 기존 Function Calling 13개 Tool 정상 동작 유지

---

## 7. 발표 데모 흐름 (하나의 스토리)

> "교사가 학생 문제를 찾고 → AI가 근거를 찾아주고 → 보고서 초안을 만들고 → 교사가 검수하고 → 시스템이 다음 위험 상황을 먼저 알려준다."

1. **교사 로그인** → 권한 범위 확인
2. **RAG 의미 검색** → "수업 태도 문제 있는 학생 찾아줘" → 관련 피드백 기록 반환
3. **종합 의견 생성** → "학생2 학기말 종합 의견 작성해줘" → 근거 포함 초안
4. **교사 수정 저장** → AI 초안 수정 → Human-in-the-Loop
5. **Proactive 알림 확인** → 성적 급락 이벤트 → 담임 알림 생성
6. **감사 로그 확인** → AI가 접근한 학생/도구 기록

---

## 8. 최종 발표 메시지

> **"SSCM의 AI는 단순 조회 챗봇이 아니라, 정형 데이터 조회(Function Calling), 비정형 기록 검색(RAG), 보고서 생성(Hybrid), 교사 검수(Human-in-the-Loop), 위험 사전 알림(Proactive)까지 연결한 교육 업무 AI로 발전했다."**

> **"조회형 챗봇 → 근거 기반 교육 업무 AI"**
