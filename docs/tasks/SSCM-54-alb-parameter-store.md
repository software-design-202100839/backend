# SSCM-54: ALB 경로 기반 라우팅 + Parameter Store 설정

- Assignee: 이데브 (DevOps Lee)
- Sprint: 3
- Status: todo
- Created: 2026-03-28
- Commit date: 2026-03-30
- Epic: SSCM-10 (배포/운영)

## Requirement
외부에서 앱에 접속할 HTTPS 진입점 + 프로덕션 시크릿 안전 관리.

## Why Now?
> ECS 서비스가 돌고 있지만 외부에서 접속할 방법이 없다. ALB가 진입점이 된다. 시크릿은 코드에 하드코딩하면 안 되므로 Parameter Store에서 주입한다.

## Design
### ALB
- ALB → Target Group (backend:8080, frontend:80)
- 경로 라우팅: `/api/*` → backend, `/*` → frontend
- Health check: backend `/api/v1/health`, frontend `/`

### Parameter Store
| 파라미터 | 타입 |
|----------|------|
| /sscm/prod/db-url | SecureString |
| /sscm/prod/db-username | SecureString |
| /sscm/prod/db-password | SecureString |
| /sscm/prod/jwt-secret | SecureString |
| /sscm/prod/redis-host | String |
| /sscm/prod/encryption-key | SecureString |

### Task Definition 연동
- `secrets` 블록에서 Parameter Store ARN 참조
- IAM Role: ecsTaskExecutionRole에 ssm:GetParameters 권한 추가

## Subtasks
- [ ] ALB 생성 + 리스너 규칙
- [ ] Target Group (backend, frontend)
- [ ] Parameter Store 파라미터 등록
- [ ] Task Definition에 secrets 블록 추가
- [ ] IAM 권한 설정
- [ ] Health check 동작 확인

## Acceptance Criteria
- [ ] ALB DNS로 프론트엔드 접속 가능
- [ ] ALB DNS/api/* 로 백엔드 API 호출 가능
- [ ] Parameter Store 값이 ECS 컨테이너에 환경변수로 주입됨
