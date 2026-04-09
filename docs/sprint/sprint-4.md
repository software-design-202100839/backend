# Sprint 4: 운영 + 모니터링 + 문서화

- **기간:** 2026-04-05 ~ 2026-04-11
- **목표:** 프로덕션 모니터링 체계 구축 + 부하 테스트 + 운영 절차 문서화

## 이슈 목록

### 완료
| 담당 | 제목 | 완료일 | PR |
|------|------|--------|-----|
| 이데브 | AWS 인프라 전체 재구축 (4개 스택) | 4/9 | - |
| 이데브 | Prometheus + Grafana 모니터링 | 4/9 | #14 |
| 이데브 | Grafana Alerting (PagerDuty 대체) | 4/9 | #15 |
| 이데브 | k6 부하 테스트 | 4/9 | #16 |
| 이백엔드 | k6 결과 분석 → Redis @Cacheable 판단 | 4/9 | #16 |
| 이데브 | 백업/복구 절차 | 4/9 | #17 |
| 이큐에이 | SonarCloud 연동 + CI 파이프라인 | 4/9 | #18 |

### 스킵/변경
| 원래 계획 | 판단 | 근거 |
|-----------|------|------|
| Dynatrace APM (15일 체험) | **스킵** | Prometheus+Grafana와 기능 중복. 논리적 근거 없는 도구 추가는 부적절 |
| PagerDuty 인시던트 관리 | **Grafana Alerting으로 대체** | 별도 SaaS 도입 불필요. 이미 구축된 Grafana 내장 Alerting으로 충분 |
| Redis @Cacheable | **도입 불필요 판단** | 조회 API 이미 충분히 빠름 (수백ms). 캐시 무효화 복잡도 대비 이점 미미 |

### 후순위
| Jira | 담당 | 제목 | 상태 | 사유 |
|------|------|------|------|------|
| SSCM-58 | 이백엔드 | @Version + @Retryable | 보류 | 현 규모에서 동시 수정 충돌 가능성 낮음, 추가 검토 필요 |

## 종료 기준 달성 여부
- [x] Prometheus + Grafana 대시보드에서 JVM/HTTP/DB 메트릭 실시간 확인
- [x] Grafana Alerting: HTTP 5xx, Heap 90%, HikariCP 고갈 시 이메일 알림
- [x] k6 부하 테스트 실행 + 병목 분석 보고서 작성
- [x] RDS 자동 백업 7일 활성화 + 복구 절차 문서화
- [x] SonarCloud CI 파이프라인 동작 (develop push/PR 시 자동 분석)
- [x] 전체 문서 정리

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
