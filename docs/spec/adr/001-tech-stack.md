# ADR-001: 기술 스택 선정

- **상태:** 확정
- **작성일:** 2025-03-12
- **최종 수정:** 2026-03-27 (버전 확정 + 프론트엔드 라이브러리 구체화)
- **작성자:** 이백엔드 (PM)

## 맥락 (Context)
교사용 학생 성적·상담 관리 시스템을 개발한다. 핵심 요구사항은 다음과 같다:
- 교사/학생/학부모 간 **권한 분리** (Role-Based Access Control)
- 학생 **개인정보 암호화** 저장
- **실시간 알림** (성적/피드백 업데이트)
- **레이더 차트** 등 성적 시각화
- 다수 교사 **동시 접속** 성능
- **웹 + 모바일 반응형** 인터페이스
- 최종 **배포 및 운영**까지 포함

수업 2순위 기준으로 "상관없으면 Spring으로 진행"이라는 가이드라인이 있다.

## 결정 (Decision)

### Backend: Spring Boot 3.5.0 + Java 17

| 항목 | 선택 | 버전 |
|------|------|------|
| 언어 | Java | 17 (LTS) |
| 프레임워크 | Spring Boot | 3.5.0 |
| API 문서 | SpringDoc OpenAPI (Swagger) | 2.8.6 |
| DB 마이그레이션 | Flyway | 최신 stable |
| 보일러플레이트 | Lombok | 최신 stable |
| 실시간 알림 | Spring WebSocket (STOMP over SockJS) | - |
| 이메일 알림 | Spring Mail + @Async + @Retryable | - |
| PDF 생성 | OpenPDF | 최신 stable |
| Excel 생성 | Apache POI | 최신 stable |

**Spring Boot 3.5.0 선택 근거:**
- 3.5.0이 2025년 5월 GA 릴리스. 현재 가장 성숙한 stable 라인.
- 4.0은 Spring Framework 7 기반 메이저 업그레이드라 생태계 호환성 리스크.
- 3.5 오픈소스 지원 종료가 2026-06-30이므로 프로젝트 기간(~6월 초) 내 지원 범위.

**Java 21이 아닌 17을 쓰는 이유:**
- 21의 핵심 신기능인 Virtual Thread는 이 규모(수십~수백 동시 접속)에서 불필요.
- 17이 LTS로 가장 안정적이며, Spring Boot 3.5의 기본 지원 대상.

**보안:**
- **Spring Security:** JWT + RBAC를 네이티브로 지원. 교사/학생/학부모 3-Role 권한 분리에 최적.
- **`@PreAuthorize`** + JPA 쿼리 `WHERE` 조건으로 Row-Level Security 적용.
- **개인정보 암호화:** AES-256 + JPA `@Convert` + AttributeConverter로 민감 필드 자동 암호화.
- **비밀번호:** BCrypt 해싱 (Spring Security 기본 제공).

**보고서 생성:**
- **OpenPDF** (iText 2.x 포크, LGPL): iText 5+ AGPL(소스 공개 의무)을 회피.
- **Apache POI**: .xlsx 생성.
- JasperReports는 학습 곡선이 과하므로 사용하지 않음.

**이메일 비동기 전략:**
- `@Async`로 비동기 전송 + `@Retryable`로 실패 시 재시도.
- 이벤트 기반 아키텍처(ApplicationEvent → EventListener에서 메일 전송)로 관심사 분리.
- 메시지 큐(SQS)는 이 규모에서 과함.

### Frontend: React 19 + TypeScript + Vite 8

| 항목 | 선택 | 버전 |
|------|------|------|
| UI 라이브러리 | React | 19.2.4 |
| 빌드 도구 | Vite | 8.0.0 |
| 차트 | Recharts | 3.8.0 |
| 라우팅 | React Router DOM | v7 |
| HTTP 클라이언트 | Axios | 최신 stable |
| 상태 관리 (서버) | TanStack Query (React Query) | 최신 stable |
| 상태 관리 (클라이언트) | Zustand | 최신 stable |
| 폼 관리 | React Hook Form + Zod | 최신 stable |
| 린트/포맷 | ESLint 9 + Prettier | 최신 stable |
| 디자인 시스템 | shadcn/ui + Tailwind CSS | 최신 stable |
| 폰트 | Pretendard | - |
| API 타입 자동 생성 | openapi-typescript | 최신 stable |
| WebSocket | @stomp/stompjs + sockjs-client | 7.3.0 / 1.6.1 |

