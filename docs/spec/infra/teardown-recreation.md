# 인프라 내리기 + 재생성 가이드

> 발표 전 비용 절감 후, 발표 당일 재배포를 위한 체크리스트.
> 예상 소요: 내리기 ~20분, 올리기 ~50분 (MSK 30~45분이 병목)

---

## 1. 내리기 (역순 삭제)

```bash
# 1-1. 모니터링 삭제
aws cloudformation delete-stack --stack-name sscm-monitoring --region ap-northeast-2
aws cloudformation wait stack-delete-complete --stack-name sscm-monitoring --region ap-northeast-2

# 1-2. ECS 삭제
aws cloudformation delete-stack --stack-name sscm-ecs --region ap-northeast-2
aws cloudformation wait stack-delete-complete --stack-name sscm-ecs --region ap-northeast-2

# 1-3. CDN 삭제 (S3 비우기 먼저)
aws s3 rm s3://sscm-frontend-static --recursive --region ap-northeast-2
aws cloudformation delete-stack --stack-name sscm-cdn --region ap-northeast-2
aws cloudformation wait stack-delete-complete --stack-name sscm-cdn --region ap-northeast-2

# 1-4. ALB 삭제
aws cloudformation delete-stack --stack-name sscm-alb --region ap-northeast-2
aws cloudformation wait stack-delete-complete --stack-name sscm-alb --region ap-northeast-2

# 1-5. MSK 삭제
aws cloudformation delete-stack --stack-name sscm-msk --region ap-northeast-2
aws cloudformation wait stack-delete-complete --stack-name sscm-msk --region ap-northeast-2

# 1-6. 데이터 삭제 (RDS + Redis — 데이터 영구 삭제됨!)
aws cloudformation delete-stack --stack-name sscm-data --region ap-northeast-2
aws cloudformation wait stack-delete-complete --stack-name sscm-data --region ap-northeast-2

echo "전체 삭제 완료"
```

---

## 2. 올리기 (순서 중요!)

### 필요한 값 (미리 준비)
```
VPC_ID=vpc-0ed7e672a2e831aa2
SUBNET_IDS=subnet-0edb38aacecedaf1b,subnet-0ea1c8178ccfe6e14
VPC_CIDR=172.31.0.0/16
DB_PASSWORD=<기존 비밀번호>
JWT_SECRET=<기존 JWT 시크릿>
ENCRYPTION_KEY=<기존 AES 키>
GEMINI_API_KEY=<Gemini API 키>
GMAIL_APP_PASSWORD=<Gmail 앱 비밀번호>
```

### Step 1: 데이터 (RDS + Redis) — ~15분
```bash
aws cloudformation create-stack \
  --stack-name sscm-data \
  --template-body file://infra/cfn-data.yml \
  --parameters \
    ParameterKey=VpcId,ParameterValue=$VPC_ID \
    ParameterKey=SubnetIds,ParameterValue=\"$SUBNET_IDS\" \
    ParameterKey=VpcCidr,ParameterValue=$VPC_CIDR \
    ParameterKey=DbPassword,ParameterValue=$DB_PASSWORD \
  --region ap-northeast-2

aws cloudformation wait stack-create-complete --stack-name sscm-data --region ap-northeast-2

# RDS/Redis 엔드포인트 메모
RDS_ENDPOINT=$(aws cloudformation describe-stacks --stack-name sscm-data --query 'Stacks[0].Outputs[?OutputKey==`RdsEndpoint`].OutputValue' --output text --region ap-northeast-2)
ANALYTICS_RDS_ENDPOINT=$(aws cloudformation describe-stacks --stack-name sscm-data --query 'Stacks[0].Outputs[?OutputKey==`AnalyticsRdsEndpoint`].OutputValue' --output text --region ap-northeast-2)
REDIS_ENDPOINT=$(aws cloudformation describe-stacks --stack-name sscm-data --query 'Stacks[0].Outputs[?OutputKey==`RedisEndpoint`].OutputValue' --output text --region ap-northeast-2)
echo "RDS: $RDS_ENDPOINT"
echo "Analytics RDS: $ANALYTICS_RDS_ENDPOINT"
echo "Redis: $REDIS_ENDPOINT"
```

