# 시스템 아키텍처

- **최종 수정:** 2026-03-27 (ECS Fargate + ALB 전환, 모니터링 이원화)

## 전체 구조

```
┌─────────────────────────────────────────────────────────┐
│                     클라이언트                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  교사 (웹)    │  │  학생 (웹)    │  │ 학부모 (웹)   │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
└─────────┼──────────────────┼──────────────────┼─────────┘
          │                  │                  │
          │             HTTPS (TLS)             │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────┐
│                      AWS ALB                             │
│           (HTTPS 터미네이션, 경로 기반 라우팅)              │
│        ACM 인증서 · 헬스체크 · 로드밸런싱                   │
│                                                          │
│    /api/* → Backend     /* → Frontend                    │
└────────────────────────┬────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼                             ▼
┌──────────────────┐          ┌──────────────────┐
│  ECS Fargate     │          │  ECS Fargate     │
│  Frontend Task   │          │  Backend Task    │
│                  │          │                  │
│  React 19+TS     │          │  Spring Boot 3.5 │
│  (Nginx 서빙)    │   REST   │                  │
│                  │◄────────►│  Spring Security  │
│  - 성적 입력 폼   │          │  (JWT + RBAC)    │
│  - 레이더 차트    │   WS     │                  │
│  - 상담 타임라인  │◄────────►│  WebSocket(STOMP)│
│  - 알림 센터     │          │                  │
└──────────────────┘          └────────┬──────────┘
                                       │
                         ┌─────────────┼─────────────┐
                         ▼             ▼             ▼
               ┌──────────────┐ ┌──────────┐ ┌───────────┐
               │ PostgreSQL 16│ │  Redis 7 │ │Spring Mail│
               │  (AWS RDS)   │ │          │ │ (SMTP)    │
               │              │ │          │ │           │
               │ - 사용자      │ │ - JWT    │ │ - 알림    │
               │ - 성적        │ │  블랙리스트│ │   이메일   │
               │ - 학생부      │ │ - Refresh│ │           │
               │ - 피드백      │ │   Token  │ │           │
               │ - 상담        │ │          │ │           │
               └──────────────┘ └──────────┘ └───────────┘

                         ┌─────────────────────────────────┐
                         │    모니터링 (Sprint 4에서 도입)     │
                         │                                   │
                         │  Prometheus ← Backend /actuator   │
                         │      ↓                            │
                         │  Grafana (대시보드)                 │
                         │      ↓                            │
                         │  AlertManager → PagerDuty         │
                         │                                   │
                         │  Dynatrace (보조, 15일 체험)        │
                         └─────────────────────────────────┘
```

### 인프라 선택 근거

| 항목 | 선택 | 대안 | 선택 이유 |
|------|------|------|-----------|
| 오케스트레이션 | ECS Fargate | EKS | 비용 1/5 (EKS 컨트롤 플레인 월 $73 vs ECS 실행 시간만 과금), 운영 부담 1/3. 2~3개 서비스에 EKS는 과잉. |
| 로드밸런서 | ALB | Nginx Ingress | ECS + ALB 네이티브 연동. HTTPS 터미네이션, 경로 기반 라우팅, 헬스체크 기본 제공. |
| 시크릿 관리 | Parameter Store | Secrets Manager | 표준 파라미터 무료 + SecureString 암호화. ECS Task Definition에서 직접 참조. |
| 컨테이너 레지스트리 | ECR | GHCR / Docker Hub | ECS와 동일 IAM 체계, imagePull에 별도 인증 불필요. |

## CI/CD 파이프라인

```
Developer (페르소나)
    │
    │ git push → feature branch
    ▼
GitHub (PR 생성)
    │
    ▼
GitHub Actions ──────────────────────────────────────
    │
    ├─ 1. Lint (ESLint + Checkstyle)
    ├─ 2. Unit Test (JUnit 5)
    ├─ 3. SonarCloud 분석 (+ JaCoCo 커버리지)
    ├─ 4. Snyk 의존성 스캔
    │
    │ ── PR 머지 후 (develop branch) ──
    │
    ├─ 5. Docker Build (frontend + backend)
    ├─ 6. Docker Push → AWS ECR
    └─ 7. aws ecs update-service → ECS Fargate 롤링 배포
```

