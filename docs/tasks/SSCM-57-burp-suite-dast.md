# SSCM-57: Burp Suite DAST 보안 테스트

- Assignee: 이큐에이 (QA Lee)
- Sprint: 3
- Status: todo
- Created: 2026-03-28
- Commit date: 2026-04-02
- Epic: SSCM-9 (보안 강화)

## Requirement
실제 서버에서 구동 중인 앱 대상 DAST(Dynamic Application Security Testing).

## Why Now?
> 프로덕션 배포가 완료되었다. 실제 동작하는 앱에 대해 외부 공격자 관점의 보안 테스트를 수행해야 한다.

## Design
### 테스트 범위
- OWASP Top 10 취약점 스캔
- 인증 API (SQL Injection, Brute Force)
- XSS (입력 필드 전체)
- CSRF
- 권한 상승 (학생→교사 API 접근)
- JWT 토큰 조작

### 도구
- Burp Suite Community Edition (무료)
- Proxy intercept + Scanner
- 결과: HTML 리포트 생성

## Subtasks
- [ ] Burp Suite 설치 + 프로젝트 설정
- [ ] 타겟 URL 설정 (ALB DNS)
- [ ] 인증 후 Crawler 실행
- [ ] Active Scan 실행
- [ ] 발견된 취약점 분류 (Critical/High/Medium/Low)
- [ ] 리포트 생성 + docs/에 저장
- [ ] Critical/High 취약점 수정 (있을 경우)

## Acceptance Criteria
- [ ] DAST 리포트 생성 완료
- [ ] Critical 취약점 0건
- [ ] High 취약점 수정 완료 (있을 경우)