### Step 2: MSK (Kafka) — ~30~45분 (가장 오래 걸림)
```bash
aws cloudformation create-stack \
  --stack-name sscm-msk \
  --template-body file://infra/cfn-msk.yml \
  --parameters \
    ParameterKey=VpcId,ParameterValue=$VPC_ID \
    ParameterKey=SubnetIds,ParameterValue=\"$SUBNET_IDS\" \
    ParameterKey=VpcCidr,ParameterValue=$VPC_CIDR \
  --region ap-northeast-2

echo "MSK 생성 중... 30~45분 소요. Step 3과 병렬 진행 가능."
```

### Step 3: ALB + SSM 파라미터 (Step 1 완료 후, Step 2와 병렬 가능) — ~5분
```bash
# MSK 부트스트랩은 나중에 업데이트하므로 임시값 사용
aws cloudformation create-stack \
  --stack-name sscm-alb \
  --template-body file://infra/cfn-alb.yml \
  --parameters \
    ParameterKey=VpcId,ParameterValue=$VPC_ID \
    ParameterKey=SubnetIds,ParameterValue=\"$SUBNET_IDS\" \
    ParameterKey=DbUrl,ParameterValue="jdbc:postgresql://${RDS_ENDPOINT}:5432/sscm" \
    ParameterKey=DbUsername,ParameterValue=sscm \
    ParameterKey=DbPassword,ParameterValue=$DB_PASSWORD \
    ParameterKey=JwtSecret,ParameterValue=$JWT_SECRET \
    ParameterKey=RedisHost,ParameterValue=$REDIS_ENDPOINT \
    ParameterKey=EncryptionKey,ParameterValue=$ENCRYPTION_KEY \
    ParameterKey=KafkaBootstrapServers,ParameterValue="placeholder:9092" \
    ParameterKey=AnalyticsDbUrl,ParameterValue="jdbc:postgresql://${ANALYTICS_RDS_ENDPOINT}:5432/sscm_analytics" \
    ParameterKey=AnalyticsDbUsername,ParameterValue=sscm \
    ParameterKey=AnalyticsDbPassword,ParameterValue=$DB_PASSWORD \
    ParameterKey=GeminiApiKey,ParameterValue=$GEMINI_API_KEY \
  --region ap-northeast-2

aws cloudformation wait stack-create-complete --stack-name sscm-alb --region ap-northeast-2

# ALB DNS 메모
ALB_DNS=$(aws cloudformation describe-stacks --stack-name sscm-alb --query 'Stacks[0].Outputs[?OutputKey==`AlbDnsName`].OutputValue' --output text --region ap-northeast-2)
echo "ALB DNS: $ALB_DNS"
```

### Step 4: MSK 완료 대기 + Kafka 부트스트랩 업데이트
```bash
aws cloudformation wait stack-create-complete --stack-name sscm-msk --region ap-northeast-2

# Kafka 부트스트랩 서버 추출
MSK_ARN=$(aws cloudformation describe-stacks --stack-name sscm-msk --query 'Stacks[0].Outputs[?OutputKey==`MskClusterArn`].OutputValue' --output text --region ap-northeast-2)
KAFKA_BOOTSTRAP=$(aws kafka get-bootstrap-brokers --cluster-arn $MSK_ARN --query 'BootstrapBrokerString' --output text --region ap-northeast-2)
echo "Kafka: $KAFKA_BOOTSTRAP"

# SSM 파라미터 업데이트
aws ssm put-parameter --name /sscm/prod/kafka-bootstrap-servers --value "$KAFKA_BOOTSTRAP" --type String --overwrite --region ap-northeast-2
```

