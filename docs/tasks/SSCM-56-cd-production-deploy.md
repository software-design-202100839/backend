# SSCM-56: GitHub Actions CD 완성 (ECR→ECS) + 프로덕션 배포

- Assignee: 이데브 (DevOps Lee)
- Sprint: 3
- Status: todo
- Created: 2026-03-28
- Commit date: 2026-04-01
- Epic: SSCM-10 (배포/운영)

## Requirement
Sprint 2에서 만든 CD 워크플로우(Docker build→ECR push)에 ECS 배포 단계 추가.

## Why Now?
> ECS 인프라(SSCM-53)와 ALB(SSCM-54)가 준비되었다. 이제 코드 push → 자동 배포 파이프라인을 완성한다.

## Design
### CD 워크플로우 확장
```
develop push → Docker build → ECR push → ECS service update → health check
```

### GitHub Secrets 설정
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`, `ECR_REGISTRY`
- `ECS_CLUSTER`, `ECS_SERVICE_BACKEND`, `ECS_SERVICE_FRONTEND`

### 배포 전략
- Rolling update (ECS 기본)
- minimumHealthyPercent: 100, maximumPercent: 200
- Health check grace period: 60s

## Subtasks
- [ ] GitHub Secrets 등록 (AWS credentials)
- [ ] Backend CD workflow에 ECS deploy 단계 추가
- [ ] Frontend CD workflow에 ECS deploy 단계 추가
- [ ] 배포 후 health check 검증 단계
- [ ] develop push → 자동 배포 E2E 테스트
- [ ] 프로덕션 URL로 전 기능 동작 확인

## Acceptance Criteria
- [ ] develop push 시 자동으로 ECR push + ECS 배포
- [ ] 배포 후 ALB URL로 앱 접속 가능
- [ ] 로그인 → 성적 입력 → 조회 시나리오 동작
