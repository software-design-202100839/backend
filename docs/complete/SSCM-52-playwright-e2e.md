# SSCM-52: Playwright E2E 테스트 + 영상 녹화 (동작 증명)

- Assignee: 이큐에이 (QA Lee)
- Sprint: 2
- Status: todo
- Created: 2026-03-27
- Commit date: 2026-03-28

## Requirement
전 기능이 동작하므로 사용자 시나리오 전체 테스트 가능. 영상 녹화를 켜서 **동작 증명**을 자동으로 확보한다.

## Design
- Frontend 레포에 Playwright 설정
- **영상 녹화 + 스크린샷 + 트레이스** 모두 활성화
- 주요 사용자 시나리오 E2E 테스트

### Playwright 설정 핵심
```typescript
// playwright.config.ts
export default defineConfig({
  use: {
    video: 'on',              // 모든 테스트 .webm 영상 녹화
    screenshot: 'on',         // 매 테스트 스크린샷 자동 저장
    trace: 'on',              // 상세 실행 트레이스 (네트워크, DOM 등)
    baseURL: 'http://localhost:5173',
  },
  outputDir: 'test-results/',  // 영상/스크린샷 저장 경로
});
```

### 테스트 시나리오 (요구사항 명세서 기반)
1. **시나리오 1: 교사 A가 성적 입력 + 긍정 피드백 작성**
   - 교사 로그인 → 성적 입력 → 자동 계산 확인 → 피드백 작성 → 저장 확인
2. **시나리오 2: 교사 B가 학생 상담 내역 조회 후 후속 상담**
   - 교사 로그인 → 공유된 상담 조회 → 새 상담 등록 → 알림 확인
3. **시나리오 3: 학부모가 자녀 성적/피드백 조회**
   - 학부모 로그인 → 성적 조회 → 피드백 조회 → 레이더 차트 확인

### 동작 증명 산출물
- `test-results/*.webm` — 각 시나리오 전체 영상
- `test-results/*.png` — 핵심 화면 스크린샷
- `test-results/trace.zip` — Playwright Trace Viewer로 열 수 있는 상세 로그
- JUnit XML 리포트 — CI 통합용

### Playwright 선택 근거 (ADR-002 참조)
| 대안 | 기각 사유 |
|------|-----------|
| Cypress | Chromium 기반이라 멀티 브라우저 테스트가 약함 |
| Selenium | 느리고 설정 복잡, 영상 녹화 내장 없음 |
| Playwright | 멀티 브라우저, 영상/스크린샷/트레이스 내장, 빠름 |

## Subtasks
- [ ] Playwright 설치 (`npm init playwright@latest`)
- [ ] playwright.config.ts 설정 (video: 'on', screenshot: 'on', trace: 'on')
- [ ] 시나리오 1: 교사 성적 입력 + 피드백 E2E
- [ ] 시나리오 2: 교사 상담 조회 + 등록 E2E
- [ ] 시나리오 3: 학부모 성적/피드백 조회 E2E
- [ ] CI 통합 (GitHub Actions에서 E2E 실행)
- [ ] 영상/스크린샷 아티팩트 업로드 설정

## Acceptance Criteria
- [ ] `npx playwright test` 성공
- [ ] 3개 시나리오 모두 영상 녹화 파일 생성 확인
- [ ] 스크린샷 자동 저장 확인
- [ ] CI에서 자동 실행 + 아티팩트 다운로드 가능

## Notes
- Playwright 영상 = "이 시스템이 동작한다"의 가장 강력한 증거
- 발표 시 영상을 직접 보여줄 수 있음
