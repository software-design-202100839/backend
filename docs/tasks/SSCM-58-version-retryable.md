# SSCM-58: @Version Optimistic Locking + @Retryable 이메일 재시도

- Assignee: 이백엔드 (Backend Lee)
- Sprint: 3 (Nice-to-have, Sprint 2에서 이월)
- Status: todo
- Created: 2026-03-28
- Commit date: 2026-04-03
- Epic: SSCM-1 (프로젝트 셋업)

## Requirement
동시 수정 충돌 방지 + 이메일 발송 실패 시 자동 재시도.

## Why Now?
> Sprint 2에서 기능 완결에 집중하느라 이월. 프로덕션 배포 후 실제 동시 접근이 발생할 수 있으므로 Sprint 3에서 처리.

## Design
### @Version (Optimistic Locking)
- 대상 엔티티: Score, Feedback, Counseling, StudentRecord
- Flyway: `ALTER TABLE ADD COLUMN version BIGINT DEFAULT 0`
- `OptimisticLockingFailureException` → 409 Conflict 응답

### @Retryable (이메일 재시도)
- spring-retry 의존성 추가
- `@Retryable(maxAttempts=3, backoff=@Backoff(delay=1000))`
- 대상: EmailService.sendNotification()

## Subtasks
- [ ] spring-retry 의존성 추가
- [ ] 엔티티 4개에 @Version 필드 추가
- [ ] Flyway 마이그레이션 (version 컬럼)
- [ ] GlobalExceptionHandler에 OptimisticLockingFailureException 처리
- [ ] EmailService에 @Retryable 적용
- [ ] @EnableRetry 설정
- [ ] 단위 테스트

## Acceptance Criteria
- [ ] 동시 수정 시 409 Conflict 반환
- [ ] 이메일 실패 시 최대 3회 재시도
- [ ] 기존 테스트 전체 통과
