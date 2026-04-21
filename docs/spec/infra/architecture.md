# 시스템 아키텍처

- **최종 수정:** 2026-04-20 (인프라 재설계 — EC2 + Docker Compose)

> **변경 이력**
> - 2026-04-09: ECS Fargate + ALB + Redis 구조
> - 2026-04-20: EC2 + Nginx + Docker Compose로 전환. Redis 제거. SMS 알림 도입.
>   - 선택 이유: 단일 학교(~수백 명) 규모에서 ECS/ALB/Redis는 과잉 설계. 비용 1/3 절감, 운영 단순화.

---

## 전체 구조

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

---

## 인프라 재설계 근거

| 항목 | 기존 | 변경 | 이유 |
|------|------|------|------|
| 컨테이너 실행 | ECS Fargate | EC2 + Docker Compose | 단일 인스턴스면 오케스트레이터 불필요. 배포 단순화 |
| 로드밸런서 | ALB (~$18/월) | Nginx (무료) | 분산할 인스턴스가 1개 → ALB의 존재 이유 없음 |
| 세션 저장소 | Redis | PostgreSQL 테이블 | 토큰 TTL 관리는 @Scheduled 배치로 대체. 장애 지점 감소 |
| 알림 채널 | 이메일 (Spring Mail) | SMS (Solapi) | 학부모·학생 실사용 채널. 스팸 없음, 즉시 수신 |
| 비용 | ~$1.5/일 | ~$0.5/일 | EC2 t3.small 기준 약 1/3 수준 |

---

## Docker Compose 구성 (프로덕션)

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

## CI/CD 파이프라인

```
Developer (페르소나)
    │
    │ git push → feature/SSCM-{N}-{slug}
    ▼
GitHub Actions CI
    │
    ├─ PostgreSQL 서비스 컨테이너 (테스트용)
    ├─ ./gradlew test jacocoTestReport
    ├─ JaCoCo 커버리지 확인 (최소 30%)
    └─ SonarCloud 정적 분석
    │
    │ PR → develop 병합
    ▼
GitHub Actions CD
    │
    ├─ Docker build (backend / frontend)
    ├─ ECR push (latest 태그)
    │
    └─ SSH → EC2
           docker compose pull
           docker compose up -d --no-deps backend frontend
```

---

## 보안 아키텍처

```
클라이언트 요청
    │
    ▼
Nginx (HTTP)
    │  /api/* 프록시
    ▼
JwtAuthenticationFilter
    ├─ Bearer 토큰 추출
    ├─ JWT 서명 검증 (HS256)
    ├─ token_blacklist 조회 (로그아웃 여부)
    └─ SecurityContext 설정
    │
    ▼
AuthorizationFilter
    ├─ /admin/** → ADMIN only
    ├─ 성적 수정 → teacher_assignments 권한 체크
    ├─ 학생부 수정 → homeroom_teacher_id 확인
    ├─ 상담/피드백 수정 → 작성자 확인
    └─ 학생/학부모 조회 → 본인/자녀 확인
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
    → teacher_assignments 권한 확인
    → Score 저장 + grade_letter/rank 계산
    → audit_logs 기록
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
| Database | PostgreSQL | 16 |
| 인증 | JWT (JJWT), bcrypt, AES-256-GCM | 0.12.5 |
| SMS | Solapi | REST API |
| 실시간 | WebSocket (STOMP) | — |
| 컨테이너 | Docker, Docker Compose | — |
| CI/CD | GitHub Actions, ECR | — |
| 모니터링 | Prometheus, Grafana | — |
| 테스트 | JUnit 5, JaCoCo, SonarCloud | — |
