# Jira Sprint 0 이슈 목록 (v2)

- **수정 사유:** "왜 지금?"에 답할 수 없는 작업 제거. CI/Docker/ECS 등은 필요해지는 시점(Sprint 1~3)으로 이동.

---

## Sprint 0 이슈 (15개)

### 이백엔드 담당 (6개)

**SSCM-1: GitHub 레포 생성 + 브랜치 전략 + 초기 문서 커밋**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: Highest
- 왜 지금: 코드를 저장할 곳이 있어야 시작 가능
- 완료 기준: 레포 생성, main/develop 브랜치, 문서 커밋 완료
- ✅ **이미 완료됨**

**SSCM-2: ERD 설계 + Flyway 초기 마이그레이션**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: Highest
- 왜 지금: API를 만들려면 DB 테이블이 먼저 있어야 함
- 완료 기준: ERD 문서 커밋, V1__init_schema.sql 로컬 PG에서 실행 확인
- 차단: SSCM-3 완료 후 Flyway 연동

**SSCM-3: Spring Boot 프로젝트 초기화**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: Highest
- 왜 지금: 백엔드 API를 만들 기반이 필요
- 설명:
  - Gradle (Kotlin DSL), Java 17, Spring Boot 3.x
  - 의존성: Web, Security, Data JPA, Flyway, PostgreSQL, Redis, Validation, SpringDoc OpenAPI, Lombok
  - 패키지: `com.sscm.{domain}.{layer}`
  - application.yml: dev/prod 프로파일 분리
  - 공통 응답 클래스 `ApiResponse<T>`, 글로벌 예외 핸들러
  - `GET /api/v1/health` 헬스체크
- 완료 기준: `./gradlew build` 성공, Swagger UI 접속

**SSCM-4: Spring Security JWT 인증 구현**
- 유형: Story | Epic: 인증/인가 | 우선순위: Highest
- 왜 지금: 모든 기능이 로그인 후에 동작. 인증이 첫 번째 기능.
- 설명: API 스펙 `/docs/api/auth-api-spec.md` 기반
  - POST /api/v1/auth/signup (회원가입)
  - POST /api/v1/auth/login (로그인)
  - POST /api/v1/auth/refresh (토큰 갱신 + Rotation)
  - POST /api/v1/auth/logout (로그아웃 + Redis 블랙리스트)
  - GET /api/v1/auth/me (내 정보 조회)
  - JwtAuthenticationFilter, bcrypt(strength=12)
- 완료 기준: 5개 엔드포인트 동작, Swagger에서 테스트 가능, Redis 블랙리스트 동작
- 차단: SSCM-2 (User 테이블), SSCM-12 (Docker Compose로 Redis 구동)

**SSCM-5: 사용자 Role 구현 및 권한 분리**
- 유형: Story | Epic: 인증/인가 | 우선순위: Highest
- 왜 지금: 교사/학생/학부모 구분이 있어야 이후 기능 개발 가능
- 설명:
  - TEACHER, STUDENT, PARENT 3개 Role
  - Role별 회원가입 시 상세 테이블 자동 생성
  - 권한 매트릭스 `/docs/infra/architecture.md` 참조
- 완료 기준: 교사 토큰으로 교사 API 접근 가능, 학생 토큰으로 교사 API 접근 시 403
- 차단: SSCM-4

**SSCM-6: ADR 문서 리뷰 및 최종 커밋**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: Medium
- 왜 지금: 기술 스택 선정 근거를 팀 공유, "왜?" 문서화 습관
- 완료 기준: ADR-001~004 최종 리뷰, develop 머지

---

### 이프론트 담당 (5개)

**SSCM-7: React+TS+Vite 프로젝트 초기화**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: Highest
- 왜 지금: 프론트엔드 화면을 만들 기반이 필요
- 설명:
  - feature-based 폴더 구조
  - React Router DOM v7
  - Axios 인스턴스 (baseURL, JWT interceptor)
  - 환경변수 (.env.development, .env.production)
- 완료 기준: `npm run dev` 로컬 실행, `npm run build` 성공

**SSCM-8: ESLint(Airbnb) + Prettier 설정**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: High
- 왜 지금: 코드를 짜기 시작하니까 첫날부터 규칙을 정해야 나중에 안 싸움
- 완료 기준: `npm run lint` 에러 0건, .eslintrc.cjs + .prettierrc 커밋

**SSCM-9: 로그인 페이지 UI**
- 유형: Story | Epic: 인증/인가 | 우선순위: High
- 왜 지금: 백엔드 인증 API가 있으니 연동할 화면 필요
- 완료 기준: 로그인 폼 렌더링, API 연동, 에러 메시지 표시, 성공 시 토큰 저장+리다이렉트
- 차단: SSCM-7, SSCM-4

