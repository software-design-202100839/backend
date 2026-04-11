# Sprint 5: 기능점검 (1차) — 시험기간 cadence 유지

- **기간:** 2026-04-11 ~ 2026-04-18
- **Jira Sprint ID:** 200
- **목표:** 시험기간으로 신규 작업 불가. develop 브랜치 회귀 점검 1건만으로 sprint cadence 유지.

## 왜 이렇게 가는가

원 로드맵(`docs/sprint/roadmap.md`)은 W13~14를 "발표 준비만 집중, 개발 작업 없음"으로 설계했음. 그러나 실제 W13~14는 시험기간과 겹쳐 발표 자료 준비조차 불가. 빈 sprint 2개를 두는 대신, 단일 회귀 점검 이슈로 sprint cadence를 유지하고 발표 준비는 Sprint 7(W15)로 이월.

**대안 비교:**

| 옵션 | 장점 | 단점 |
|------|------|------|
| Sprint 5/6 안 열기 | 단순 | 2주간 sprint cadence gap → "운영 중단"으로 보일 위험 |
| 빈 Sprint 5/6 열기 | cadence 유지 | 작업 0건 → 정직하지 않음 |
| **회귀 점검 1건 sprint 5/6** | cadence 유지 + 정직 | 작업 자체는 매우 작음 (의도된 대로) |
| Sprint 5/6 합쳐서 2주짜리 | 작업량 매칭 | 1주 고정 원칙 위반(감점) |

→ 회귀 점검 1건 sprint 채택.

## 이슈 목록

| Jira | 담당 | 제목 | 상태 |
|------|------|------|------|
| SSCM-65 | 이백엔드 | [기능점검 1차] develop 빌드/테스트 회귀 통과 확인 | 진행 중 |

## 종료 기준
- [ ] `./gradlew clean test` → BUILD SUCCESSFUL, 0 fail
- [ ] 회귀 발생 시 별도 hotfix 이슈 생성
- [ ] Sprint close + 다음 sprint(Sprint 6) 자동 active 전환

## 작업 기록

### 2026-04-11 (sprint 시작) — baseline
- `./gradlew clean test` → BUILD SUCCESSFUL
- 결과: 111 tests, 110 pass, 0 fail, 0 error, 1 skip
- skip 1건은 `SscmApplicationTests` (DB env 변수 없으면 정상 스킵)
- Sprint 4 종료 시점과 동일. 회귀 없음.

### 2026-04-18 (sprint close 예정)
- 사용자가 토요일에 "닫아줘" 트리거 → 회귀 점검 1회 더 + close