### Step 5: 코드 업데이트 (하드코딩된 값 변경)
```bash
# prometheus-prod.yml — ALB DNS 업데이트
sed -i "s|targets:.*|targets: ['${ALB_DNS}']|" infra/monitoring/prometheus-prod.yml

# 여기서 커밋은 하지 않음 — Step 7에서 이미지 빌드 시 반영
echo "prometheus-prod.yml 업데이트 완료: $ALB_DNS"
```

### Step 6: Docker 이미지 빌드 + ECR 푸시
```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com

# 백엔드
docker build -t 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest .
docker push 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest

# 프론트엔드 Docker 빌드 불필요 — S3+CloudFront로 서빙 (Step 8에서 npm build → S3 sync)

# Prometheus + Grafana (monitoring)
cd ../sscm-backend/infra/monitoring
docker build -f Dockerfile.prometheus -t 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-prometheus:latest .
docker push 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-prometheus:latest
docker build -f Dockerfile.grafana -t 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-grafana:latest .
docker push 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-grafana:latest
```

### Step 7: ECS 배포 — ~5분
```bash
cd ../..
aws cloudformation create-stack \
  --stack-name sscm-ecs \
  --template-body file://infra/cfn-ecs-cluster.yml \
  --parameters \
    ParameterKey=Environment,ParameterValue=prod \
    ParameterKey=VpcId,ParameterValue=$VPC_ID \
    ParameterKey=SubnetIds,ParameterValue=\"$SUBNET_IDS\" \
    ParameterKey=BackendImage,ParameterValue=516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest \
  --capabilities CAPABILITY_NAMED_IAM \
  --region ap-northeast-2

aws cloudformation wait stack-create-complete --stack-name sscm-ecs --region ap-northeast-2
```

### Step 8: CDN (CloudFront + S3) — ~5분
```bash
aws cloudformation create-stack \
  --stack-name sscm-cdn \
  --template-body file://infra/cfn-cdn.yml \
  --parameters \
    ParameterKey=AlbDnsName,ParameterValue=$ALB_DNS \
  --region ap-northeast-2

aws cloudformation wait stack-create-complete --stack-name sscm-cdn --region ap-northeast-2

# CloudFront 도메인 메모
CF_DOMAIN=$(aws cloudformation describe-stacks --stack-name sscm-cdn --query 'Stacks[0].Outputs[?OutputKey==`CloudFrontDomain`].OutputValue' --output text --region ap-northeast-2)
CF_ID=$(aws cloudformation describe-stacks --stack-name sscm-cdn --query 'Stacks[0].Outputs[?OutputKey==`CloudFrontId`].OutputValue' --output text --region ap-northeast-2)
echo "CloudFront: $CF_DOMAIN (ID: $CF_ID)"

# S3에 프론트엔드 빌드 업로드
cd ../sscm-frontend
npm ci && npm run build
aws s3 sync dist/ s3://sscm-frontend-static --delete --region ap-northeast-2
aws cloudfront create-invalidation --distribution-id $CF_ID --paths "/*"
```

### Step 9: 모니터링 — ~5분
```bash
cd ../sscm-backend
aws cloudformation create-stack \
  --stack-name sscm-monitoring \
  --template-body file://infra/cfn-monitoring.yml \
  --parameters \
    ParameterKey=VpcId,ParameterValue=$VPC_ID \
    ParameterKey=SubnetIds,ParameterValue=\"$SUBNET_IDS\" \
    ParameterKey=PrometheusImage,ParameterValue=516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-prometheus:latest \
    ParameterKey=GrafanaImage,ParameterValue=516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-grafana:latest \
    ParameterKey=GrafanaAdminPassword,ParameterValue=admin \
    ParameterKey=GmailAppPassword,ParameterValue=$GMAIL_APP_PASSWORD \
  --capabilities CAPABILITY_NAMED_IAM \
  --region ap-northeast-2

aws cloudformation wait stack-create-complete --stack-name sscm-monitoring --region ap-northeast-2
```

