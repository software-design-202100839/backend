# SSCM Project Status

> Last updated: 2026-03-31 by session 2026-03-31-01

## Current Sprint
- Sprint: 3 (Jira ID: 133)
- Period: 2026-03-29 ~ 2026-04-04
- Focus: 클라우드 배포 + 보안

## Active Tasks (Sprint 3)

### 반드시 (Must) — 배포 + 보안
| Jira | Assignee | Summary | Commit Date | Status |
|------|----------|---------|-------------|--------|
| SSCM-53 | 이데브 | ECS Fargate 클러스터 + Task Definition | 3/29 | done |
| SSCM-54 | 이데브 | ALB 경로 라우팅 + Parameter Store | 3/30 | done |
| SSCM-55 | 이백엔드 | 개인정보 암호화 (AES-256-GCM) | 3/31 | done |
| SSCM-56 | 이데브 | CD 완성 (ECR→ECS) + 프로덕션 배포 | 4/1 | todo |
| SSCM-57 | 이큐에이 | Burp Suite DAST | 4/2 | todo |

### 가능하면 (Nice-to-have)
| Jira | Assignee | Summary | Commit Date | Status |
|------|----------|---------|-------------|--------|
| SSCM-58 | 이백엔드 | @Version + @Retryable (Sprint 2 이월) | 4/3 | todo |

### 미생성 (시간 여유 시)
- 보고서 생성 API/UI (새 스택 적용)
- 상담 필터링 보강
- Testcontainers

## Blockers
- (none)

## Sprint 2 완료 요약
| Jira | Assignee | Summary | Completed |
|------|----------|---------|-----------|
| SSCM-46 | 이백엔드 | 피드백 CRUD API | 2026-03-22 |
| SSCM-47 | 이백엔드 | 상담내역 CRUD API + 교사 간 공유 | 2026-03-23 |
| SSCM-48 | 이프론트 | 피드백 UI + 상담 UI | 2026-03-24 |
| SSCM-49 | 이백엔드 | 알림 API + WebSocket + 이메일 | 2026-03-25 |
| SSCM-50 | 이프론트 | 알림 UI + 학부모 대시보드 | 2026-03-26 |
| SSCM-51 | 이데브 | Dockerfile + ECR + CD | 2026-03-27 |
| SSCM-52 | 이큐에이 | Playwright E2E + 영상 녹화 (7/7 통과) | 2026-03-27 |

## Sprint 2 종료 기준 — 전부 달성
- [x] 전 기능 동작 (인증, 성적, 학생부, 피드백, 상담, 알림, 학부모 뷰)
- [x] Playwright E2E 3개 시나리오 7개 테스트 통과 + 영상 녹화 파일 확보
- [x] Docker 이미지 빌드 성공 (backend 517MB + frontend 94MB)
- [x] GitHub Actions CD → ECR 푸시 파이프라인 동작
- [x] JUnit 94개 테스트 통과 (0 fail, 1 skip) + JaCoCo 리포트 (Inst 36%, Branch 25%)

## Sprint 3 종료 기준
- [ ] ECS Fargate에서 backend + frontend 컨테이너 Running
- [ ] ALB URL로 전 기능 접속 가능
- [ ] develop push → 자동 배포 (ECR→ECS) 동작
- [x] 개인정보 암호화 적용 (DB 평문 노출 안 됨) — AES-256-GCM, PR #4
- [ ] Burp Suite DAST 리포트 생성, Critical 0건
- [x] JUnit 테스트 전체 통과 유지 — 106개 (0 fail)

## Next Up (Sprint 4, 4/5~4/11) — 운영 + 모니터링 + 문서화
- Prometheus + Grafana (상시 모니터링)
- Dynatrace APM (보조, 15일 체험)
- PagerDuty (인시던트 관리)
- k6 부하 테스트 → 결과에 따라 Redis @Cacheable 도입 판단
- 백업/복구 절차
- 전체 문서화

## W13~14 — 발표 준비만 집중

## Repos
- Backend: `software-design-202100839/backend` → `/mnt/c/Users/seung/workspace/sscm-backend`
- Frontend: `software-design-202100839/frontend` → `/mnt/c/Users/seung/workspace/sscm-frontend`
- Both on `develop` branch, clean state

## Key Decisions (2026-03-27)
- 인프라 EKS → ECS Fargate 전환 (비용/운영 최적화)
- CD Argo CD → GitHub Actions 직접 배포 전환
- Nginx Ingress → ALB 전환
- SonarQube → SonarCloud 전환
- 모니터링: Prometheus + Grafana 메인 + Dynatrace 보조
- Frontend 점진적 마이그레이션: 보고서 UI(신규)에만 새 스택 적용, 기존 14개 파일 현행 유지
- ADR-003: 모노레포 → 분리 레포 현행화
- SSCM-58 (@Version + @Retryable): Sprint 3 Nice-to-have로 이월
- Sprint 3 작업: 반드시(Must) vs 가능하면(Nice-to-have) 분류
