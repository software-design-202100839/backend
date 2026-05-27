# SSCM Project Status

> Last updated: 2026-05-27

## 프로젝트 현황 요약

SSCM(Smart School Class Management) 프로젝트의 모든 개발 스프린트가 완료되었으며, AWS 풀 아키텍처가 프로덕션 환경에 배포되어 운영 중이다. 현재는 **발표 준비** 단계에 집중하고 있다.

### 주요 완료 항목

| 영역 | 상태 | 요약 |
|------|------|------|
| 코어 기능 (Sprint 1~4) | 완료 | 성적, 학생부, 상담, 피드백, 알림, 관리자 CRUD |
| OLAP 분석 파이프라인 | 완료 | Kafka 이벤트 → 분석 DB, 대시보드, 집계 API |
| 멀티테넌시 (ADR-006) | 완료 | School 엔티티, JWT schoolId, TenantContext, 교차 학교 접근 차단 |
| AI 챗봇 | 완료 | Spring AI + Gemini 2.5 Flash, 13개 Tool, 역할별 접근 제어, 대화 히스토리 |
| 모니터링 3계층 | 완료 | 앱 10 + 비즈니스 5 + 인프라 4 = 19 Grafana 패널, 5 알림 규칙 |
| AWS 풀 아키텍처 | 배포 완료 | ECS Fargate + ALB + RDS x2 + Redis + MSK + CloudFront |
| CI/CD | 완료 | 백엔드: GitHub Actions → ECR → ECS 자동 배포, 프론트: S3 + CloudFront CD |
| SonarCloud | 개선 완료 | 커버리지 제외 설정, 보안 핫스팟 수정, Quality Gate 통과 |
| 부하 테스트 | 완료 | k6 200VU 기준 p95 791ms, 140 req/s, 에러율 0% |
| 프론트엔드 UX | 완료 | UI 전면 리디자인 — Collapsible 사이드바, StudentDrawer, PrivacyBadge, 분석 탭, 마크다운 렌더링 |
| 대규모 시드 데이터 | 완료 | /seed/large: 30명 × 3학년도 × 2학기, 성적 900건, 피드백 544건, 상담 271건, 학생부 903건 |

---

## 스프린트 이력

| Sprint | 기간 | 주요 내용 | 회고 |
|--------|------|-----------|------|
| Sprint 1 | 2026-03-15 ~ 03-22 | 인증, 성적 CRUD, 프론트 초기 구축 | `docs/sprint/sprint-1.md` |
| Sprint 2 | 2026-03-22 ~ 03-29 | 학생부, 상담, 피드백, 알림 | `docs/sprint/sprint-2.md` |
| Sprint 3 | 2026-03-29 ~ 04-04 | OLAP 분석, Kafka, 관리자 기능 | `docs/sprint/sprint-3.md` |
| Sprint 4 | 2026-04-05 ~ 04-11 | 모니터링, 부하 테스트, SonarCloud | `docs/sprint/sprint-4.md` |
| Sprint 5+ | 2026-04-11 ~ 05-27 | AWS 배포, 멀티테넌시, AI 챗봇 강화, 프론트 CD | — |

---

## AWS 인프라 현황

### 프로덕션 리소스

| 리소스 | 스펙 | 엔드포인트/식별자 |
|--------|------|-------------------|
| ECS 클러스터 | `sscm-cluster` | backend + frontend + monitoring 서비스 |
| Backend Task | 0.5 vCPU / 1GB | ECS Fargate |
| Frontend Task | 0.25 vCPU / 0.5GB | ECS Fargate |
| Monitoring Task | 0.25 vCPU / 0.5GB | Prometheus + Grafana 사이드카 |
| ALB | 경로 라우팅 | `/api/*` → backend, `/grafana/*` → monitoring, `/*` → frontend |
| RDS (운영) | db.t3.micro | `sscm-db` (PostgreSQL 16) |
| RDS (분석) | db.t3.micro | `sscm-analytics-db` (PostgreSQL 16) |
| ElastiCache Redis | cache.t3.micro | JWT 블랙리스트 L1 캐시 |
| MSK Kafka | kafka.t3.small x2 | 이벤트 파이프라인 (OLAP) |
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

| 항목 | 수치 |
|------|------|
| p50 | 297.4ms |
| p95 | 791.2ms |
| 처리량 | 140 req/s |
| 에러율 | 0% |
| 환경 | 0.5 vCPU / 1GB, 200 VU, 3분 30초 |

---

## 현재 Focus

- **발표 준비** (아키텍처 다이어그램, Q&A, 트레이드오프, 데모 리허설)
- 스크린샷 캡처 완료
- 슬라이드 구성안 작성 완료

---

## Blockers

- (none)

---

## Repos

- Backend: `software-design-202100839/backend` → `/mnt/c/Users/seung/workspace/sscm-backend`
- Frontend: `software-design-202100839/frontend` → `/mnt/c/Users/seung/workspace/sscm-frontend`
- Both on `develop` branch
