# SSCM Project Status

> Last updated: 2026-05-29

## 프로젝트 현황 요약

SSCM(Smart School Class Management) 프로젝트의 모든 개발 스프린트가 완료되었으며, AWS 풀 아키텍처가 프로덕션 환경에 배포되어 운영 중이다. Hybrid AI 프론트엔드 통합 및 임베딩 시드까지 완료하여 **발표 시연 준비 완료** 상태이다.

### 주요 완료 항목

| 영역 | 상태 | 요약 |
|------|------|------|
| 코어 기능 (Sprint 1~4) | 완료 | 성적, 학생부, 상담, 피드백, 알림, 관리자 CRUD |
| OLAP 분석 파이프라인 | 완료 | Kafka 이벤트 → 분석 DB, 대시보드, 집계 API |
| 멀티테넌시 (ADR-006) | 완료 | School 엔티티, JWT schoolId, TenantContext, 교차 학교 접근 차단 |
| AI 챗봇 백엔드 (ADR-007) | 완료 | Hybrid AI — FC 16 tools + RAG(pgvector) + 보고서 생성 + Proactive 알림 + HITL + 감사 로그 |
| AI 챗봇 프론트엔드 (ADR-008) | 완료 | 보고서 카드 + 참고 근거 UI + 교사 수정 저장 + 위험 알림 카드 |
| 임베딩 시드 | 완료 | 피드백 544건 + 상담 271건 = 815건 벡터화 (gemini-embedding-001, 3072차원) |
| 모니터링 3계층 | 완료 | 앱 10 + 비즈니스 5 + 인프라 4 = 19 Grafana 패널, 5 알림 규칙 |
| AWS 풀 아키텍처 | 배포 완료 | ECS Fargate + ALB + RDS x2 + Redis + MSK + CloudFront |
| CI/CD | 완료 | 백엔드: GitHub Actions → ECR → ECS 자동 배포, 프론트: S3 + CloudFront CD |
| SonarCloud | 개선 완료 | 커버리지 제외 설정, 보안 핫스팟 수정, Quality Gate 통과 |
| 부하 테스트 | 완료 | k6 200VU 기준 p95 791ms, 140 req/s, 에러율 0% |
| 프론트엔드 UX | 완료 | UI 전면 리디자인 — Collapsible 사이드바, StudentDrawer, PrivacyBadge, 분석 탭, 마크다운 렌더링 |
| 대규모 시드 데이터 | 완료 | 30명 × 3학년도 × 2학기, 성적 900건, 피드백 544건, 상담 271건, 학생부 903건 |

---

## 스프린트 이력

| Sprint | 기간 | 주요 내용 | 회고 |
|--------|------|-----------|------|
| Sprint 1 | 2026-03-15 ~ 03-22 | 인증, 성적 CRUD, 프론트 초기 구축 | `docs/sprint/sprint-1.md` |
| Sprint 2 | 2026-03-22 ~ 03-29 | 학생부, 상담, 피드백, 알림 | `docs/sprint/sprint-2.md` |
| Sprint 3 | 2026-03-29 ~ 04-04 | OLAP 분석, Kafka, 관리자 기능 | `docs/sprint/sprint-3.md` |
| Sprint 4 | 2026-04-05 ~ 04-11 | 모니터링, 부하 테스트, SonarCloud | `docs/sprint/sprint-4.md` |
| Sprint 5+ | 2026-04-11 ~ 05-29 | AWS 배포, 멀티테넌시, Hybrid AI, 프론트 통합, 임베딩 시드 | — |

---

## AI 기능 검증 결과 (2026-05-28 실측)

| 데모 프롬프트 | 결과 |
|-------------|------|
| "학생2 성적 추이 알려줘" | 성공 — 학생2(ID:3) 정확 매핑, 6학기 추이, reportId=null |
| "2026년 1학기 수업 태도 문제 있는 학생 찾아줘" | 성공 — RAG 유사도 0.76/0.72, 관련 학생 반환 |
| "학생2 종합 의견 작성해줘" | 성공 — 876자 초안, references 5건 (피드백3+상담2), 보고서 카드 표시 |
| GET /reports/{reportId} (다른 학교 교사) | 성공 — 404 반환 (접근 차단) |
| POST /reports/{reportId}/edit | 성공 — 교사 수정본 저장, edit_distance 기록 |

---

## AWS 인프라 현황

### 프로덕션 리소스

