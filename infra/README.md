# SSCM 인프라 (CloudFormation)

## 스택 구성

| 파일 | Jira | 설명 |
|------|------|------|
| `cfn-ecs-cluster.yml` | SSCM-53 | ECS Cluster, Task Definition, Service, SG, IAM |
| `cfn-alb.yml` | SSCM-54 | ALB, 경로 라우팅, Parameter Store (예정) |

## 사전 조건

1. AWS CLI v2 설치 + `aws configure` 완료
2. ECR 리포지토리 생성 완료 (`sscm-backend`, `sscm-frontend`)
3. Docker 이미지 ECR에 push 완료

## 배포 방법

### 1. 파라미터 확인

| 파라미터 | 설명 | 예시 |
|----------|------|------|
| `VpcId` | 사용할 VPC ID | `vpc-0abc123...` |
| `SubnetIds` | 퍼블릭 서브넷 2개 이상 | `subnet-aaa,subnet-bbb` |
| `BackendImage` | Backend ECR URI | `123456789.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest` |
| `FrontendImage` | Frontend ECR URI | `123456789.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-frontend:latest` |

### 2. 스택 생성

```bash
aws cloudformation create-stack \
  --stack-name sscm-ecs \
  --template-body file://infra/cfn-ecs-cluster.yml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameters \
    ParameterKey=VpcId,ParameterValue=vpc-0abc123 \
    ParameterKey=SubnetIds,ParameterValue="subnet-aaa,subnet-bbb" \
    ParameterKey=BackendImage,ParameterValue=123456789.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest \
    ParameterKey=FrontendImage,ParameterValue=123456789.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-frontend:latest
```

### 3. 배포 확인

```bash
# 스택 상태 확인
aws cloudformation describe-stacks --stack-name sscm-ecs --query 'Stacks[0].StackStatus'

# ECS 서비스 상태 확인
aws ecs list-services --cluster sscm-cluster
aws ecs describe-services --cluster sscm-cluster --services sscm-backend sscm-frontend
```

### 4. 스택 업데이트 (변경 시)

```bash
aws cloudformation update-stack \
  --stack-name sscm-ecs \
  --template-body file://infra/cfn-ecs-cluster.yml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameters \
    ParameterKey=VpcId,UsePreviousValue=true \
    ParameterKey=SubnetIds,UsePreviousValue=true \
    ParameterKey=BackendImage,ParameterValue=NEW_IMAGE_URI \
    ParameterKey=FrontendImage,UsePreviousValue=true
```

### 5. 스택 삭제

```bash
aws cloudformation delete-stack --stack-name sscm-ecs
```

## SSCM-54에서 추가될 내용

- ALB + Target Group + Listener Rule (`/api/*` → backend, `/*` → frontend)
- Parameter Store 시크릿 (DB URL, Redis, JWT Secret)
- Task Definition에 `secrets` 블록 추가 (환경변수 → valueFrom)
- Security Group 인바운드를 ALB SG로 제한 (0.0.0.0/0 제거)
