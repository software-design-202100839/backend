# 보안 설계 문서

- **작성일:** 2026-04-20
- **작성자:** 이백엔드 + 이큐에이
- **대상:** Sprint 6 전면 재설계 반영

---

## 1. 접근 제어 (Controlled Onboarding)

### 원칙

> "Admin이 사전 등록한 전화번호로만 계정을 활성화할 수 있다"

자유 회원가입을 허용하면 역할 선택만으로 권한을 취득할 수 있어 학교 시스템의 특성상 허용 불가.

### 활성화 흐름

```
Admin DB 등록 (이름 + 전화번호)
    ↓
[사용자] 전화번호 입력
    ↓
phone_hash 조회 → 미등록이면 동일 응답 반환 (열거 공격 방지)
    ↓
Solapi SMS OTP 발송 (6자리, 5분 만료)
    ↓
OTP 입력 → 5회 초과 시 폐기, 재발급 필요
    ↓
이메일(로그인 ID) + 비밀번호 직접 설정
    ↓
계정 활성화 완료
```

### 보안 포인트

| 항목 | 내용 |
|------|------|
| 미등록 번호 처리 | 200 OK + 동일 메시지 반환 (존재 여부 노출 차단) |
| OTP 만료 | 5분 |
| OTP 실패 제한 | 5회 초과 시 즉시 폐기 → 재발급 필요 |
| Zero-knowledge | Admin 포함 누구도 비밀번호를 알 수 없음 |
| 전화번호 변경 | 오프라인 요청만 허용 (사용자 자의적 변경 불가) |

---

## 2. 인증 (Authentication)

### 비밀번호

- **알고리즘:** bcrypt, strength=12
- **평문 저장 금지:** 설정, 로그, DB 어디에도 저장하지 않음
- **변경:** 로그인 후 /auth/me → 비밀번호 변경, 또는 SMS OTP 찾기 흐름

### 로그인 잠금

```
로그인 실패 5회 → login_locked_until = NOW() + 30분
잠금 중 요청 → 423 Locked, 잠금 해제까지 남은 시간 안내
성공 시 → failed_login_count = 0 초기화
```

### JWT 토큰

| 항목 | 값 |
|------|-----|
| Access Token 만료 | 30분 |
| Refresh Token 만료 | 7일 |
| 서명 알고리즘 | HS256 |
| 저장 위치 | AT: 클라이언트 메모리, RT: PostgreSQL |

### Refresh Token Rotation

```
RT 갱신 요청 → 기존 RT 즉시 삭제 → 새 AT + 새 RT 발급
탈취된 RT 재사용 시 → DB에 없음 → 401 반환
```

### 로그아웃

```
AT → token_blacklist에 추가 (잔여 만료시간 = expires_at)
RT → refresh_tokens에서 삭제
→ 이후 해당 AT로 요청 시 블랙리스트 확인 후 거부
```

### Redis L1 캐시 + DB L2 Fallback (현재 상태, 2026-05-27)

초기에는 Redis를 제거하고 PostgreSQL만 사용했으나, AWS 풀 아키텍처 배포 후 다중 인스턴스 환경에서 블랙리스트 공유 문제와 커넥션 풀 고갈 리스크가 확인되어 Redis를 재도입했다.

**현재 구조: TokenBlacklistService**
```
JWT 블랙리스트 확인 요청
    ↓
Redis L1 캐시 조회 (ElastiCache)
    ↓ cache miss
PostgreSQL L2 fallback 조회
    ↓ DB에 존재하면
Redis에 캐싱 후 반환
```

| 계층 | 역할 | TTL |
|------|------|-----|
| Redis (L1) | 블랙리스트 빠른 조회, 다중 인스턴스 공유 | AT 잔여 만료시간 |
| PostgreSQL (L2) | 영속 저장, Redis 장애 시 fallback | @Scheduled 배치 정리 (매일 03:00) |

**Micrometer 메트릭 추적:**
- `token.blacklist.cache.hit` — Redis 캐시 히트 수
- `token.blacklist.cache.miss` — Redis 미스 → DB fallback 수
- Grafana 대시보드에서 캐시 효율 모니터링 가능

### Redis 이전/재도입 히스토리