### Step 10: 코드 하드코딩 업데이트 + 커밋 + 재배포
```bash
# SecurityConfig.java — CloudFront 도메인 CORS 업데이트
# (수동으로 SecurityConfig.java 열어서 CloudFront 도메인 변경)

# 프론트엔드 cd.yml — CloudFront Distribution ID 업데이트
# (수동으로 .github/workflows/cd.yml에서 Distribution ID 변경)

# Grafana 대시보드 — ALB LoadBalancer dimension 업데이트
# (sscm-overview.json에서 ALB ARN suffix 변경)

# 커밋 + push → CD 자동 배포
```

### Step 11: 시드 데이터
```bash
# 기본 시드
curl -X POST "http://${ALB_DNS}/api/v1/dev/seed/all?key=sscm-seed-2026"

# 대규모 시드
curl -X POST "http://${ALB_DNS}/api/v1/dev/seed/large?key=sscm-seed-2026"

# 새별중학교
curl -X POST "http://${ALB_DNS}/api/v1/dev/seed/school2?key=sscm-seed-2026"

# Analytics backfill
TOKEN=$(curl -s -X POST "http://${ALB_DNS}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@sscm.dev","password":"admin1234"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
curl -X POST "http://${ALB_DNS}/api/v1/analytics/admin/backfill" -H "Authorization: Bearer $TOKEN"
```

### Step 11-2: 임베딩 생성 (pgvector)
```bash
# 피드백/상담 텍스트 임베딩 (Gemini API 호출, 3~5분 소요)
curl -X POST "http://${ALB_DNS}/api/v1/dev/seed/embeddings?key=sscm-seed-2026"
```

**참고:**
- pgvector 확장은 V10 Flyway 마이그레이션에서 자동 설치됨
- Docker 이미지는 `pgvector/pgvector:pg16` 사용 (docker-compose.yml)
- 프로덕션 RDS에서는 `CREATE EXTENSION IF NOT EXISTS vector;` 수동 실행 필요할 수 있음

### Step 12: 검증
```bash
# 헬스체크
curl -sf "http://${ALB_DNS}/actuator/health"

# 프론트엔드
curl -sf "https://${CF_DOMAIN}" | head -1

# Grafana
curl -sf "http://${ALB_DNS}/grafana/api/health"

# 로그인 테스트
curl -s -X POST "http://${ALB_DNS}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"teacher@sscm.dev","password":"teacher1234"}' | head -c 100

echo "전체 재배포 완료!"
```

---

## 3. 주의사항

| 항목 | 설명 |
|------|------|
| **MSK가 병목** | 30~45분 소요. Step 2에서 바로 시작하고 Step 3과 병렬 진행 |
| **S3 버킷 이름** | `sscm-frontend-static`은 글로벌 유니크. 삭제 후 24시간 내 재생성 불가할 수 있음 |
| **RDS 데이터 삭제** | DeletionPolicy: Delete라서 스택 삭제 시 영구 삭제. 시드로 복구 |
| **CloudFront 도메인 변경** | 재생성 시 도메인 바뀜 → SecurityConfig CORS + 프론트 cd.yml 업데이트 필요 |
| **ALB DNS 변경** | 재생성 시 DNS 바뀜 → prometheus-prod.yml + Grafana 대시보드 업데이트 필요 |
| **pgvector Docker 이미지** | `pgvector/pgvector:pg16` — 기본 `postgres:16-alpine`이 아님 |
| **임베딩 생성** | /seed/embeddings → Gemini API 호출 815건, 3~5분 소요 |

---

## 4. 예상 비용 절감

| 기간 | 인프라 유지 비용 | 절감액 |
|------|----------------|--------|
| 7일 | ~$63 (~8만원) | - |
| 내린 기간 | $0 | $63/주 절감 |
| 재배포 시 | 추가 비용 없음 (기존 리소스 재생성) | - |

ECR 이미지 저장 비용만 미미하게 발생 (~$0.10/GB/월).
