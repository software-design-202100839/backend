# Sprint 2: 전 기능 완성 + 안정화 + 컨테이너화

- **기간:** 2026-03-15 ~ 2026-03-28
- **목표:** 전 기능 동작 + E2E 영상 증거 + Docker 이미지 빌드 + CD 파이프라인

## 이슈 목록

| Jira | 담당 | 제목 | 결과 |
|------|------|------|------|
| - | 이백엔드 | 피드백 API | 완료 |
| - | 이백엔드 | 상담 내역 API | 완료 |
| - | 이백엔드 | 알림 (WebSocket + 이메일) | 완료 |
| - | 이프론트 | 피드백 UI | 완료 |
| - | 이프론트 | 상담 UI | 완료 |
| - | 이프론트 | 알림 센터 UI | 완료 |
| - | 이프론트 | 학부모 전용 조회 뷰 | 완료 |
| SSCM-51 | 이데브 | Dockerfile + ECR + CD | 완료 |
| SSCM-52 | 이큐에이 | Playwright E2E + 영상 녹화 | 완료 |

## 종료 기준 달성 여부
- [x] 전 기능 동작 (인증, 성적, 학생부, 피드백, 상담, 알림, 학부모 뷰)
- [x] Playwright E2E 3개 시나리오 7개 테스트 통과 + 영상 녹화 파일 확보
- [x] JUnit 94개 테스트 통과 (0 fail, 1 skip) + JaCoCo 리포트 (Inst 36%, Branch 25%)
- [x] Docker 이미지 빌드 성공 (backend 517MB + frontend 94MB)
- [x] GitHub Actions CD → ECR 푸시 파이프라인 동작
- [ ] ~~@Version + @Retryable~~ → Sprint 3 이월 (SSCM-58)

## 주요 성과

### 기능 완성
- 6개 도메인(인증, 성적, 학생부, 피드백, 상담, 알림) 전체 CRUD 완성
- 3-Role 시스템(교사/학생/학부모) 권한 분리 완료
- WebSocket(STOMP) 실시간 알림 동작

### 품질 검증
- JUnit 94개 테스트 전체 통과
- Playwright E2E 7개 시나리오 영상 녹화 확보 → 동작 증명

### 컨테이너화
- 멀티스테이지 Dockerfile (빌드+런타임 분리)
- GitHub Actions CD: develop push → ECR push 자동화

## 미완료 → Sprint 3 이월
| Jira | 제목 | 사유 |
|------|------|------|
| SSCM-58 | @Version + @Retryable | 기능 완결 + 동작 증명에 집중, 시간 부족 |
