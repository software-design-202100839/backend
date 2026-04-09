# SSCM Project Status

> Last updated: 2026-04-09 by session 2026-04-09-01

## Current Sprint
- Sprint: 4 (Jira ID: 미생성)
- Period: 2026-04-05 ~ 2026-04-11
- Focus: 운영 + 모니터링 + 문서화

## Sprint 3 결과 (2026-03-29 ~ 2026-04-04) — 완료
- 전체 종료 기준 달성
- 회고: `docs/sprint/sprint-3.md`

## Active Tasks (Sprint 4)

### 반드시 (Must) — 운영 + 모니터링
| Jira | Assignee | Summary | Commit Date | Status |
|------|----------|---------|-------------|--------|
| - | 이데브 | Prometheus + Grafana 설정 | 4/9 | **done** |
| - | 이데브 | ~~Dynatrace APM 연동~~ | - | **스킵** (Prometheus+Grafana와 기능 중복, 설계적 근거 없음) |
| - | 이데브 | ~~PagerDuty~~ → Grafana Alerting으로 대체 | 4/9 | **done** |
| - | 이데브 | k6 부하 테스트 | 4/9 | **done** |
| - | 이백엔드 | k6 결과 분석 → Redis @Cacheable 도입 판단 | 4/9 | **done** (불필요 판단, k6/REPORT.md 참조) |
| - | 이데브 | 백업/복구 절차 | 4/9 | **done** |
| - | 이큐에이 | SonarCloud 연동 + CI 파이프라인 | 4/9 | **done** |
| - | 전원 | 전체 문서 정리 | 4/10~11 | todo |

### 이월 (Sprint 3 → 4) — 우선순위 하향
| Jira | Assignee | Summary | Status | 비고 |
|------|----------|---------|--------|------|
| SSCM-58 | 이백엔드 | @Version + @Retryable | todo | 백업/복구, SonarCloud, 문서 정리가 더 급하여 후순위 |

### 발견된 개선 사항 (시간 여유 시)
- 학생부 content JSON 표시 → 카테고리별 렌더링
- 피드백 isVisibleToStudent 기본값 → 학생 공개 로직 점검
- 상담내역 학생 권한 조회 허용
- 보고서 생성 API/UI

## AWS 인프라 현황
- ALB: `sscm-alb-1703346258.ap-northeast-2.elb.amazonaws.com`
- ECS 클러스터: `sscm-cluster` (backend + frontend 서비스)
- RDS: `sscm-db.cvsqwimyifjw.ap-northeast-2.rds.amazonaws.com` (PostgreSQL 16)
- Redis: `sscm-redis.xvtaov.0001.apn2.cache.amazonaws.com`
- ECR: `sscm-backend`, `sscm-frontend`
- CloudFormation 스택: `sscm-alb`, `sscm-data`, `sscm-ecs`
- **현재 상태:** 전체 스택 삭제됨 (2026-04-09 비용 절감). 다음 작업 시 재구축 필요.
- Grafana 접속: 스택 재구축 후 ALB DNS/grafana/ (admin/sscm2026!)

## 비용 관리
- 전체 스택 가동 시 ~$47/월 (~$1.5/일)
- 사용 안 할 때: sscm-ecs 스택 삭제 (Fargate 과금 중지)
- 발표 끝나면: 3개 스택 전부 삭제

## Blockers
- (none)

## W13~14 — 발표 준비만 집중

## Repos
- Backend: `software-design-202100839/backend` → `/mnt/c/Users/seung/workspace/sscm-backend`
- Frontend: `software-design-202100839/frontend` → `/mnt/c/Users/seung/workspace/sscm-frontend`
- Both on `develop` branch
