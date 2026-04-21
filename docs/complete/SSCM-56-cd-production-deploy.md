# SSCM-56: GitHub Actions CD 완성 (ECR→ECS) + 프로덕션 배포

- Assignee: 이데브 (DevOps Lee)
- Sprint: 3
- Status: done
- Created: 2026-03-28
- Completed: 2026-04-03
- Epic: SSCM-10 (배포/운영)

## Requirement
Sprint 2에서 만든 CD 워크플로우(Docker build→ECR push)에 ECS 배포 단계 추가.

## 완료 내용

### CD 워크플로우 (Backend PR #5, Frontend PR #3)
```
develop push → Docker build → ECR push → ECS update-service → wait stable → verify task
```

### AWS 인프라 구축
- CloudFormation 스택 3개: sscm-alb, sscm-data, sscm-ecs
- ALB: `sscm-alb-162195414.ap-northeast-2.elb.amazonaws.com`
- RDS PostgreSQL 16 (db.t3.micro)
- ElastiCache Redis (cache.t3.micro)
- ECS Fargate: backend (512 CPU, 1GB) + frontend (256 CPU, 512MB)

### 추가 수정 (배포 과정에서 발견)
- actuator 헬스체크 인증 제외 (PR #8)
- 학생 회원가입 admissionYear NPE 수정 (PR #7)
- API baseURL 환경변수 분리 (Frontend PR #4)
- HealthCheckGracePeriodSeconds: 120 설정

## Subtasks
- [x] GitHub Secrets 등록 (AWS credentials)
- [x] Backend CD workflow에 ECS deploy 단계 추가
- [x] Frontend CD workflow에 ECS deploy 단계 추가
- [x] 배포 후 health check 검증 단계
- [x] develop push → 자동 배포 E2E 테스트
- [x] 프로덕션 URL로 전 기능 동작 확인

## Acceptance Criteria
- [x] develop push 시 자동으로 ECR push + ECS 배포
- [x] 배포 후 ALB URL로 앱 접속 가능
- [x] 로그인 → 성적 입력 → 조회 시나리오 동작
- [x] Playwright E2E 프로덕션 7/7 통과

## 트러블슈팅
- 10건 기록: `docs/troubleshooting/SSCM-56-production-deploy.md`
