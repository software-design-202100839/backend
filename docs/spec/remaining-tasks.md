# 남은 작업 계획 (발표 준비 완료까지)

> Last updated: 2026-05-29

---

## 완료 상황 요약

모든 개발 Phase가 완료되었다. 원래 계획에 없던 추가 기능(멀티테넌시, Hybrid AI, 프론트 통합)도 구현 완료.
현재 남은 것은 **발표 시연 준비**뿐이다.

---

## Phase A: 모니터링 정상화 (Prometheus + Grafana) — 완료

**A-1. Prometheus 스크래핑 수정** — 완료
- prometheus-prod.yml에서 target을 ALB 엔드포인트로 변경
- ECS 사이드카 방식으로 Prometheus + Grafana 배포

**A-2. Grafana 대시보드 보강** — 완료
- 기존 6개 → **19개 패널**으로 확장 (3계층)
  - 앱 계층 (10개): API p95, HTTP 처리량, 5xx 에러, JVM Heap, GC, HikariCP, Kafka Lag, 스레드, Uptime, CPU
  - 비즈니스 계층 (5개): 성적/상담/피드백/알림 등록, AI 챗봇 사용
  - 인프라 계층 (4개): CloudWatch — ECS CPU/메모리, RDS 커넥션, Redis 메모리

**A-3. Grafana 알림** — 완료
- 5개 알림 규칙 설정 (5xx, Heap>90%, 커넥션풀>90%, Consumer Lag, 응답시간 급등)

---

## Phase B: 프론트엔드 연동 확인 — 완료

**B-1. CloudFront → API 호출 테스트** — 완료
**B-2. 프론트 API URL 환경변수** — 완료

---

## Phase C: Hybrid AI 프론트엔드 통합 — 완료

**C-1. 백엔드 API** — 완료
- ChatResponse에 reportId 포함
- ReportIdHolder ThreadLocal (3중 안전 처리)
- GET /reports/{reportId} + 역할별 접근 제어
- EmbeddingService pgvector 호환성 수정 (?::vector 캐스팅)
- @Primary JdbcTemplate로 운영DB/분석DB 혼동 해결
- 시스템 프롬프트 개선 (학생 이름→ID 검색 우선)
- 보고서 생성 maxTokens 4096 설정

**C-2. 프론트엔드 UI** — 완료
- ReportCard 컴포넌트 (초안 + 참고 근거 접기/펼치기 + 교사 수정 UI)
- RiskAlertCard 컴포넌트 (SYSTEM 알림 필터 + 4개 규칙 유형)
- analyticsService: getReport(), saveReportEdit()

**C-3. 임베딩 시드** — 완료
- 피드백 544건 + 상담 271건 = 815건, 에러 0건
- gemini-embedding-001 (3072차원)

**C-4. 데모 프롬프트 검증** — 완료
- "학생2 성적 추이 알려줘" → 성공 (이름→ID 정확 매핑)
- "수업 태도 문제 있는 학생 찾아줘" → 성공 (RAG 유사도 0.76)
- "학생2 종합 의견 작성해줘" → 성공 (876자 초안, references 5건)

---

## Phase D: SonarCloud + 최종 정리 — 완료

**D-1. SonarCloud** — 완료
- Quality Gate 통과, 보안 핫스팟 수정

**D-2. 커밋** — 완료
- 백엔드: `b7edcba` feat: Hybrid AI 프론트엔드 통합
- 프론트: `0af84b4` feat: Hybrid AI UI

---

## 원래 계획 외 추가 구현 사항

### 1. 멀티테넌시 (ADR-006)
- School 엔티티, TenantContext (ThreadLocal), JWT schoolId claim
- 교차 학교 접근 차단 (403)
- Shared-Database, Discriminator-Column 전략

### 2. Hybrid AI 챗봇 (ADR-007)
- Function Calling 16 tools + RAG(pgvector) + 보고서 생성 + Proactive 알림
- 역할별 접근 제어 (교사 16 tools, 학생/학부모 5 tools)
- Human-in-the-Loop (교사 수정 저장 + edit_distance 추적)
- AI 감사 로그 (ai_request_logs)

### 3. Hybrid AI 프론트엔드 통합 (ADR-008)
- 보고서 카드 UI (초안 + 참고 근거 + 수정 저장)
- 대시보드 위험 알림 카드
- 임베딩 시드 815건

### 4. 프론트엔드 UI 전면 리디자인
- Collapsible 사이드바, StudentDrawer, PrivacyBadge, 분석 탭
- AI 챗봇 마크다운 렌더링 (react-markdown + remark-gfm)

### 5. 대규모 시드 데이터
- 30명 × 6학기, 성적 900건, 피드백 544건, 상담 271건, 학생부 903건

### 6. 프론트엔드 CD + 모니터링 3계층
- GitHub Actions → S3 + CloudFront
- 19 Grafana 패널, 5 알림 규칙

---

## 현재 남은 작업

| 항목 | 상태 | 우선순위 |
|------|------|----------|
| 발표 대본 최종 점검 | 대기 | 필수 |
| Q&A 대비 최종 점검 | 대기 | 필수 |
| AWS 인프라 재기동 | 대기 | 필수 (발표 전) |
| 프로덕션 임베딩 시드 | 대기 | 필수 (인프라 올린 후) |
| 데모 리허설 최종 통과 | 대기 | 필수 |
| git push (백엔드+프론트) | 대기 | 인프라 올린 후 |
| 발표 후 AWS 스택 삭제 | 대기 | 발표 후 |
