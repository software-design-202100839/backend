# SSCM 인프라 (CloudFormation)

## 스택 구성

| 파일 | Jira | 설명 | 배포 순서 |
|------|------|------|-----------|
| `cfn-alb.yml` | SSCM-54 | ALB, Target Group, Listener Rule, Parameter Store | 1st |
| `cfn-ecs-cluster.yml` | SSCM-53 | ECS Cluster, Task Definition, Service, SG, IAM | 2nd |

> **배포 순서 중요**: cfn-alb 스택이 먼저 배포되어야 cfn-ecs-cluster에서 Cross-Stack Reference(ALB SG, Target Group ARN)를 사용할 수 있다.

## 사전 조건

1. AWS CLI v2 설치 + `aws configure` 완료
2. ECR 리포지토리 생성 완료 (`sscm-backend`, `sscm-frontend`)
3. Docker 이미지 ECR에 push 완료

## 배포 방법

### Step 1: ALB + Parameter Store 스택

```bash
aws cloudformation create-stack \
  --stack-name sscm-alb \
  --template-body file://infra/cfn-alb.yml \
  --parameters \
    ParameterKey=VpcId,ParameterValue=vpc-0abc123 \
    ParameterKey=SubnetIds,ParameterValue="subnet-aaa,subnet-bbb" \
    ParameterKey=DbUrl,ParameterValue="jdbc:postgresql://host:5432/sscm" \
    ParameterKey=DbUsername,ParameterValue=sscm \
    ParameterKey=DbPassword,ParameterValue=YOUR_PASSWORD \
    ParameterKey=JwtSecret,ParameterValue=YOUR_JWT_SECRET \
    ParameterKey=RedisHost,ParameterValue=redis-host \
    ParameterKey=EncryptionKey,ParameterValue=YOUR_AES_KEY

# 스택 완료 대기
aws cloudformation wait stack-create-complete --stack-name sscm-alb
```

### Step 2: ECS 클러스터 스택

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

# 스택 완료 대기
aws cloudformation wait stack-create-complete --stack-name sscm-ecs
```

### Step 3: 배포 확인

```bash
# ALB DNS 확인
aws cloudformation describe-stacks --stack-name sscm-alb \
  --query 'Stacks[0].Outputs[?OutputKey==`AlbDnsName`].OutputValue' --output text

# ECS 서비스 상태
aws ecs describe-services --cluster sscm-cluster \
  --services sscm-backend sscm-frontend \
  --query 'services[].{name:serviceName,status:status,running:runningCount}'
```

## Cross-Stack Reference 구조

```
cfn-alb.yml (sscm-alb 스택)
  ├── Export: sscm-alb-sg-id        → cfn-ecs-cluster의 SG 인바운드 소스
  ├── Export: sscm-backend-tg-arn   → cfn-ecs-cluster의 Backend Service LoadBalancers
  └── Export: sscm-frontend-tg-arn  → cfn-ecs-cluster의 Frontend Service LoadBalancers
```

## Parameter Store 파라미터

| 경로 | 용도 | 주입 대상 |
|------|------|-----------|
| `/sscm/prod/db-url` | PostgreSQL JDBC URL | `DB_URL` |
| `/sscm/prod/db-username` | DB 사용자명 | `DB_USERNAME` |
| `/sscm/prod/db-password` | DB 비밀번호 | `DB_PASSWORD` |
| `/sscm/prod/jwt-secret` | JWT 서명 키 | `JWT_SECRET` |
| `/sscm/prod/redis-host` | Redis 엔드포인트 | `REDIS_HOST` |
| `/sscm/prod/encryption-key` | AES-256-GCM 키 | `ENCRYPTION_KEY` |

> 배포 후 AWS 콘솔 > Systems Manager > Parameter Store에서 실제 값으로 교체할 것.

## ALB 라우팅 규칙

| 우선순위 | 경로 | 대상 |
|----------|------|------|
| 10 | `/api/*` | Backend (8080) |
| 20 | `/ws/*` | Backend (8080) — WebSocket |
| 30 | `/actuator/*` | Backend (8080) — Health/Metrics |
| default | `/*` | Frontend (80) |

## 스택 삭제 (역순)

```bash
aws cloudformation delete-stack --stack-name sscm-ecs
aws cloudformation wait stack-delete-complete --stack-name sscm-ecs
aws cloudformation delete-stack --stack-name sscm-alb
```