**SSCM-10: 회원가입 페이지 UI**
- 유형: Story | Epic: 인증/인가 | 우선순위: High
- 왜 지금: 사용자를 만들어야 로그인을 테스트할 수 있음
- 설명: Role 선택 → Role별 추가 정보 → 가입 완료
- 완료 기준: 3가지 Role별 가입 폼 동작, API 연동
- 차단: SSCM-7, SSCM-4

**SSCM-11: 라우팅 + 인증 가드 (ProtectedRoute)**
- 유형: Story | Epic: 인증/인가 | 우선순위: High
- 왜 지금: 로그인이 되니까 보호 페이지 접근 제어 필요
- 설명:
  - 공개 라우트: /login, /signup
  - 보호 라우트: /dashboard 등
  - Axios interceptor: 401 시 자동 토큰 갱신
- 완료 기준: 미인증 시 /login 리다이렉트, 인증 후 보호 라우트 접근
- 차단: SSCM-9, SSCM-10

---

### 이데브 담당 (2개)

**SSCM-12: Docker Compose 로컬 개발환경 (PostgreSQL + Redis)**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: Highest
- 왜 지금: 백엔드가 DB와 Redis에 연결하려면 로컬에서 띄워야 함
- 설명:
  - PostgreSQL 16 + 초기 DB/유저 생성
  - Redis 7
  - 볼륨 마운트, .env 파일
- 완료 기준: `docker-compose up -d` → PG + Redis 구동, Spring Boot 정상 접속

**SSCM-13: Redis 설정 (Spring Boot 연동)**
- 유형: Task | Epic: 프로젝트 셋업 | 우선순위: High
- 왜 지금: JWT 블랙리스트/Refresh Token 저장에 Redis 필요
- 설명:
  - spring-boot-starter-data-redis 의존성
  - Redis 키 설계: auth-api-spec.md 참조
  - application.yml Redis 접속 설정
- 완료 기준: Spring Boot에서 Redis 읽기/쓰기 테스트 통과
- 차단: SSCM-12

---

### 이큐에이 담당 (2개)

**SSCM-14: 인증 API Postman 컬렉션 작성**
- 유형: Task | Epic: 인증/인가 | 우선순위: Medium
- 왜 지금: 인증 API가 완성됐으니 체계적 테스트
- 설명:
  - 환경 변수: baseUrl, accessToken, refreshToken
  - 테스트 시나리오 7개 (auth-api-spec.md 참조)
- 완료 기준: Collection Runner 전체 통과
- 차단: SSCM-4

**SSCM-15: Postbot으로 엣지 케이스 테스트 생성**
- 유형: Task | Epic: 인증/인가 | 우선순위: Medium
- 왜 지금: Postman 컬렉션이 있으니 AI로 엣지 케이스 확장
- 설명: 경계값, SQL injection, XSS, 빈 문자열 등
- 완료 기준: 유효한 테스트 컬렉션에 추가
- 차단: SSCM-14

---

## 의존 관계

```
SSCM-1 (레포) ✅ 완료
    │
    ├→ SSCM-3 (Spring Boot) → SSCM-2 (ERD) ──┐
    │                                          ├→ SSCM-4 (JWT) → SSCM-5 (Role)
    ├→ SSCM-12 (Docker Compose) → SSCM-13 (Redis) ─┘
    │
    ├→ SSCM-7 (React) → SSCM-8 (ESLint)
    │                       │
    │                  SSCM-9 (로그인) → SSCM-10 (가입) → SSCM-11 (인증가드)
    │
    └→ SSCM-6 (ADR 리뷰)

SSCM-4 완성 → SSCM-14 (Postman) → SSCM-15 (Postbot)
```

---

## Sprint 0에서 하지 않는 것 (교수님 질문 대비)

| 작업 | 왜 안 하나? | 언제? |
|------|------------|-------|
| GitHub Actions CI | PR이 몇 개 안 됨. 로컬 빌드로 충분. | Sprint 1 |
| SonarCloud + JaCoCo | 분석할 코드가 적음. | Sprint 1 |
| Snyk | 초기 의존성은 안정적 버전. | Sprint 1 |
| Dockerfile | 배포할 곳이 없으니 이미지를 만들 이유 없음. | Sprint 2 |
| ECS Fargate + ALB | 앱도 이미지도 없는데 클라우드를 왜 띄우나. | Sprint 3 |
| Prometheus + Grafana / Dynatrace | 운영 환경이 없으니 모니터링할 대상 없음. | Sprint 4 |
