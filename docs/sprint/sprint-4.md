# Sprint 4: 운영 + 모니터링 + 문서화

- **기간:** 2026-04-05 ~ 2026-04-11
- **목표:** 프로덕션 모니터링 체계 구축 + 부하 테스트 + 운영 절차 문서화

## 이슈 목록

### 완료
| Jira | 담당 | 제목 | 완료일 | PR |
|------|------|------|--------|-----|
| - | 이데브 | AWS 인프라 전체 재구축 (4개 스택) | 4/9 | - |
| SSCM-59 | 이데브 | Prometheus + Grafana 모니터링 | 4/9 | #14 |
| SSCM-60 | 이데브 | Grafana Alerting (PagerDuty 대체) | 4/9 | #15 |
| SSCM-61 | 이데브/이백엔드 | k6 부하 테스트 + Redis @Cacheable 판단 | 4/9 | #16 |
| SSCM-62 | 이데브 | 백업/복구 절차 | 4/9 | #17 |
| SSCM-63 | 이큐에이 | SonarCloud 연동 + CI 파이프라인 | 4/9 | #18 |
| SSCM-64 | 이큐에이 | 전체 문서 정리 (스프린트 + 아키텍처) | 4/9~11 | #19 |
| SSCM-58 | 이백엔드 | @Version 낙관적 락 + @Retryable 이메일 재시도 | 4/11 | #20 |

### 스킵/변경
| 원래 계획 | 판단 | 근거 |
|-----------|------|------|
| Dynatrace APM (15일 체험) | **스킵** | Prometheus+Grafana와 기능 중복. 논리적 근거 없는 도구 추가는 부적절 |
| PagerDuty 인시던트 관리 | **Grafana Alerting으로 대체** | 별도 SaaS 도입 불필요. 이미 구축된 Grafana 내장 Alerting으로 충분 |
| Redis @Cacheable | **도입 불필요 판단** | 조회 API 이미 충분히 빠름 (수백ms). 캐시 무효화 복잡도 대비 이점 미미 |

### 후순위 (없음)
SSCM-58은 4/11 마지막 날 구현 완료. Sprint 4 종료 시점 미완료 작업 없음.

## 종료 기준 달성 여부
- [x] Prometheus + Grafana 대시보드에서 JVM/HTTP/DB 메트릭 실시간 확인
- [x] Grafana Alerting: HTTP 5xx, Heap 90%, HikariCP 고갈 시 이메일 알림
- [x] k6 부하 테스트 실행 + 병목 분석 보고서 작성
- [x] RDS 자동 백업 7일 활성화 + 복구 절차 문서화
- [x] SonarCloud CI 파이프라인 동작 (develop push/PR 시 자동 분석)
- [x] 전체 문서 정리 (architecture.md 다이어그램 정합성까지 포함)
- [x] @Version 낙관적 락 + @Retryable 이메일 재시도 (Sprint 2→3→4 이월 마무리)

## 주요 성과

### 1. 모니터링 체계 구축
- Prometheus + Grafana를 ECS Fargate 단일 Task로 배포 (비용 ~$0.33/일)
- SSCM Overview 대시보드 10개 패널 (Uptime, CPU, Heap, HTTP, HikariCP, GC)
- Grafana Alerting 3개 규칙 + Gmail SMTP 이메일 알림

### 2. 부하 테스트 + 근거 있는 판단
- k6로 50 VU 부하 테스트 실행 → 로그인 API 병목 발견 (bcrypt + 512 CPU)
- 실 사용 패턴 분석: 동시 50명 로그인은 비현실적 → 현 구성 유지 판단
- Redis @Cacheable: 조회 API 이미 빠름 → 도입 불필요 판단
- "측정 → 분석 → 판단" 프로세스 실증

### 3. 운영 절차
- RDS 자동 백업 7일 보존 활성화
- 수동 스냅샷, PITR, 복구 절차 문서화
- Redis 백업 불필요 판단 (JWT 토큰 캐시 전용)

### 4. 코드 품질
- SonarCloud CI 파이프라인 구축 (테스트 + JaCoCo + SonarCloud 분석)
- develop push/PR 시 자동 실행

## 설계 판단 기록

### "왜 Dynatrace를 안 하는가?"
Prometheus+Grafana로 JVM 메트릭, HTTP 요청, DB 커넥션 풀 모니터링이 충분히 커버됨. Dynatrace는 분산 트레이싱과 AI 이상 탐지가 강점이나, 모놀리스 구조인 SSCM에서는 해당 기능이 불필요. 기능이 중복되는 도구를 "써봤다"는 이유로 추가하는 것은 설계적 근거가 없음.

### "왜 PagerDuty 대신 Grafana Alerting인가?"
PagerDuty는 인시던트 에스컬레이션과 온콜 로테이션이 강점이나, 1인 운영 학교 프로젝트에서는 과잉. Grafana에 내장된 Alerting으로 알림 규칙 + 이메일 발송이 충분히 가능. 별도 SaaS 계정/연동 없이 기존 인프라 활용.

### "왜 Redis @Cacheable을 안 하는가?"
k6 부하 테스트 결과, 병목은 로그인(bcrypt)이지 조회 API가 아님. 학생 목록, 과목 목록 등 조회 API는 수백ms 이내로 충분히 빠름. 캐시를 도입하면 캐시 무효화 전략(TTL, 이벤트 기반 등)의 복잡도가 추가되는데, 현 규모에서 얻는 성능 이점이 미미. premature optimization 회피.

### "왜 SSCM-58을 마지막 날 다시 살렸는가?"
4/9 시점에선 "현 규모 동시 충돌 가능성 낮음"을 근거로 보류했으나, Sprint 4 마지막 날(4/11) 다른 작업이 모두 끝나고 시간 여유가 생김. 구현 자체가 단순(엔티티 4개 + Flyway 1개 + 핸들러 1개 + 빈 1개)했고, "Sprint 2부터 이월된 이슈를 끝까지 처리"가 프로세스 성실성의 근거가 됨. 다만 자동 재시도 적용 범위는 한정적: `@Retryable`은 사용 빈도 낮은 알림 발송에만, 낙관적 락 충돌은 사용자 안내(새로고침)로 처리해 데이터 정합성을 우선했다.

## 회고

### Keep
- **"측정 → 분석 → 판단" 프로세스가 효과적이었다.** k6 → Redis 캐시 보류, Prometheus 메트릭 → Dynatrace 스킵 모두 측정 결과를 근거로 도구 도입을 거절. 도구를 "써봤다"는 이유로 추가하지 않는 원칙이 정착됨.
- **이월 이슈 정직한 추적.** SSCM-58은 Sprint 2→3→4까지 이월 이력을 태스크 문서에 그대로 남기고, 마지막 날 구현으로 종결. "보류"와 "포기"를 구분해 기록.

### Problem
- **status.md vs Jira 동기화 지연.** SSCM-64가 4/9에 Jira에선 완료 처리됐는데 status.md는 4/11까지 stale. 양방향 동기화 책임자가 없어 발생.
- **architecture.md 다이어그램과 본문 불일치.** 4/9에 "SSCM-64 완료" 처리됐으나 다이어그램에 PagerDuty/Dynatrace가 남아 있었음. 텍스트 수정과 다이어그램 수정이 분리되어 누락 발생.

### Try
- 다음 스프린트(발표 주)부터: 세션 종료 시 Jira/status.md 양방향 점검을 세션 프로토콜에 포함.
- 다이어그램이 있는 문서 수정 시: "다이어그램과 본문이 같은 결론을 말하는가?"를 자체 점검 항목으로 추가.
