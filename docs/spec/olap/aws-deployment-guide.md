# AWS 풀 아키텍처 배포 가이드

> 목적: ECS + ALB + RDS + MSK + ElastiCache 풀 구성으로 배포
> 비용: ~$0.32/hr (~$7.7/일). 발표 전후로만 켜놓을 것.

---

## 사전 조건

- AWS CLI 설치 + `aws configure` 완료 (ap-northeast-2)
- Docker 설치 (이미지 빌드용)
- ECR 리포지토리 존재 (확인됨)

---

## 배포 순서

### 0단계: VPC/서브넷 정보 확인

AWS 콘솔 → VPC → "내 VPC"에서 확인:

```
VPC ID:       vpc-xxxxxxxxx
서브넷 1:     subnet-aaaaa (ap-northeast-2a)
서브넷 2:     subnet-bbbbb (ap-northeast-2c)
VPC CIDR:     172.31.0.0/16 (기본 VPC)
```

> 기본 VPC를 사용하면 별도 생성 불필요. 서브넷은 2개 이상 필요 (AZ 분산).

---

### 1단계: ALB + SSM Parameter Store 스택 생성

**CloudFormation → 스택 생성 → 템플릿 업로드**

- 파일: `infra/cfn-alb.yml`
- 스택 이름: `sscm-alb`
- 파라미터:
  | 파라미터 | 값 (일단 기본값, 나중에 업데이트) |
  |---------|------|
  | VpcId | vpc-xxxxxxx |
  | SubnetIds | subnet-aaaaa,subnet-bbbbb |
  | DbUrl | CHANGE_ME (2단계 후 업데이트) |
  | DbUsername | sscm |
  | DbPassword | sscm1234! |
  | JwtSecret | (256비트 이상 문자열) |
  | RedisHost | CHANGE_ME (2단계 후 업데이트) |
  | EncryptionKey | (32바이트 hex 문자열) |
  | KafkaBootstrapServers | CHANGE_ME (3단계 후 업데이트) |
  | AnalyticsDbUrl | CHANGE_ME (2단계 후 업데이트) |
  | AnalyticsDbUsername | sscm |
  | AnalyticsDbPassword | sscm1234! |
  | GeminiApiKey | (Google AI Studio에서 발급) |

→ 스택 생성 완료 대기 (~2분)

---

### 2단계: RDS + Redis 스택 생성

- 파일: `infra/cfn-data.yml`
- 스택 이름: `sscm-data`
- 파라미터:
  | 파라미터 | 값 |
  |---------|------|
  | VpcId | vpc-xxxxxxx |
  | SubnetIds | subnet-aaaaa,subnet-bbbbb |
  | VpcCidr | 172.31.0.0/16 |
  | DbPassword | sscm1234! |

→ 스택 생성 완료 대기 (~10분, RDS가 느림)

완료 후 **Outputs 탭**에서 확인:
- `RdsJdbcUrl`: jdbc:postgresql://sscm-db.xxxx.ap-northeast-2.rds.amazonaws.com:5432/sscm
- `AnalyticsRdsJdbcUrl`: jdbc:postgresql://sscm-analytics-db.xxxx.ap-northeast-2.rds.amazonaws.com:5432/sscm_analytics
- `RedisEndpoint`: sscm-redis.xxxx.cache.amazonaws.com

---

### 3단계: MSK (Kafka) 스택 생성

- 파일: `infra/cfn-msk.yml`
- 스택 이름: `sscm-msk`
- 파라미터:
  | 파라미터 | 값 |
  |---------|------|
  | VpcId | vpc-xxxxxxx |
  | SubnetIds | subnet-aaaaa,subnet-bbbbb |
  | VpcCidr | 172.31.0.0/16 |

→ 스택 생성 완료 대기 (**~15-20분**, MSK 느림)

완료 후 Bootstrap Servers 확인:
- AWS 콘솔 → MSK → 클러스터 → `sscm-kafka` → "클라이언트 정보 보기"
- "Bootstrap servers" 복사 (예: `b-1.sscm-kafka.xxx.kafka.ap-northeast-2.amazonaws.com:9092,b-2...`)

---

### 4단계: SSM Parameter Store 값 업데이트

2~3단계에서 얻은 실제 값으로 업데이트.

**AWS 콘솔 → Systems Manager → Parameter Store** 또는 CLI:

```bash
# RDS 운영 DB
aws ssm put-parameter --name /sscm/prod/db-url \
  --value "jdbc:postgresql://sscm-db.xxxx.ap-northeast-2.rds.amazonaws.com:5432/sscm" \
  --overwrite

# Analytics DB
aws ssm put-parameter --name /sscm/prod/analytics-db-url \
  --value "jdbc:postgresql://sscm-analytics-db.xxxx.ap-northeast-2.rds.amazonaws.com:5432/sscm_analytics" \
  --overwrite

# Redis
aws ssm put-parameter --name /sscm/prod/redis-host \
  --value "sscm-redis.xxxx.cache.amazonaws.com" \
  --overwrite

# Kafka
aws ssm put-parameter --name /sscm/prod/kafka-bootstrap-servers \
  --value "b-1.sscm-kafka.xxx:9092,b-2.sscm-kafka.xxx:9092" \
  --overwrite
```

---

### 5단계: Docker 이미지 빌드 + ECR 푸시

**로컬(WSL)에서:**

```bash
cd /mnt/c/Users/seung/workspace/sscm-backend

# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com

# 빌드
docker build -t sscm-backend .

# 태그 + 푸시
docker tag sscm-backend:latest 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest
docker push 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest
```

---

### 6단계: ECS 클러스터 스택 생성

- 파일: `infra/cfn-ecs-cluster.yml`
- 스택 이름: `sscm-ecs`
- 파라미터:
  | 파라미터 | 값 |
  |---------|------|
  | VpcId | vpc-xxxxxxx |
  | SubnetIds | subnet-aaaaa,subnet-bbbbb |
  | BackendImage | 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-backend:latest |
  | FrontendImage | 516232034601.dkr.ecr.ap-northeast-2.amazonaws.com/sscm-frontend:latest |

> Frontend 이미지가 없으면 Backend만 먼저 배포 (FrontendService의 DesiredCount를 0으로 수정)

→ 스택 생성 완료 대기 (~3분)

---

### 7단계: 분석 DB 스키마 생성

RDS에 분석 테이블을 만들어야 함. 로컬에서 접속:

```bash
# RDS에 접속하려면 같은 VPC에서만 가능.
# 방법 1: EC2 bastion에서 접속
# 방법 2: RDS를 일시적으로 PubliclyAccessible=true로 변경

# 접속 후:
psql -h sscm-analytics-db.xxxx.rds.amazonaws.com -U sscm -d sscm_analytics
# 비밀번호: sscm1234!

# analytics-schema.sql 실행
```

또는 Backend가 뜨면 `/api/v1/analytics/admin/backfill` 호출로 테이블 자동 생성될 수도 있음.

---

### 8단계: 확인

```bash
# ALB DNS 확인 (CloudFormation sscm-alb 스택의 Outputs)
# 예: sscm-alb-xxxxxxx.ap-northeast-2.elb.amazonaws.com

# 헬스 체크
curl http://<ALB-DNS>/actuator/health

# 시드 데이터
curl -X POST http://<ALB-DNS>/api/v1/dev/seed

# 로그인 확인
curl -X POST http://<ALB-DNS>/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"teacher@sscm.dev","password":"teacher1234"}'
```

---

### 9단계: 부하 테스트

```bash
cd infra/loadtest
k6 run --env BASE_URL=http://<ALB-DNS> k6-dashboard-api.js
```

Grafana는 별도 배포 필요 (cfn-monitoring.yml) 또는 CloudWatch에서 ECS 메트릭 확인.

---

## 정리 (비용 절약)

발표 끝나면 스택 삭제 (역순):

```bash
aws cloudformation delete-stack --stack-name sscm-ecs
aws cloudformation delete-stack --stack-name sscm-msk
aws cloudformation delete-stack --stack-name sscm-data
aws cloudformation delete-stack --stack-name sscm-alb
```

---

## 예상 소요 시간

| 단계 | 시간 |
|------|------|
| 1. ALB 스택 | ~2분 |
| 2. Data 스택 (RDS x2 + Redis) | ~10분 |
| 3. MSK 스택 | ~15-20분 |
| 4. SSM 업데이트 | ~2분 |
| 5. Docker 빌드 + 푸시 | ~5분 |
| 6. ECS 스택 | ~3분 |
| 7. 스키마 + 시드 | ~5분 |
| **총** | **~40-50분** |

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| ECS Task 시작 실패 | SSM 파라미터 값 잘못됨 | CloudWatch Logs > /ecs/sscm-backend 확인 |
| RDS 연결 거부 | 보안 그룹 | RDS SG에 ECS SG의 인바운드 허용 확인 |
| Kafka 연결 실패 | Bootstrap servers 잘못됨 | MSK > 클라이언트 정보에서 정확한 값 복사 |
| ALB 502/503 | ECS Task 아직 안 뜸 | ECS 서비스 > Tasks 탭에서 상태 확인 |
| 헬스체크 실패 | /health 경로 | /actuator/health로 수정 필요시 |