**GitHub Actions 직접 배포 선택 근거:**
- Argo CD는 Kubernetes 전용. ECS 환경에서는 불필요.
- 이미지 빌드 → ECR push → `aws ecs update-service`로 하나의 워크플로우 파일에 완결.
- GitOps 원칙은 ECS Task Definition을 Git에서 관리하는 것으로 충족.

## 보안 아키텍처 (3단계)

```
빌드 타임                 런타임                    운영
┌─────────────┐    ┌─────────────┐    ┌─────────────────┐
│   Snyk      │    │ Burp Suite  │    │ Prometheus      │
│             │    │             │    │ + Grafana       │
│ - 의존성     │    │ - DAST 스캔  │    │ - 메트릭 수집    │
│   취약점     │    │ - XSS/SQLI  │    │ - 대시보드       │
│   스캔      │    │   테스트     │    │                 │
│             │    │ - 인증 우회  │    │       ↓         │
│ (CI 단계)   │    │   테스트     │    │   PagerDuty     │
│             │    │             │    │ - 인시던트 알림   │
└─────────────┘    └─────────────┘    └─────────────────┘

                                      ┌─────────────────┐
                                      │  Dynatrace (보조)│
                                      │ - AI 근본 원인   │
                                      │   분석 (15일 체험)│
                                      └─────────────────┘
```

**모니터링 전략:**
- **Prometheus + Grafana (메인):** 무료, 상시 운영. Spring Boot Actuator + Micrometer 네이티브 지원.
- **Dynatrace (보조):** 상용 APM. 15일 무료 체험을 발표 직전에 시작하여 스크린샷 확보. "오픈소스 vs 상용 APM 비교 분석" ADR 작성 → 프로젝트 깊이 상승.
- **PagerDuty:** Prometheus AlertManager → PagerDuty 연동. 무료 tier.

## 개인정보 암호화

| 구간 | 방법 |
|------|------|
| 전송 중 (in-transit) | HTTPS/TLS. ALB에서 TLS 터미네이션. AWS ACM 무료 인증서. |
| 저장 시 (at-rest) DB 레벨 | AWS RDS 스토리지 암호화 옵션 활성화. |
| 저장 시 (at-rest) 앱 레벨 | 민감 필드(연락처 등) AES-256 암호화. JPA `@Convert` + AttributeConverter 자동 처리. |
| 비밀번호 | BCrypt 해싱 (Spring Security 기본 제공). |

## 백업 및 복구

- **PostgreSQL**: AWS RDS 자동 백업(매일 스냅샷 + point-in-time recovery). 자체 운영 시 `pg_dump` 크론잡 → S3 저장.
- **Redis**: 캐시/블랙리스트 용도이므로 백업 필수 아님. 유실 시 자동 재생성되는 구조로 설계.
- **인프라**: ECS Task Definition + 코드 모두 Git에 있으므로 재배포 가능.

## 사용자 권한 매트릭스

| 기능 | 교사 (TEACHER) | 학생 (STUDENT) | 학부모 (PARENT) |
|------|:-:|:-:|:-:|
| 성적 입력/수정 | O | X | X |
| 성적 조회 (본인/담당) | O | O (본인) | O (자녀) |
| 학생부 조회/수정 | O | X | X |
| 피드백 작성 | O | X | X |
| 피드백 조회 | O | O (본인) | O (자녀) |
| 상담 내역 기록 | O | X | X |
| 상담 내역 조회 | O (교사 간 공유) | X | X |
| 보고서 생성/다운로드 | O | X | X |
| 알림 수신 | O | O | O |
