# ADR-006: 다중 학교 멀티테넌시 설계

- **상태:** 채택
- **작성일:** 2026-05-27
- **결정자:** 이승희

---

## 맥락

SSCM은 다중 학교/SaaS 확장을 전제로 인프라를 설계(Kafka, ECS Auto Scaling, Redis, OLAP 분리, CloudFront CDN)했지만, 코드 레벨에서는 School 개념이 없었다.

**문제점:**
- 교사가 아무 학생의 데이터에 접근 가능 (교차 학교 데이터 유출)
- Kafka 이벤트에 학교 컨텍스트 없음 (분석 데이터 혼재)
- 관리자가 모든 학교의 유저를 볼 수 있음

→ 인프라 설계와 코드의 괴리를 해소하고, 다중 학교 격리를 실제로 구현.

---

## 결정

### 테넌트 전략: Shared-Database, Discriminator-Column

| 전략 | 장점 | 단점 | 채택 |
|------|------|------|:----:|
| **Database-per-tenant** | 완전 격리, RLS 불필요 | 학교 수 × DB 인스턴스 비용, 마이그레이션 복잡 | ✗ |
| **Schema-per-tenant** | 격리 + 단일 DB | 동적 스키마 관리 복잡, 커넥션 풀 공유 문제 | ✗ |
| **Shared-DB + Discriminator Column** | 단순, 비용 효율, 기존 코드 최소 변경 | 쿼리마다 WHERE school_id 필요 | ✓ |

**선택 이유:**
- 학교 수가 수십~수백 수준에서 DB-per-tenant은 비용 비현실적
- 기존 스키마에 school_id FK만 추가하면 되므로 마이그레이션이 단순
- TenantContext(ThreadLocal)로 서비스 메서드 시그니처 변경 없이 학교 전파 가능

### school_id 배치: users + classes만 (정규화 원칙)

| 테이블 | school_id | 이유 |
|--------|:---------:|------|
| **users** | ✓ 직접 | 모든 사용자는 하나의 학교에 소속 |
| **classes** | ✓ 직접 | AdminService에서 사용자 컨텍스트 없이 직접 생성 |
| scores | ✗ | student → user → school FK 체인으로 추론 |
| feedbacks | ✗ | 동일 |
| counselings | ✗ | 동일 |
| student_records | ✗ | 동일 |
| subjects | ✗ | 전역 마스터 데이터 (학교 공통) |
| analytics 집계 테이블 | ✓ 직접 | 역정규화 — 학교별 필터링 성능을 위해 |

**왜 scores/feedbacks에 school_id를 안 넣었는가:**
> "역정규화는 성능 병목이 실측될 때 하는 것이지, 미리 하는 것이 아닙니다. 현재 FK 체인(student → user → school)으로 추론이 가능하고, 쿼리 성능 문제가 발생하면 그때 school_id를 추가합니다."

---

## 구현 구조

### 요청 흐름

```
클라이언트 → JWT (schoolId claim 포함)
         → JwtAuthenticationFilter
              ├─ SecurityContext에 userId, role 설정
              └─ TenantContext.setSchoolId(schoolId) ← ThreadLocal
         → Controller
         → Service (TenantContext.requireSchoolId()로 학교 확인)
         → Repository (학교별 필터링 쿼리)
         → finally: TenantContext.clear()
```

### 격리 포인트

| 계층 | 격리 방법 |
|------|----------|
| **JWT** | schoolId claim — 토큰 발급 시 user.school.id 포함 |
| **서비스** | TenantContext.requireSchoolId() — 학생 조회 시 school 일치 검증 |
| **Admin** | getCurrentSchool() — 유저/클래스 생성 시 학교 자동 할당 |
| **Analytics** | AnalyticsAccessChecker — TEACHER/ADMIN도 교차 학교 차단 |
| **Kafka** | 이벤트 페이로드에 schoolId 포함 → Consumer가 집계 테이블에 저장 |
| **OLAP** | getAllSubjectStatistics()에 school_id WHERE절 |

---

## 대안 비교: TenantContext(ThreadLocal) vs 파라미터 전달

| 방식 | 장점 | 단점 |
|------|------|------|
| **ThreadLocal (채택)** | 메서드 시그니처 변경 불필요, 서비스 레이어 투명 | 비동기/스레드 풀에서 전파 필요 |
| 파라미터 전달 | 명시적, 추적 용이 | 모든 서비스 메서드에 schoolId 파라미터 추가 필요 |

**선택 이유:** 기존 서비스 메서드 시그니처를 유지하면서 30+ 메서드에 학교 컨텍스트를 전파할 수 있음. @Async 이벤트는 TenantContext.getSchoolId()를 이벤트 페이로드에 담아서 해결.

---

## 보안 검증 시나리오

| 시나리오 | 기대 결과 |
|----------|----------|
| 한빛중 교사 JWT로 새별중 학생 성적 조회 | 403 ACCESS_DENIED |
| 한빛중 교사 JWT로 한빛중 학생 성적 등록 | 200 OK |
| 한빛중 교사 JWT로 새별중 학생 피드백 작성 | 403 ACCESS_DENIED |
| 한빛중 관리자 JWT로 새별중 분석 대시보드 조회 | 403 ACCESS_DENIED |
| 한빛중 관리자 JWT로 교사 목록 조회 | 한빛중 교사만 반환 |
| 과목별 통계 조회 | 해당 학교 데이터만 집계 |

---

## 수정 범위

### 신규 파일 (4개)
- V9 마이그레이션, School 엔티티, SchoolRepository, TenantContext

### 수정 파일 (20+)
- User/ClassRoom 엔티티 (school FK)
- JWT 발급/검증/필터 (schoolId claim + TenantContext)
- AdminService (테넌트 스코핑)
- AnalyticsAccessChecker (교차 학교 차단)
- Score/Feedback/Counseling/StudentRecord Service (생성 시 학교 검증)
- Analytics 이벤트/Consumer/Repository/Dashboard/DataLoader (schoolId 전파)
- 테스트 파일 11개 (School mock + TenantContext 설정)

### 변경하지 않은 것
- Subject 테이블 — 전역 마스터 데이터
- TokenBlacklist/RefreshToken — 토큰 무효화는 전역
- 기존 엔티티 관계 구조 — FK 체인 유지
- Kafka 토픽 구조 — 같은 토픽, 페이로드에 schoolId 추가

---

## 성장 경로

| 단계 | 규모 | 인프라 변경 |
|------|------|-----------|
| MVP (현재) | 1~2개 학교 | 현재 그대로 |
| Phase 1 | 10개 학교 | 커넥션 풀 확대 (20→100), ECS 태스크 증가 |
| Phase 2 | 100개 학교 | RDS Read Replica, Kafka 파티션 증가, Redis Cluster |
| Phase 3 | 1000+ | Database-per-tenant 전환 검토 |
