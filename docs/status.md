# SSCM Project Status

> Last updated: 2026-04-11 by session 2026-04-11-01

## Current Sprint
- **Sprint 5 진행 중** (Jira Sprint ID: 200, 2026-04-11 ~ 2026-04-18)
- 목표: 시험기간 cadence 유지. 회귀 점검 1건만(SSCM-65).
- 다음: Sprint 6(4/18~4/25) 동일 패턴, Sprint 7(4/25~5/2) 실제 발표 준비.
- 매주 토요일 사용자 트리거로 sprint close (한 줄 메시지면 충분).
- 회고: `docs/sprint/sprint-5.md`

## 직전 Sprint
- **Sprint 4 종료** (Jira Sprint ID: 167, 2026-04-11 close)
- 회고: `docs/sprint/sprint-4.md`

## Sprint 3 결과 (2026-03-29 ~ 2026-04-04) — 완료
- 전체 종료 기준 달성
- 회고: `docs/sprint/sprint-3.md`

## Sprint 4 결과 (2026-04-05 ~ 2026-04-11) — 완료

### 완료
| Jira | Assignee | Summary | Commit Date |
|---------|----------|---------|-------------|
| SSCM-59 | 이데브 | Prometheus + Grafana 모니터링 | 4/9 |
| SSCM-60 | 이데브 | Grafana Alerting (PagerDuty 대체) | 4/9 |
| SSCM-61 | 이데브 | k6 부하 테스트 + 결과 분석 | 4/9 |
| SSCM-62 | 이데브 | RDS 백업 활성화 + 복구 절차 문서화 | 4/9 |
| SSCM-63 | 이큐에이 | SonarCloud 연동 + CI 파이프라인 | 4/9 |
| SSCM-64 | 이큐에이 | 전체 문서 정리 (스프린트 + 아키텍처) | 4/9~11 |
| SSCM-58 | 이백엔드 | @Version Optimistic Locking + @Retryable 이메일 재시도 (Sprint 2→3→4 이월, 마지막날 클리어) | 4/11 |

### 스킵/변경 (설계 판단)
| 원래 계획 | 판단 | 근거 |
|-----------|------|------|
| Dynatrace APM | **스킵** | Prometheus+Grafana와 기능 중복. 설계적 근거 없는 도구 추가는 부적절 |
| PagerDuty | **Grafana Alerting 대체** | 1인 운영 규모에서 별도 SaaS 과잉. 내장 Alerting + Gmail SMTP로 충분 |
| Redis @Cacheable | **도입 불필요** | k6 결과: 조회 API 이미 빠름. 캐시 무효화 복잡도 대비 이점 미미 |

### 발견된 개선 사항 (스코프 아웃 — 발표 후 검토)
- 학생부 content JSON 표시 → 카테고리별 렌더링
- 피드백 isVisibleToStudent 기본값 → 학생 공개 로직 점검
- 상담내역 학생 권한 조회 허용
- 보고서 생성 API/UI (요구사항 우선순위 낮음, SSCM-8 에픽 — 4주 압축 스프린트 범위 밖)

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

## Jira 위생 상태 (2026-04-11 정리)
- 미완료 이슈: SSCM-8(보고서 에픽, 스코프 아웃)만 의도적으로 오픈 유지
- 에픽 SSCM-5/6/7/9/10 → 완료 전환 (Sprint 2~4에서 구현 완료한 스토리들 반영)
- 스토리 0개 미완료 (SSCM-58까지 4/11 클리어)

## W13~14 — 발표 준비만 집중

## Repos
- Backend: `software-design-202100839/backend` → `/mnt/c/Users/seung/workspace/sscm-backend`
- Frontend: `software-design-202100839/frontend` → `/mnt/c/Users/seung/workspace/sscm-frontend`
- Both on `develop` branch