| 시점 | 결정 | 이유 |
|------|------|------|
| Sprint 6 초기 | Redis → PostgreSQL 이전 | 인프라 단순화 (단일 인스턴스 기준 충분) |
| AWS 배포 후 | Redis 재도입 (L1 캐시) | 다중 인스턴스 블랙리스트 공유 필요, 동시 1000+ 요청 시 DB 커넥션 풀 고갈 방지 |

---

## 2-1. 멀티테넌시 격리 (Multi-Tenancy Isolation)

### 원칙

> "교사/관리자는 자신이 소속된 학교의 데이터만 접근할 수 있다. 교차 학교 접근은 모든 경우에 차단한다."

### JWT schoolId Claim

로그인 시 JWT Access Token에 `schoolId` 클레임을 포함하여 발급한다. 모든 API 요청에서 학교 컨텍스트를 식별하는 데 사용한다.

```json
{
  "sub": "user-uuid",
  "role": "TEACHER",
  "schoolId": 3,
  "exp": 1748300000
}
```

### TenantContext (ThreadLocal)

```
JWT 파싱 → schoolId 추출
    ↓
TenantContext.set(schoolId)  // ThreadLocal에 저장
    ↓
서비스 레이어에서 TenantContext.get()으로 학교 컨텍스트 조회
    ↓
요청 완료 후 TenantContext.clear()  // 메모리 누수 방지
```

### 서비스별 학교 경계 검증

| 서비스 | 생성 시 | 조회 시 |
|--------|---------|---------|
| ScoreService | 학생의 schoolId와 요청자 schoolId 일치 확인 | 본인 학교 데이터만 반환 |
| FeedbackService | 동일 | 동일 |
| CounselingService | 동일 | 동일 |
| StudentRecordService | 동일 | 동일 |

### AnalyticsAccessChecker

TEACHER/ADMIN 역할의 분석 데이터 접근 시 학교 경계를 검증한다.

```
분석 API 요청 (TEACHER, schoolId=3)
    ↓
AnalyticsAccessChecker.validate(requestSchoolId, userSchoolId)
    ↓
schoolId 불일치 → 403 Forbidden
```

### AI 챗봇 역할별 도구 제한

| 역할 | 접근 가능 도구 수 | 설명 |
|------|-------------------|------|
| TEACHER | 13개 | 성적 조회/분석, 피드백 관리, 상담 내역, 학생부, 통계 등 전체 교육 도구 |
| STUDENT | 5개 | 본인 성적 조회, 본인 피드백 조회 등 제한된 도구 |
| PARENT | 5개 | 자녀 성적 조회, 자녀 피드백 조회 등 제한된 도구 |

---

## 3. 인가 (Authorization)

### 역할 기반 접근 제어

| 역할 | 접근 범위 | 학교 격리 |
|------|-----------|-----------|
| ADMIN | 전체 접근, 모든 데이터 조회·수정 | 본인 학교만 (schoolId 검증) |
| TEACHER | 교육 데이터 관리 (담당 범위 제한) | 본인 학교만 (schoolId 검증) |
| STUDENT | 본인 공개 데이터 조회만 | 본인 학교 (암묵적) |
| PARENT | 자녀 공개 데이터 조회만 | 자녀 학교 (암묵적) |

### 교사 권한 체크 (학년도 기반)

```
성적 수정 요청 (teacher_id=3, student_id=1, subject_id=2, year=2026)
    ↓
student_enrollments에서 student_id=1의 class_id 조회 (year=2026)
    ↓
teacher_assignments에서
  (teacher_id=3, class_id=N, subject_id=2, academic_year=2026) 존재 여부
    ↓
없으면 403 Forbidden
```

```
학생부(기본) 수정 요청 (teacher_id=5, student_id=1, academicYear=2026)
    ↓
student_enrollments에서 class_id 조회
    ↓
classes에서 homeroom_teacher_id = 5 확인
    ↓
불일치하면 403 Forbidden
```

### 학생·학부모 데이터 접근

