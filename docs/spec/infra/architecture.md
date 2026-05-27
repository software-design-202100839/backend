# 시스템 아키텍처

- **최종 수정:** 2026-05-27

> **변경 이력**
> - 2026-04-09: ECS Fargate + ALB + Redis 구조
> - 2026-04-20: EC2 + Nginx + Docker Compose로 전환 (개발/테스트 환경). Redis 제거.
> - 2026-05-26: AWS 풀 아키텍처 재배포 (다중 학교 SaaS 확장 전제). ECS + ALB + RDS x2 + Redis + MSK + CloudFront.
> - 2026-05-27: 멀티테넌시, AI 챗봇, 모니터링 3계층, CI/CD 파이프라인 문서 반영.

---

## 프로덕션 아키텍처 (AWS)

현재 운영 중인 AWS 풀 아키텍처. 멀티테넌시(다중 학교) 지원을 전제로 설계.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              클라이언트                                   │
│   교사 (웹)  /  학생 (웹)  /  학부모 (웹)                                 │
└───────────────────────────┬─────────────────────────────────────────────┘
                            │ HTTPS
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     CloudFront (CDN)                                     │
│   정적 자산 (React SPA) → S3 버킷                                        │
│   API 요청 (/api/*) → ALB Origin                                        │
└───────────────────────────┬─────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     ALB (Application Load Balancer)                      │
│                                                                          │
│   /api/*      → ECS Backend (Target Group)                               │
│   /grafana/*  → ECS Monitoring (Target Group)                            │
│   /*          → ECS Frontend (Target Group)                              │
└──────┬──────────────────┬──────────────────┬────────────────────────────┘
       │                  │                  │
       ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐
│  Backend     │  │  Frontend    │  │  Monitoring              │
│  ECS Fargate │  │  ECS Fargate │  │  ECS Fargate             │
│  0.5vCPU/1GB │  │  0.25/0.5GB  │  │  0.25vCPU/0.5GB         │
│              │  │              │  │  Prometheus + Grafana     │
│  Spring Boot │  │  React 19    │  │  (사이드카 컨테이너)      │
│  3.5         │  │  + TypeScript│  │                          │
└──────┬───────┘  └──────────────┘  └──────────────────────────┘
       │
       │  데이터 계층
       ├──────────────────────────────────────────────────┐
       │                                                   │
       ▼                                                   ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  RDS (운영)       │  │  RDS (분석)       │  │  ElastiCache     │
│  sscm-db         │  │  sscm-analytics  │  │  Redis           │
│  db.t3.micro     │  │  -db             │  │  cache.t3.micro  │
│  PostgreSQL 16   │  │  db.t3.micro     │  │                  │
│                  │  │  PostgreSQL 16   │  │  JWT 블랙리스트   │
│  users, scores,  │  │                  │  │  L1 캐시          │
│  classes,        │  │  집계/분석 데이터  │  │                  │
│  counselings...  │  │                  │  │                  │
└──────────────────┘  └──────────────────┘  └──────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│  MSK (Amazon Managed Streaming Kafka)     │
│  kafka.t3.small x2 브로커                 │
│                                           │
│  이벤트 파이프라인:                         │
│  운영 DB 변경 → Kafka → Consumer → 분석 DB │
│  (장애 격리 + backfill API 복구)           │
└──────────────────────────────────────────┘
```

### AWS 리소스 요약

| 리소스 | 스펙 | 용도 | 비용 |
|--------|------|------|------|
| ECS Fargate (backend) | 0.5 vCPU / 1GB | Spring Boot API | ~$0.03/hr |
| ECS Fargate (frontend) | 0.25 vCPU / 0.5GB | React SPA (Nginx) | ~$0.01/hr |
| ECS Fargate (monitoring) | 0.25 vCPU / 0.5GB | Prometheus + Grafana | ~$0.01/hr |
| ALB | — | 경로 기반 라우팅 | ~$0.02/hr |
| RDS x2 | db.t3.micro | 운영 + 분석 DB | ~$0.02/hr x2 |
| ElastiCache Redis | cache.t3.micro | JWT 블랙리스트 캐시 | ~$0.02/hr |
| MSK Kafka | kafka.t3.small x2 | 이벤트 파이프라인 | ~$0.15/hr x2 |
| CloudFront + S3 | — | 프론트엔드 CDN | 거의 무료 |
| **합계** | | | **~$47/월** |

---

## 멀티테넌시

ADR-006에 따라 **Shared-Database, Discriminator-Column** 전략을 채택.

```
┌─────────────────────────────────────────────────┐
│  JWT Token                                       │
│  { sub: "userId", schoolId: 1, role: "TEACHER" } │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│  JwtAuthenticationFilter                         │
│  → schoolId 추출 → TenantContext.set(schoolId)  │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│  TenantContext (ThreadLocal)                     │
│  → 서비스 메서드 시그니처 변경 없이 school_id 전파│
│  → 교차 학교 접근 시 403 차단                    │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│  Database                                        │
│  users.school_id FK → schools.id                 │
│  classes.school_id FK → schools.id               │
│  → Discriminator Column으로 데이터 격리           │
└─────────────────────────────────────────────────┘
```

---

## 모니터링 아키텍처 (3계층)

Prometheus + Grafana를 ECS 사이드카로 배포. 19개 패널 + 5개 알림 규칙.

### 계층 구성

| 계층 | 패널 수 | 주요 지표 |
|------|---------|-----------|
| 앱 (Application) | 10 | API p95 응답시간, HTTP 처리량, 5xx 에러, JVM Heap, GC 일시정지, HikariCP 커넥션풀, Kafka Consumer Lag, 스레드, Uptime, CPU |
| 비즈니스 (Business) | 5 | 성적 등록, 상담 생성, 피드백, 알림, AI 챗봇 사용 |
| 인프라 (Infra) | 4 | CloudWatch 연동 — ECS CPU/메모리, RDS 커넥션, Redis 메모리 |

### 알림 규칙

| 규칙 | 조건 | 채널 |
|------|------|------|
| 5xx 에러 | 5xx > 0 | Grafana Alert |
| Heap 포화 | JVM Heap > 90% | Grafana Alert |
| 커넥션풀 고갈 | HikariCP active > 90% | Grafana Alert |
| Consumer 지연 | Kafka Lag 지속 증가 | Grafana Alert |
| 응답 급등 | API p95 > 임계값 | Grafana Alert |

```
┌──────────────┐     scrape      ┌──────────────┐     query     ┌──────────────┐
│  Backend     │ ──────────────► │  Prometheus  │ ◄──────────── │  Grafana     │
│  /actuator/  │   (사이드카)     │              │               │  19 panels   │
│  prometheus  │                 │              │               │  5 alerts    │
└──────────────┘                 └──────────────┘               └──────────────┘
                                        ▲
                                        │ CloudWatch
                                        │ (인프라 지표)
                                 ┌──────┴──────┐
                                 │  AWS        │
                                 │  ECS/RDS/   │
                                 │  Redis      │
                                 └─────────────┘
```

---

## CI/CD 파이프라인

### 백엔드 (자동 배포 — ECS)

```
Developer
    │
    │ git push → develop
    ▼
GitHub Actions CI
    │
    ├─ PostgreSQL 서비스 컨테이너 (테스트용)
    ├─ ./gradlew test jacocoTestReport
    ├─ JaCoCo 커버리지 확인
    └─ SonarCloud 정적 분석
    │
    │ CI 통과
    ▼
GitHub Actions CD
    │
    ├─ Docker build (multi-stage)
    ├─ ECR push (latest 태그)
    └─ ECS force-new-deployment (rolling update)
```

### 프론트엔드 (자동 배포 — S3 + CloudFront)

```
Developer
    │
    │ git push → develop
    ▼
GitHub Actions CD
    │
    ├─ npm ci && npm run build
    ├─ S3 sync (빌드 산출물 업로드)
    └─ CloudFront 캐시 무효화 (/*) 
```

---

## 개발 환경 아키텍처 (Docker Compose)

> 아래는 로컬 개발/테스트 환경이다. 프로덕션은 위의 AWS 아키텍처를 사용한다.

### 전체 구조

```
┌──────────────────────────────────────────────────────────────┐
│                         클라이언트                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │ 교사 (웹)    │  │ 학생 (웹)    │  │ 학부모 (웹)  │          │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘          │
└─────────┼────────────────┼────────────────┼──────────────────┘
          │                │                │
          │           HTTP (Port 80)        │
          ▼                ▼                ▼
┌──────────────────────────────────────────────────────────────┐
│                       EC2 (t3.small)                          │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                  Nginx (리버스 프록시)                    │  │
│  │                                                         │  │
│  │   /api/*   →  backend:8080                              │  │
│  │   /*       →  frontend:80                               │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌────────────────────┐    ┌────────────────────────────┐    │
│  │  Frontend          │    │  Backend                   │    │
│  │  (React 19 + TS)   │    │  (Spring Boot 3.5)         │    │
│  │                    │REST│                            │    │
│  │  - 성적 입력/조회   │◄──►│  Spring Security           │    │
│  │  - 레이더 차트      │    │  (JWT + RBAC)              │    │
│  │  - 학생부 통합 뷰   │ WS │                            │    │
│  │  - 상담 타임라인    │◄──►│  WebSocket (STOMP)         │    │
│  │  - ADMIN 관리 화면  │    │                            │    │
│  └────────────────────┘    └──────────┬─────────────────┘    │
│                                       │                       │
│                         ┌─────────────┘                       │
│                         ▼                                     │
│  ┌──────────────────────────────────────────────────────┐    │
│  │              PostgreSQL 16 (Docker Volume)            │    │
│  │                                                       │    │
│  │  - users, teachers, students, parents                 │    │
│  │  - classes, student_enrollments, teacher_assignments  │    │
│  │  - scores, student_records, feedbacks, counselings    │    │
│  │  - refresh_tokens, token_blacklist, invite_tokens     │    │
│  │  - audit_logs, notifications                          │    │
│  │  - schools (멀티테넌시)                                │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
└──────────────────────────────────────────────────────────────┘
                              │
                              │ SMS (Solapi API)
                              ▼
                    ┌─────────────────┐
                    │  Solapi SMS     │
                    │                 │
                    │  - OTP 발송     │
                    │  - 알림 발송    │
                    │  (민감정보 제외) │
                    └─────────────────┘
```

### 인프라 재설계 근거 (개발 환경)

| 항목 | 프로덕션 (AWS) | 개발 환경 (EC2) | 이유 |
|------|---------------|----------------|------|
| 컨테이너 실행 | ECS Fargate | Docker Compose | 개발 시 단일 인스턴스면 오케스트레이터 불필요 |
| 로드밸런서 | ALB | Nginx | 개발 환경에서 ALB 비용 불필요 |
| 세션 저장소 | Redis (ElastiCache) | PostgreSQL 테이블 | 개발 시 장애 지점 감소 |
| 비용 | ~$1.5/일 | ~$0.5/일 | 개발 환경 비용 최소화 |

### Docker Compose 구성

```yaml
# docker-compose.prod.yml 구조

services:
  nginx:
    image: nginx:alpine
    ports: ["80:80"]
    depends_on: [backend, frontend]

  frontend:
    image: {ECR}/sscm-frontend:latest
    environment:
      - VITE_API_BASE_URL=/api/v1

  backend:
    image: {ECR}/sscm-backend:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=postgres
      - JWT_SECRET, ENCRYPTION_KEY (환경변수 주입)
      - SOLAPI_API_KEY, SOLAPI_API_SECRET
    depends_on: [postgres]

  postgres:
    image: postgres:16
    volumes: [postgres_data:/var/lib/postgresql/data]
    environment:
      - POSTGRES_DB=sscm
```

---

## 보안 아키텍처

```
클라이언트 요청
    │
    ▼
ALB / Nginx (환경에 따라)
    │  /api/* 프록시
    ▼
JwtAuthenticationFilter
    ├─ Bearer 토큰 추출
    ├─ JWT 서명 검증 (HS256)
    ├─ schoolId 추출 → TenantContext 설정 (멀티테넌시)
    ├─ token_blacklist 조회 (Redis L1 → DB fallback)
    └─ SecurityContext 설정
    │
    ▼
AuthorizationFilter
    ├─ /admin/** → ADMIN only
    ├─ 성적 수정 → teacher_assignments 권한 체크
    ├─ 학생부 수정 → homeroom_teacher_id 확인
    ├─ 상담/피드백 수정 → 작성자 확인
    ├─ 학생/학부모 조회 → 본인/자녀 확인
    └─ 교차 학교 접근 → TenantContext 검증 (403 차단)
    │
    ▼
AES-256-GCM 복호화
    └─ counselings.content, next_plan
    └─ users.email, phone (응답 시)
```

---

## 데이터 흐름 예시

### 성적 입력

```
교사 → [성적 입력 폼] POST /api/v1/scores
    → TenantContext로 school_id 검증
    → teacher_assignments 권한 확인
    → Score 저장 + grade_letter/rank 계산
    → audit_logs 기록
    → Kafka 이벤트 발행 (분석 DB 동기화)
    → NotificationEvent 발행
    → Solapi SMS 발송 ("성적이 업데이트되었습니다")
    → WebSocket push (브라우저 실시간 알림)
```

### 계정 활성화

```
학생/학부모 → [활성화 화면] 전화번호 입력
    → phone_hash 조회 (사전 등록 여부 확인)
    → Solapi SMS OTP 발송 (5분 만료)
    → OTP + 이메일 + 비밀번호 제출
    → bcrypt 해시 저장, 계정 활성화
```

---

## 기술 스택

| 계층 | 기술 | 버전 |
|------|------|------|
| Frontend | React, TypeScript, Vite | 19, 5, 8 |
| Backend | Spring Boot, Spring Security, JPA | 3.5 |
| AI | Spring AI, Gemini 2.5 Flash | — |
| Database | PostgreSQL (운영 + 분석) | 16 |
| 캐시 | ElastiCache Redis | — |
| 메시징 | MSK Kafka | — |
| 인증 | JWT (JJWT), bcrypt, AES-256-GCM | 0.12.5 |
| SMS | Solapi | REST API |
| 실시간 | WebSocket (STOMP) | — |
| 컨테이너 | ECS Fargate (prod), Docker Compose (dev) | — |
| CI/CD | GitHub Actions, ECR, S3, CloudFront | — |
| 모니터링 | Prometheus, Grafana, CloudWatch | — |
| CDN | CloudFront + S3 | — |
| 테스트 | JUnit 5, JaCoCo, SonarCloud | — |
