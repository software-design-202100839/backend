# SSCM-53: ECS Fargate 클러스터 + Task Definition 작성

- Assignee: 이데브 (DevOps Lee)
- Sprint: 3
- Status: todo
- Created: 2026-03-28
- Commit date: 2026-03-29
- Epic: SSCM-10 (배포/운영)

## Requirement
Sprint 2에서 만든 Docker 이미지(backend 517MB, frontend 94MB)를 실행할 서버리스 환경 필요.

## Why Now?
> Docker 이미지가 ECR에 올라갈 준비가 됐다. 이 이미지를 실행할 컨테이너 환경이 필요하다.

## Design
### ECS Fargate 클러스터
- 클러스터명: `sscm-cluster`
- VPC: 기본 VPC 또는 전용 VPC (서브넷 2개 이상, AZ 분산)

### Task Definition
| 서비스 | CPU | Memory | Port | Image |
|--------|-----|--------|------|-------|
| backend | 512 | 1024MB | 8080 | ECR backend:latest |
| frontend | 256 | 512MB | 80 | ECR frontend:latest |

### 환경변수
- DB, Redis, JWT 시크릿 → Parameter Store에서 주입 (SSCM-54)
- `SPRING_PROFILES_ACTIVE=prod`

## Subtasks
- [ ] ECS 클러스터 생성
- [ ] Backend Task Definition 작성
- [ ] Frontend Task Definition 작성
- [ ] ECS 서비스 생성 (desired count: 1)
- [ ] Security Group 설정 (8080, 80)
- [ ] CloudWatch Logs 설정

## Acceptance Criteria
- [ ] ECS 클러스터 Active 상태
- [ ] Task Definition 등록 완료
- [ ] 서비스가 RUNNING 상태 (이미지 pull 가능 확인)