```
STUDENT 성적 조회:
  JWT sub → user_id → students.user_id 확인 (본인인지)
  → 본인 성적만 반환

PARENT 자녀 성적 조회:
  JWT sub → user_id → parents.user_id → parent_student.student_id
  → 연결된 자녀 목록 중 요청 student_id 포함 여부 확인
  → 포함되지 않으면 403

공개 여부 필터:
  is_visible_to_student = true 인 것만 학생에게 반환
  is_visible_to_parent = true 인 것만 학부모에게 반환
```

---

## 4. 데이터 암호화

### 컬럼 레벨 암호화 (AES-256-GCM)

| 컬럼 | 이유 |
|------|------|
| users.email | PII — 로그인 ID |
| users.phone | PII — SMS 수신 번호 |
| counselings.content | 가정환경·심리 상태 등 민감 상담 내용 |
| counselings.next_plan | 동일 |

**AES-256-GCM 특성:**
- 비결정적 암호화 (같은 값도 매번 다른 암호문) → 암호문 비교 불가
- GCM 인증 태그 → 변조 감지 가능
- 키: 환경변수로 주입, 코드에 하드코딩 금지

### Blind Index (SHA-256)

- `users.email_hash`: 로그인 조회, 중복 가입 차단
- `users.phone_hash`: OTP 발송 대상 확인

### 암호화 대상에서 제외한 이유

| 컬럼 | 제외 이유 |
|------|-----------|
| users.name | 이름 검색 필요 |
| scores.score | AVG, RANK, SUM 등 SQL 집계 필요 |
| feedbacks.content | 내용 검색 필요 |
| student_records.content (JSONB) | JSONB GIN 인덱스 쿼리 필요 |

> 성적을 암호화하면 `SELECT AVG(score) GROUP BY ...`, `RANK() OVER (...)` 등 집계 쿼리가 전부 불가능해진다. SQL 집계를 앱 레이어로 옮기면 N+1 문제와 성능 저하가 발생한다.

---

## 5. SMS 알림 보안

### 원칙

> SMS 메시지 본문에 민감정보를 포함하지 않는다

| 허용 | 금지 |
|------|------|
| "성적이 업데이트되었습니다" | "수학 85점이 입력되었습니다" |
| "피드백이 등록되었습니다" | "행동 피드백: 수업 태도 불량" |
| "새 상담 내역이 등록되었습니다" | 상담 내용 요약 |

**이유:** SMS는 이동통신사 서버를 경유. 수신자 외 제3자가 볼 수 있는 채널. 민감정보는 앱 로그인 후 확인 유도.

---

## 6. 데이터 무결성

### 삭제 정책

> 성적, 학생부, 피드백, 상담 내역은 삭제 불가. 수정만 허용.

- 법적 보존 의무 (교육 기록)
- 이력 추적 필요 (누가 언제 어떤 데이터를 입력했는지)

### 낙관적 락 (Optimistic Locking)

동시 수정으로 인한 lost-update 방지.

| 테이블 | version 컬럼 |
|--------|-------------|
| scores | O |
| feedbacks | O |
| counselings | O |
| student_records | O |

```
수정 요청 시 version 필드 포함 필수
→ DB의 version과 불일치 시 409 (COMMON_004)
→ 클라이언트는 재조회 후 최신 version으로 재시도
```

### Audit Logs

모든 교육 데이터 변경 시 audit_logs에 기록:
- 변경된 테이블, 레코드 ID, 필드명
- 변경 전/후 값
- 수정자 (changed_by), 수정 시각

ADMIN 예외 수정도 동일하게 기록 → "누가 예외 수정했는지" 추적 가능.

---

## 7. 발표용 요약

### 보안 설계 핵심 3가지

**① 폐쇄형 접근**
> "사전 등록된 전화번호 + SMS OTP로만 계정을 활성화한다. 자유 회원가입 없음."

**② 책임 기반 권한**
> "조회는 전체 교사에게 공유하되, 수정 권한은 담당 교사·담임·작성자로 제한한다. 권한 체크는 teacher_assignments 테이블 하나로 통일."

**③ 선택적 암호화**
> "민감한 상담 내용과 PII(연락처, 이메일)만 AES-256-GCM으로 암호화한다. 성적·이름을 암호화하면 SQL 집계가 불가능하므로 의도적으로 제외했다."
