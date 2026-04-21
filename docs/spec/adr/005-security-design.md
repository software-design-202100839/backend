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

### Redis → PostgreSQL 이전 이유

| 항목 | Redis | PostgreSQL |
|------|-------|-----------|
| TTL 관리 | 자동 | @Scheduled 배치 (매일 03:00) |
| 인프라 | 별도 서버 | DB 재사용 |
| 규모 적합성 | 초당 수만 건 | 수백 명 학교 시스템에 충분 |
| 장애 지점 | 2개 (DB + Redis) | 1개 (DB만) |

---

## 3. 인가 (Authorization)

### 역할 기반 접근 제어

| 역할 | 접근 범위 |
|------|-----------|
| ADMIN | 전체 접근, 모든 데이터 조회·수정 |
| TEACHER | 교육 데이터 관리 (담당 범위 제한) |
| STUDENT | 본인 공개 데이터 조회만 |
| PARENT | 자녀 공개 데이터 조회만 |

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