**React 19 선택 근거:**
- React 19는 정식 stable.
- Recharts 3.x, React Router v7, @stomp/stompjs 등 핵심 라이브러리의 React 19 호환성을 확인한 후 결정.
- "최신이라서"가 아닌 "호환성을 확인했기 때문에"가 근거.

**Vite 8 선택 근거:**
- CRA(Create React App)는 공식 deprecated.
- Turbopack은 사실상 Next.js 생태계 전용.
- Vite 8은 stable이며 개발/프로덕션 빌드 속도가 압도적.

**TanStack Query + Zustand 선택 근거:**
- SSCM은 대부분 "서버에서 데이터를 가져와서 보여주는" 앱. TanStack Query의 캐싱/리페칭/무효화가 핵심.
- 클라이언트 전용 상태(모달, 사이드바 토글 등)만 Zustand로 최소 관리.
- Redux 미사용: 보일러플레이트 과다, 복잡한 클라이언트 상태가 없음.
- Context API 미사용: Provider 중첩 시 리렌더링 성능 문제.

**React Hook Form + Zod 선택 근거:**
- 성적 입력 폼이 핵심 기능. React Hook Form은 비제어 컴포넌트 기반으로 리렌더링 최소화.
- Zod는 TypeScript 네이티브 스키마 검증. 프론트 폼 검증과 API 요청 검증을 동일 스키마로 처리.
- Formik 미사용: 제어 컴포넌트 기반이라 대규모 폼에서 성능 저하, 번들 사이즈 큼.

**openapi-typescript 선택 근거:**
- Swagger에서 OpenAPI spec 추출 → TypeScript 타입 자동 생성.
- 프론트-백 간 타입 불일치를 원천 차단. API 변경 시 수동 타입 수정 불필요.

**shadcn/ui + Tailwind CSS 선택 근거:**
- shadcn/ui는 복사 기반(copy-paste) 컴포넌트라 커스터마이징이 자유로움.
- Tailwind CSS는 유틸리티 퍼스트로 디자인 일관성 유지.
- MUI/Ant Design 미사용: 번들 사이즈 크고 커스터마이징 제한.

### Database: PostgreSQL 16 + Redis 7

**PostgreSQL 선택 근거:**
- 성적 데이터는 학생-과목-학기 관계가 명확 → RDBMS 자연스러움.
- JSONB: 학생부 "특기사항" 같은 비정형 데이터를 JSON으로 저장하면서도 SQL 검색/필터링 가능. MongoDB를 별도로 둘 필요 없음.
- CTE, Window Functions: 성적 통계/랭킹 쿼리에 MySQL보다 강력.
- MySQL 대비 trade-off: 초기 설정이 복잡하고 한국 커뮤니티가 작지만, AWS RDS가 둘 다 지원하므로 운영 난이도 차이 없음.

**Redis 용도 제한:**
- **사용**: JWT 블랙리스트 + Refresh Token 저장소.
- **사용하지 않음 (초기)**: 데이터 캐싱. k6 부하 테스트 후 DB 조회가 병목으로 확인되면 그때 `@Cacheable` 도입.
- **근거**: "측정 전 최적화(premature optimization)를 하지 않는다." 학교 시스템 트래픽에서 PostgreSQL만으로 충분할 가능성 높음.

### 실시간 알림: WebSocket (STOMP over SockJS)

**WebSocket > SSE 선택 근거:**
- 상담 내역 교사 간 공유 시 topic 기반 구독(`/topic/student/{id}`)이 자연스러움.
- 교사 간 실시간 협업(동일 학생 상담/피드백 동시 작업) 시나리오에 확장 가능.
- SSE는 서버→클라이언트 단방향에 적합하나, 다수의 구독 토픽 관리에는 WebSocket이 유리.
- Trade-off: WebSocket은 connection을 유지하므로 서버 리소스 추가 소모. SockJS가 자동 재연결 제공.

