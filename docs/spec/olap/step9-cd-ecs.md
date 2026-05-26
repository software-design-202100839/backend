# Step 9: CD 워크플로우 — ECS 배포로 전환

> 작성일: 2026-05-26
> 목적: EC2 docker-compose 배포 → ECS Fargate 배포로 전환

---

## 변경 전 (EC2)

```
GitHub Actions → Docker build → ECR push → SSH to EC2 → docker compose pull → up
```

문제:
- EC2 인스턴스에 직접 SSH 접속 필요 (보안 우려)
- docker-compose 파일 전송 필요
- 헬스체크가 EC2 IP 기반

---

## 변경 후 (ECS)

```
GitHub Actions → Docker build → ECR push → aws ecs update-service --force-new-deployment
                                          → aws ecs wait services-stable
                                          → ALB 헬스체크
```

장점:
- SSH 키 불필요 (AWS API만 사용)
- Rolling deployment 자동 (zero-downtime)
- ALB DNS 기반 헬스체크 (인스턴스 IP 불필요)
- `aws ecs wait`로 배포 안정화 대기

---

## CD 파이프라인 흐름

```
1. build-and-push (Job 1)
   ├── Checkout
   ├── AWS credentials 설정
   ├── ECR 로그인
   └── Docker build + tag (latest + SHA) + push

2. deploy (Job 2, build-and-push 완료 후)
   ├── AWS credentials 설정
   ├── aws ecs update-service --force-new-deployment
   ├── aws ecs wait services-stable (~2-3분)
   └── ALB DNS로 /actuator/health 헬스체크
```

---

## 실행 방법

- GitHub → Actions → "Backend CD" → "Run workflow" → develop 브랜치 선택 → 실행
- 수동 실행만 지원 (`workflow_dispatch`)

---

## 필요한 GitHub Secrets

| Secret | 용도 |
|--------|------|
| `AWS_ACCESS_KEY_ID` | AWS API 인증 |
| `AWS_SECRET_ACCESS_KEY` | AWS API 인증 |

기존 EC2 관련 시크릿(`EC2_HOST`, `EC2_SSH_KEY`)은 더 이상 불필요.
