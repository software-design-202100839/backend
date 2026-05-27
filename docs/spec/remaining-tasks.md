# 남은 작업 계획 (발표 준비 완료까지)

> Last updated: 2026-05-27

---

## 완료 상황 요약

모든 Phase가 완료되었거나 진행 중이다. 원래 계획에 없던 추가 기능도 구현 완료.

---

## Phase A: 모니터링 정상화 (Prometheus + Grafana) — 전체 완료

**A-1. Prometheus 스크래핑 수정** — 완료
- prometheus-prod.yml에서 target을 ALB 엔드포인트로 변경
- ECS 사이드카 방식으로 Prometheus + Grafana 배포
- Prometheus targets 페이지에서 UP 확인 완료

**A-2. Grafana 대시보드 보강** — 완료
- 기존 6개 → **19개 패널**으로 확장 (3계층 구성)
  - 앱 계층 (10개): API p95 응답시간, HTTP 요청 처리량, 5xx 에러, JVM Heap, GC 일시정지, HikariCP 커넥션풀, Kafka Consumer Lag, 스레드 수, Uptime, CPU 사용률
  - 비즈니스 계층 (5개): 성적 등록, 상담 생성, 피드백, 알림, AI 챗봇 사용
  - 인프라 계층 (4개): CloudWatch 연동 — ECS CPU/메모리, RDS 커넥션, Redis 메모리

**A-3. Grafana 알림 확인** — 완료
- 5개 알림 규칙 설정 및 동작 확인
  - 5xx 에러 발생, Heap > 90%, 커넥션풀 > 90%, Consumer Lag 증가, 응답시간 급등

---

## Phase B: 프론트엔드 연동 확인 — 전체 완료

**B-1. CloudFront → API 호출 테스트** — 완료
- CORS에 CloudFront 도메인 추가
- 브라우저에서 전체 흐름 테스트 통과

**B-2. 프론트 API URL 환경변수** — 완료
- VITE_API_BASE_URL 설정 확인 및 재빌드
- S3 업로드 + CloudFront 캐시 무효화 완료

---

## Phase C: 데모 리허설 + 스크린샷 — 대부분 완료

**C-1. 전체 시나리오 1회 통과** — 부분 완료
1. CloudFront → 프론트엔드 로딩 — 확인
2. 교사 로그인 → 성적 등록 — 확인
3. 분석 대시보드 확인 — 확인
4. AI 챗봇 질의 — 확인
5. Grafana 대시보드 확인 — 확인

**C-2. 스크린샷 캡처** — 완료
- AWS ECS/MSK/RDS/CloudFront 콘솔 캡처 완료
- Grafana 대시보드, k6 결과, 프론트엔드, AI 챗봇 화면 캡처 완료

**C-3. 문제 발견 시 수정** — 해당 없음 (주요 이슈 없었음)

---

## Phase D: SonarCloud + 최종 정리 — 전체 완료

**D-1. SonarCloud 확인** — 완료
- Quality Gate 통과
- 커버리지 제외 설정 추가 (config, DTO, entity 등)
- 보안 핫스팟 수정 완료

**D-2. 최종 커밋 + 문서 정리** — 진행 중
- 주요 변경사항 커밋 완료
- 문서 최종 업데이트 진행 중

---

## 원래 계획 외 추가 구현 사항

계획 단계에서 예정하지 않았으나, 프로젝트 품질 향상을 위해 추가 구현한 항목들:

### 1. 멀티테넌시 (ADR-006)
- **School 엔티티** 추가 — 학교별 데이터 격리 기반
- **TenantContext (ThreadLocal)** — 서비스 메서드 시그니처 변경 없이 school_id 전파
- **JWT schoolId claim** — 인증 토큰에 소속 학교 정보 포함
- **교차 학교 접근 차단** — 다른 학교 데이터 접근 시 403 응답
- 전략: Shared-Database, Discriminator-Column (school_id FK)

### 2. AI 챗봇 강화
- 기존 기본 챗봇 → **13개 Tool (Function Calling)** 연결
- **역할별 접근 제어**: 교사 13 tools, 학생/학부모 5 tools
- **대화 히스토리**: 인메모리 관리 (sessionId + 30분 TTL)
- Spring AI + Gemini 2.5 Flash 기반

### 3. 프론트엔드 UI 전면 리디자인
- **Collapsible 사이드바** (220px ↔ 64px) — 메뉴 그룹화 (기록/분석/시스템)
- **StudentDrawer** — 우측 슬라이드 학생 선택 + StudentSummaryHeader
- **PrivacyBadge** — 비공개=회색, 공개=주황 (위험 강조)
- **분석 페이지 탭** — 요약/성적/출결/피드백 4탭으로 스크롤 감소
- **AI 챗봇 마크다운 렌더링** (react-markdown + remark-gfm)
- **대시보드 정보 밀도 개선** — 인사말 + 통계 카드 + 빠른 접근
- **로그인 그라디언트 제거** — 깔끔한 흰색 배경
- **Card shadow 제거** → border만 (Plane.so 스타일)
- **모바일 반응형** — 햄버거 메뉴, 챗봇 전체너비

### 6. 대규모 시드 데이터 (/seed/large)
- 30명 × 3학년도(2024~2026) × 2학기 = **6학기 추이 데이터**
- 성적 900건 (향상/하락/상위고정/하위고정 4그룹 트렌드)
- 피드백 544건, 상담 271건, 학생부 903건 (현실적 한국어 내용)
- Kafka 이벤트 발행 → Analytics DB 자동 집계

### 4. 프론트엔드 CD 파이프라인
- GitHub Actions → npm build → **S3 업로드 + CloudFront 캐시 무효화**
- develop 브랜치 push 시 자동 배포

### 5. 모니터링 3계층 완성
- 앱 계층 (10패널) + 비즈니스 계층 (5패널) + 인프라 계층 (4패널, CloudWatch 연동)
- 5개 알림 규칙 설정

---

## 현재 남은 작업

| 항목 | 상태 | 우선순위 |
|------|------|----------|
| 발표 슬라이드 최종 검토 | 진행 중 | 필수 |
| 문서 최종 업데이트 | 진행 중 | 필수 |
| 데모 리허설 최종 통과 | 대기 | 필수 |
| 발표 후 AWS 스택 삭제 | 대기 | 발표 후 |