### 동시 접속 처리

- 중/고등학교 교사 동시 접속: 현실적으로 수십~수백 명.
- Spring Boot 기본 Tomcat: 200 스레드 동시 처리 가능. 단일 인스턴스로도 충분.
- ECS Fargate에서 태스크 수를 늘려 로드밸런싱 가능.
- **동시 수정 충돌**: JPA `@Version`을 활용한 Optimistic Locking.
- **증명**: k6 부하 테스트로 "동시 100명 접속 시 응답시간 X초 이내" 수치 확보.

## 대안 및 기각 사유

| 대안 | 기각 사유 |
|------|-----------|
| Node.js/Express (백엔드) | 수업 가이드라인에서 "상관없으면 Spring". 또한 인증/인가를 passport.js로 직접 조립해야 하여 Spring Security 대비 구현 비용 높음. |
| Spring Boot 4.0 | Spring Framework 7 기반 메이저 업그레이드로 호환성 리스크. 프로젝트 기간 내 안정성 우선. |
| Java 21 | Virtual Thread는 이 규모에서 불필요. 17이 LTS로 가장 안정적. |
| Next.js (프론트엔드) | SSR이 이 프로젝트에서 필수가 아님. 교사가 로그인 후 사용하는 SPA 구조에 React+Vite가 더 가벼움. |
| Redux (상태 관리) | 서버 상태 중심 앱에서 보일러플레이트 과다. TanStack Query 캐싱/리페칭이 핵심. |
| Formik (폼 관리) | 제어 컴포넌트 기반이라 대규모 폼에서 성능 저하, 번들 사이즈 큼. |
| MySQL (DB) | PostgreSQL의 JSONB + CTE/Window Functions가 성적 통계에 강점. |
| MongoDB (DB) | 성적 데이터의 관계형 특성(학생↔과목↔학기)이 명확하여 RDBMS가 자연스러움. |
| iText 5+ (PDF) | AGPL 라이센스(소스 공개 의무). OpenPDF는 LGPL로 자유로움. |

## Frontend 점진적 마이그레이션 전략 (2026-03-27 결정)

Sprint 0~2에서 구현된 기존 프론트엔드 코드(14개 파일)는 `useState` + Axios 직접 호출 + 인라인 스타일로 **정상 동작 중**이다.

**결정: 기존 코드는 현행 유지, 신규 기능에만 새 스택 적용.**

- Sprint 3 보고서 UI(신규)부터 TanStack Query + React Hook Form + Zod + shadcn/ui + Tailwind CSS 적용
- 기존 14개 페이지는 안정성 보존을 위해 리팩터링하지 않음
- 동일 앱에서 두 패턴이 공존하지만, 실무에서도 점진적 마이그레이션은 정석 전략

**근거:**
- "동작하는 코드를 뜯으면 Sprint 2 목표(안정적 동작 + 증명)가 깨진다"
- 교수님 질문 시: "기존 기능 안정성 보존을 위해 점진적 마이그레이션 전략을 취했습니다. 신규 기능(보고서)부터 새 스택을 적용하여 검증한 뒤, 향후 확장 시 기존 코드도 전환할 수 있습니다."

## 결과 (Consequences)
- Spring Boot 생태계에 의존하므로, 팀원 모두 Java/Spring 학습이 필요.
- TypeScript 도입으로 초기 설정 비용이 순수 JS 대비 높으나, 장기적 유지보수성 확보.
- PostgreSQL + Redis 이중 구조로 인프라 복잡도 증가하나, JWT 블랙리스트 처리에 필수.
- TanStack Query + Zustand 조합으로 상태 관리 복잡도 최소화 (신규 코드부터 적용).
- openapi-typescript로 프론트-백 타입 동기화 자동화.
- Frontend 점진적 마이그레이션으로 기존 기능 안정성 보존.