| 리소스 | 스펙 | 엔드포인트/식별자 |
|--------|------|-------------------|
| ECS 클러스터 | `sscm-cluster` | backend + monitoring 서비스 |
| Backend Task | 0.5 vCPU / 1GB | ECS Fargate |
| Monitoring Task | 0.25 vCPU / 0.5GB | Prometheus + Grafana 사이드카 |
| ALB | 경로 라우팅 | `/api/*` → backend, `/grafana/*` → monitoring |
| RDS (운영) | db.t3.micro | `sscm-db` (PostgreSQL 16 + pgvector) |
| RDS (분석) | db.t3.micro | `sscm-analytics-db` (PostgreSQL 16) |
| ElastiCache Redis | cache.t3.micro | JWT 블랙리스트 L1 캐시 |
| MSK Kafka | kafka.t3.small x2 | 이벤트 파이프라인 (OLAP + 위험 감지) |
| CloudFront + S3 | — | 프론트엔드 CDN 배포 |
| ECR | — | `sscm-backend`, `sscm-frontend` 이미지 저장소 |

### 비용

- 전체 스택 가동 시: ~$47/월 (~$1.5/일)
- 사용 안 할 때: sscm-ecs 스택 삭제 (Fargate 과금 중지)
- 발표 종료 후: 3개 스택 전부 삭제 예정

---

## CI/CD 파이프라인

### 백엔드 (자동 배포)
```
git push develop → GitHub Actions CI → test + SonarCloud → Docker build → ECR push → ECS force-new-deployment
```

### 프론트엔드 (자동 배포)
```
git push develop → GitHub Actions CD → npm build → S3 업로드 → CloudFront 캐시 무효화
```

---

## SonarCloud 품질

- Quality Gate: 통과
- 커버리지 제외 설정 적용 (config, DTO, entity 등)
- 보안 핫스팟 수정 완료

---

## 부하 테스트 결과

### 기존 Redis 도입 효과 (프로덕션 AWS, 200 VU)

| 항목 | 수치 |
|------|------|
| p50 | 297.4ms |
| p95 | 791.2ms |
| 처리량 | 140 req/s |
| 에러율 | 0% |
| 환경 | 0.5 vCPU / 1GB, 200 VU, 3분 30초 |

### DB 분리 A/B 테스트 (로컬, 2026-06-01 실측)

대규모 시드: 3개 학교, 학생 3,000명, 성적 90,000건, 피드백 18,000건, 상담 9,000건

| 항목 | Case A (미분리) | Case B (OLTP only) | Case B-2 (분리) |
|------|:---:|:---:|:---:|
| 시나리오 | OLTP + 분석(운영 DB) | OLTP만 | OLTP + 분석(Analytics DB) |
| score_read avg | 17.21ms | 9.37ms | 10.91ms |
| **score_read p95** | **51ms** | **12ms** | **20ms** |
| score_read med | 10ms | 9ms | 9ms |
| analytics avg | 120ms | - | 170ms |
| 에러율 | 0% | 0% | 0% |
| VU | 50 OLTP + 10 OLAP | 50 OLTP | 50 OLTP + 10 OLAP |

**핵심**: DB 분리 시 동일 분석 부하에서 OLTP p95가 51ms → 20ms (2.6배 개선)

k6 결과 원본: `k6/results/case-a-result.txt`, `case-b-result.txt`, `case-b2-result.txt`

### Kafka Consumer 중단/재개 테스트 (로컬, 2026-06-02 실측)

| 확인 항목 | 결과 |
|----------|------|
| Consumer pause/resume API | 200 OK (6개 Consumer 전부) |
| pause 중 성적 수정 API 성공률 | **100%** (61/61) |
| pause 중 성적 수정 에러율 | **0%** |
| resume 후 Analytics DB 반영 | 확인 (student 30: avg 68→79.4, updated_at 변경) |

**핵심**: Consumer가 중단되어도 운영 API는 100% 정상 동작. Kafka에 이벤트가 쌓였다가 Consumer 재개 후 분석 DB에 자동 반영.

k6 결과 원본: `k6/results/kafka-isolation-fixed-result.txt`

---

## 현재 Focus

- **발표 시연 준비** (인프라 재기동, 데모 리허설, 수치 확인)
- DB 분리 A/B 테스트 완료 (p95: 51ms→20ms, 2.6배 개선)
- Kafka 장애 격리 테스트 완료 (Consumer 중단 시 API 성공률 100%)
- 로컬 커밋 완료, push는 인프라 올린 후 진행 예정

---

## Blockers

- (none)

---

## Repos

- Backend: `software-design-202100839/backend` → `/mnt/c/Users/seung/workspace/sscm-backend`
- Frontend: `software-design-202100839/frontend` → `/mnt/c/Users/seung/workspace/sscm-frontend`
- Both on `develop` branch
