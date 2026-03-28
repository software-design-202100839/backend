# SSCM-51: Dockerfile + ECR + GitHub Actions CD

- Assignee: 이데브 (DevOps Lee)
- Sprint: 2
- Status: todo
- Created: 2026-03-27
- Commit date: 2026-03-27

## Requirement
앱이 완성되었으므로 컨테이너로 패키징할 이유가 생김 (`docs/sprint/roadmap.md` Phase 3 참조)

## Design
레포가 분리되어 있으므로 각각 독립적으로:
- Backend Dockerfile: Gradle 빌드 + JDK 17 런타임
- Frontend Dockerfile: npm build + Nginx 서빙
- GitHub Actions CD: 각 레포에 `.github/workflows/cd.yml`
- ECR: backend/frontend 각각 별도 레포지토리

### 배포 대상
- **ECS Fargate** (EKS 아님) — ADR-002 참조
- CD 워크플로우: Docker build → ECR push → `aws ecs update-service` (Argo CD 아님)
- 시크릿: AWS Parameter Store에서 ECS Task Definition이 직접 참조

### Tech choices (tradeoff 비교 필요)
- [ ] Dockerfile 빌드 전략 (multi-stage vs single-stage)
- [ ] Base image 선택 (Alpine vs Distroless vs Debian slim)
- [ ] CD trigger 전략 (develop push vs tag-based)

## Subtasks
- [ ] Backend Dockerfile 작성
- [ ] Frontend Dockerfile 작성
- [ ] Backend CD workflow (`.github/workflows/cd.yml`)
- [ ] Frontend CD workflow (`.github/workflows/cd.yml`)
- [ ] ECR 레포 설정 (문서화)
- [ ] 빌드 테스트 확인

## Acceptance Criteria
- [ ] `docker build` 성공 (backend + frontend)
- [ ] CD workflow가 develop push 시 트리거
- [ ] ECR에 이미지 push 가능한 구조

## Notes
- Sprint 3에서 ECS Fargate 서비스 생성 + ALB 설정 + Parameter Store 연동 진행
- 이 태스크는 이미지 빌드/푸시까지만 범위
