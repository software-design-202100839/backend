# SSCM Project Status

> Last updated: 2026-04-03 by session 2026-04-03-01

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
| SSCM-56 | 이데브 | CD 완성 (ECR→ECS) + 프로덕션 배포 | 4/1~4/3 | done |
| SSCM-57 | 이큐에이 | Burp Suite DAST | 4/4 | todo |

### 가능하면 (Nice-to-have)
| Jira | Assignee | Summary | Commit Date | Status |
|------|----------|---------|-------------|--------|
| SSCM-58 | 이백엔드 | @Version + @Retryable (Sprint 2 이월) | - | todo |

### 미생성 (시간 여유 시)
- 학생 로그인 UX 개선 (내 정보만 바로 보이도록)
- 보고서 생성 API/UI (새 스택 적용)
- 상담 필터링 보강
- Testcontainers

## Sprint 3 종료 기준
- [x] ECS Fargate에서 backend + frontend 컨테이너 Running
- [x] ALB URL로 전 기능 접속 가능
- [x] develop push → 자동 배포 (ECR→ECS) 동작
- [x] 개인정보 암호화 적용 (DB 평문 노출 안 됨) — AES-256-GCM, PR #4
- [ ] Burp Suite DAST 리포트 생성, Critical 0건
- [x] JUnit 테스트 전체 통과 유지 — 106개 (0 fail)
- [x] Playwright E2E 프로덕션 7/7 통과

## AWS 인프라 현황
- ALB: `sscm-alb-162195414.ap-northeast-2.elb.amazonaws.com`
- ECS 클러스터: `sscm-cluster` (backend + frontend 서비스)
- RDS: `sscm-db.cvsqwimyifjw.ap-northeast-2.rds.amazonaws.com` (PostgreSQL 16)
- Redis: `sscm-redis.xvtaov.0001.apn2.cache.amazonaws.com`
- ECR: `sscm-backend`, `sscm-frontend`
- CloudFormation 스택: `sscm-alb`, `sscm-data`, `sscm-ecs`

## 비용 관리
- 전체 스택 가동 시 ~$47/월 (~$1.5/일)
- 사용 안 할 때: sscm-ecs 스택 삭제 (Fargate 과금 중지)
- 발표 끝나면: 3개 스택 전부 삭제

## Blockers
- (none)

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
- Both on `develop` branch
