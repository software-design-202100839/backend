# SSCM-53: ECS Fargate 클러스터 + Task Definition 작성

- Assignee: 이데브 (DevOps Lee)
- Sprint: 3
- Status: done
- Created: 2026-03-28
- Completed: 2026-03-29
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
- [x] ECS 클러스터 생성
- [x] Backend Task Definition 작성
- [x] Frontend Task Definition 작성
- [x] ECS 서비스 생성 (desired count: 1)
- [x] Security Group 설정 (8080, 80)
- [x] CloudWatch Logs 설정

## Acceptance Criteria
- [x] CloudFormation 템플릿 cfn-lint 검증 통과
- [x] Task Definition 등록 완료 (IaC로 정의)
- [ ] 실제 AWS 배포 후 서비스 RUNNING 확인 (AWS CLI 설정 후)

## 구현 방식
- IaC (CloudFormation) 선택: AWS 단일 클라우드, 추가 도구 불필요
- `infra/cfn-ecs-cluster.yml` — 전체 리소스 정의
- `infra/README.md` — 배포 가이드
- PR: backend#2 → develop 머지 완료
