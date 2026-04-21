# ERD 설계: 학생 성적 및 상담 관리 시스템

- **상태:** 확정 (설계 전면 재검토 반영)
- **최초 작성:** 2025-03-12
- **최종 수정:** 2026-04-20 (설계 전면 재검토 — 권한 모델, 학년도 이력, 인증 재설계)
- **작성자:** 이백엔드 (PM)
- **리뷰:** 이큐에이 (보안 관점 검토)

> **변경 이력**
> - V1 (Sprint 0): 초기 스키마
> - V2 (SSCM-55): users.email → TEXT + email_hash, users.phone → TEXT, counselings AES-256 암호화
> - V3 (SSCM-58): scores, feedbacks, counselings, student_records에 낙관적 락 version 컬럼
> - **V4 (Sprint 6):** Redis → PostgreSQL 토큰 테이블, 인증 재설계 (Controlled Onboarding + SMS OTP)
> - **V5 (Sprint 6):** ADMIN 역할 추가, 로그인 잠금 필드
> - **V6 (Sprint 6):** invite_tokens (SMS OTP), audit_logs
> - **V7 (Sprint 6):** classes, student_enrollments, teacher_assignments (학년도 기반 권한 모델), student_records 재설계

---

## 설계 원칙

1. **폐쇄형 시스템**: 자유 회원가입 없음. Admin이 사전 등록한 전화번호로만 계정 활성화 가능
2. **학년도 기반 이력**: 담임·담당 교사 관계는 학년도별로 관리. 학생은 고정 엔티티, 소속/번호는 이력
3. **조회는 공유, 수정은 책임**: 교사 간 조회 공유, 수정은 담당/작성자로 제한
4. **삭제 불가**: 교육 데이터는 수정+이력 관리. audit_logs로 변경 추적
5. **암호화 최소화 원칙**: 집계/검색 필요 데이터는 평문. 민감 PII·상담내용만 AES-256-GCM
6. **정규화 우선**: 3NF 기준. 성적 집계(평균·석차)는 조회 시 앱 레벨 계산

---

## ERD 다이어그램

```
★ = AES-256-GCM 암호화 컬럼
# = SHA-256 blind index (조회/UNIQUE용)
⓪ = 낙관적 락 version 컬럼

┌─────────────────────────────────┐
│              users              │
├─────────────────────────────────┤
│ PK  id                          │
│     email ★                    │
│     email_hash # (UNIQUE)       │
│     password_hash (bcrypt)      │
│     name                        │
│     phone ★                    │
│     phone_hash # (UNIQUE)       │  ← 신규: OTP 발송 대상 확인용
│     role (ADMIN/TEACHER/        │  ← ADMIN 추가
│           STUDENT/PARENT)       │
│     is_active                   │
│     failed_login_count          │  ← 신규: 로그인 잠금 카운터
│     login_locked_until          │  ← 신규: 잠금 해제 시각
│     created_at                  │
│     updated_at                  │
│     deleted_at                  │
└───┬──────┬─────┬────────────────┘
    │      │     │
    ▼      ▼     ▼
┌────────┐ ┌─────────┐ ┌────────┐
│teachers│ │students │ │parents │
├────────┤ ├─────────┤ ├────────┤
│PK id   │ │PK id    │ │PK id   │
│FK user │ │FK user  │ │FK user │
│dept    │ │admit_yr │ └───┬────┘
└───┬────┘ └────┬────┘     │
    │           │          ▼
    │           │   ┌──────────────┐
    │           │   │parent_student│
    │           │   ├──────────────┤
    │           │   │FK parent_id  │
    │           │   │FK student_id │
    │           │   │relationship  │
    │           │   └──────────────┘
    │           │
    ▼           ▼
┌───────────────────────────────────────┐
│              classes                  │  ← 신규
├───────────────────────────────────────┤
│ PK  id                                │
│     academic_year                     │
│     grade                             │
│     class_num                         │
│ FK  homeroom_teacher_id → teachers    │
│     created_at                        │
│ UNIQUE(academic_year, grade, class_num)│
└───────────┬───────────────────────────┘
            │
    ┌───────┴──────────────────────┐
    ▼                              ▼
┌──────────────────────┐  ┌─────────────────────────┐
│  student_enrollments │  │   teacher_assignments    │  ← 신규
├──────────────────────┤  ├─────────────────────────┤
│ PK  id               │  │ PK  id                  │
│ FK  student_id       │  │ FK  teacher_id          │
│ FK  class_id         │  │ FK  class_id            │
│     student_num      │  │ FK  subject_id          │
│     academic_year    │  │     academic_year        │
│     created_at       │  │     created_at           │
│ UNIQUE(student_id,   │  │ UNIQUE(teacher_id,       │
│        academic_year)│  │        class_id,         │
└──────────────────────┘  │        subject_id,       │
                          │        academic_year)     │
                          └─────────────────────────┘

┌──────────────────────────────────────┐
│              subjects                │
├──────────────────────────────────────┤
│ PK  id                               │
│     name                             │
│     code (UNIQUE)                    │
│     description                      │
│     created_at                       │
└──────────────────────────────────────┘

┌─────────────────────────────────────────┐
│                 scores                  │
├─────────────────────────────────────────┤
│ PK  id                                  │
│ FK  student_id → students               │
│ FK  subject_id → subjects               │
│ FK  teacher_id → teachers (입력 교사)   │
│     year, semester                      │
│     score DECIMAL(5,2)                  │
│     grade_letter (자동계산)              │
│     rank (자동계산)                      │
│     created_at, updated_at              │
│     created_by, updated_by → users      │
│     version ⓪                          │
│ UNIQUE(student_id, subject_id,          │
│        year, semester)                  │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│             student_records             │  ← 수정
├─────────────────────────────────────────┤
│ PK  id                                  │
│ FK  student_id → students               │
│ FK  subject_id → subjects (세특만 사용) │  ← 신규
│     academic_year                       │
│     semester                            │
│     record_type (BASIC / SPECIAL)       │  ← 신규
│     category                            │
│     content JSONB                       │
│     is_visible_to_student               │  ← 신규 (담임 설정)
│     is_visible_to_parent                │  ← 신규 (담임 설정)
│     review_status                       │  ← 신규 (담임 검토 상태)
│     created_at, updated_at              │
│     created_by, updated_by → users      │
│     version ⓪                          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│               feedbacks                 │
├─────────────────────────────────────────┤
│ PK  id                                  │
│ FK  student_id → students               │
│ FK  teacher_id → teachers               │
│     category                            │
│     content TEXT (평문)                  │
│     is_visible_to_student               │
│     is_visible_to_parent                │
│     created_at, updated_at              │
│     version ⓪                          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│              counselings                │
├─────────────────────────────────────────┤
│ PK  id                                  │
│ FK  student_id → students               │
│ FK  teacher_id → teachers               │
│     counsel_date                        │
│     category                            │
│     content ★ (상담내용 AES-256-GCM)    │
│     next_plan ★                         │
│     next_counsel_date                   │
│     (is_shared 제거 — 전체 교사 공유)   │  ← 제거
│     created_at, updated_at              │
│     version ⓪                          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│             notifications               │
├─────────────────────────────────────────┤
│ PK  id                                  │
│ FK  recipient_id → users                │
│     type                                │
│     title                               │
│     message (민감정보 미포함)            │
│     reference_type                      │
│     reference_id                        │
│     is_read                             │
│     created_at                          │
└─────────────────────────────────────────┘

── 인증/보안 테이블 ──────────────────────

┌─────────────────────────────────────────┐
│             invite_tokens               │  ← 신규 (SMS OTP)
├─────────────────────────────────────────┤
│ PK  id                                  │
│     phone_hash VARCHAR(64) (SHA-256)    │
│     otp_code VARCHAR(6)                 │
│     purpose (ACTIVATE / PW_RESET)       │
│     expires_at TIMESTAMPTZ             │
│     used_at TIMESTAMPTZ                │
│     attempt_count INTEGER DEFAULT 0    │
│     created_at                          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│             refresh_tokens              │  ← 신규 (Redis 대체)
├─────────────────────────────────────────┤
│ PK  id                                  │
│ FK  user_id → users ON DELETE CASCADE   │
│     token_hash VARCHAR(64) UNIQUE       │
│     expires_at TIMESTAMPTZ             │
│     created_at                          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│             token_blacklist             │  ← 신규 (Redis 대체)
├─────────────────────────────────────────┤
│ PK  id                                  │
│     token_hash VARCHAR(64) UNIQUE       │
│     expires_at TIMESTAMPTZ             │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│               audit_logs               │  ← 신규
├─────────────────────────────────────────┤
│ PK  id                                  │
│     table_name VARCHAR(50)              │
│     record_id BIGINT                    │
│     field_name VARCHAR(100)             │
│     old_value TEXT                      │
│     new_value TEXT                      │
│ FK  changed_by → users (NULL=시스템)    │
│     changed_at TIMESTAMPTZ             │
└─────────────────────────────────────────┘
```

---

## 엔티티 상세 설명

### 1. users

모든 사용자 공통 정보.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| email | TEXT | NOT NULL | 로그인 ID (사용자가 활성화 시 직접 설정) ★AES-256-GCM |
| email_hash | VARCHAR(64) | UNIQUE NOT NULL | SHA-256 blind index — 로그인/중복 조회용 |
| password_hash | VARCHAR(255) | NOT NULL | bcrypt(12) |
| name | VARCHAR(100) | NOT NULL | 평문 (검색 필요) |
| phone | TEXT | NOT NULL | 사전 등록 연락처 ★AES-256-GCM |
| phone_hash | VARCHAR(64) | UNIQUE NOT NULL | SHA-256 — OTP 발송 대상 확인용 |
| role | VARCHAR(20) | NOT NULL | ADMIN / TEACHER / STUDENT / PARENT |
| is_active | BOOLEAN | DEFAULT true | 계정 활성 상태 |
| failed_login_count | INTEGER | DEFAULT 0 | 로그인 실패 횟수 (5회 초과 시 잠금) |
| login_locked_until | TIMESTAMPTZ | | 잠금 해제 시각 (30분 잠금) |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | 트리거 자동 갱신 |
| deleted_at | TIMESTAMPTZ | | Soft Delete |

**인덱스:** email_hash(UNIQUE), phone_hash(UNIQUE), role, deleted_at

> **phone_hash 추가 이유:** 활성화 화면에서 사용자가 전화번호를 입력하면 phone_hash로 조회해 사전 등록 여부 확인. 평문 phone은 암호화되어 있어 직접 조회 불가.

---

### 2. teachers

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK→users UNIQUE | 1:1 |
| department | VARCHAR(50) | | 담당 교과 (예: 수학) |

> **homeroom_grade, homeroom_class 제거:** 학년도별 담임 관계는 `classes.homeroom_teacher_id`로 관리

---

### 3. students

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK→users UNIQUE | 1:1 |
| admission_year | INTEGER | NOT NULL | 입학년도 (학생 기본 정보) |

> **grade, class_num, student_num 제거:** 학년도별 소속은 `student_enrollments`로 관리

---

### 4. parents

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK→users UNIQUE | 1:1 |

---

### 5. parent_student

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| parent_id | BIGINT | FK→parents | 복합 PK |
| student_id | BIGINT | FK→students | 복합 PK |
| relationship | VARCHAR(20) | NOT NULL | FATHER / MOTHER / GUARDIAN |

> ADMIN이 사전 등록 시 확정. 사용자가 직접 연결 불가.

---

### 6. classes (신규)

학년도별 반 정보. 담임-반 관계의 단일 진실 공급원.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| academic_year | INTEGER | NOT NULL | 학년도 (예: 2026) |
| grade | INTEGER | NOT NULL CHECK(1~3) | 학년 |
| class_num | INTEGER | NOT NULL | 반 |
| homeroom_teacher_id | BIGINT | FK→teachers | 담임 교사 |
| created_at | TIMESTAMPTZ | NOT NULL | |

**UNIQUE:** (academic_year, grade, class_num)

> 학년도가 바뀌면 새 레코드 생성. 이전 학년도 데이터는 그대로 유지 → 과거 담임 이력 추적 가능.

---

### 7. student_enrollments (신규)

학생의 학년도별 소속 반 및 번호.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK→students NOT NULL | |
| class_id | BIGINT | FK→classes NOT NULL | |
| student_num | INTEGER | NOT NULL | 학번 (학년도별 변경 가능) |
| academic_year | INTEGER | NOT NULL | 중복 저장 (조회 편의) |
| created_at | TIMESTAMPTZ | NOT NULL | |

**UNIQUE:** (student_id, academic_year) — 학생은 학년도당 1개 반만

---

### 8. teacher_assignments (신규)

교사의 학년도별 담당 반+과목. 권한 체크의 근거.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| teacher_id | BIGINT | FK→teachers NOT NULL | |
| class_id | BIGINT | FK→classes NOT NULL | |
| subject_id | BIGINT | FK→subjects NOT NULL | |
| academic_year | INTEGER | NOT NULL | 중복 저장 (조회 편의) |
| created_at | TIMESTAMPTZ | NOT NULL | |

**UNIQUE:** (teacher_id, class_id, subject_id, academic_year)

> **권한 체크 예시:** 교사가 학생의 수학 성적을 입력하려 할 때 →  
> `teacher_assignments`에서 (teacher_id, 해당 학생의 class_id, 수학 subject_id, 현재 학년도) 조합이 존재하는지 확인.

---

### 9. subjects

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL PK | |
| name | VARCHAR(100) | 과목명 |
| code | VARCHAR(20) UNIQUE | 과목 코드 |
| description | TEXT | |
| created_at | TIMESTAMPTZ | |

기본 데이터: 국어, 수학, 영어, 사회, 과학, 역사, 도덕, 체육, 음악, 미술, 기술·가정, 정보

---

### 10. scores

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK→students | |
| subject_id | BIGINT | FK→subjects | |
| teacher_id | BIGINT | FK→teachers | 입력 교사 (감사 추적용) |
| year | INTEGER | NOT NULL | |
| semester | INTEGER | NOT NULL CHECK(1,2) | |
| score | DECIMAL(5,2) | NOT NULL CHECK(0~100) | 평문 유지 (SQL 집계 필요) |
| grade_letter | VARCHAR(5) | | 자동 계산 (A+~F) |
| rank | INTEGER | | 자동 계산 |
| created_at, updated_at | TIMESTAMPTZ | | |
| created_by, updated_by | BIGINT | FK→users | 감사 추적 |
| version | BIGINT | DEFAULT 0 | 낙관적 락 ⓪ |

**UNIQUE:** (student_id, subject_id, year, semester)

**수정 권한 체크:** `teacher_assignments`에서 교사가 해당 학생 반의 해당 과목 담당인지 확인

---

### 11. student_records (수정)

기본 학생부(담임)와 세특(교과교사)을 단일 테이블로 관리. `record_type`으로 구분.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK→students | |
| subject_id | BIGINT | FK→subjects NULLABLE | 세특만 사용. BASIC은 NULL |
| academic_year | INTEGER | NOT NULL | |
| semester | INTEGER | NOT NULL CHECK(1,2) | |
| record_type | VARCHAR(10) | NOT NULL | BASIC (담임) / SPECIAL (세특) |
| category | VARCHAR(50) | NOT NULL | 아래 참조 |
| content | JSONB | NOT NULL | 카테고리별 유연한 데이터 |
| is_visible_to_student | BOOLEAN | DEFAULT false | 담임이 설정 |
| is_visible_to_parent | BOOLEAN | DEFAULT false | 담임이 설정 |
| review_status | VARCHAR(20) | DEFAULT 'DRAFT' | DRAFT / REVIEWED / APPROVED (담임 검토) |
| created_at, updated_at | TIMESTAMPTZ | | |
| created_by, updated_by | BIGINT | FK→users | |
| version | BIGINT | DEFAULT 0 | 낙관적 락 ⓪ |

**record_type별 category:**

| record_type | category 값 | 작성 권한 |
|-------------|-------------|-----------|
| BASIC | ATTENDANCE | 담임 |
| BASIC | GENERAL_OPINION | 담임 |
| BASIC | AWARD | 담임 |
| BASIC | VOLUNTEER | 담임 |
| SPECIAL | SPECIAL_NOTE | 해당 과목 담당 교사 |

**세특(SPECIAL) 제약:** subject_id NOT NULL, record_type = 'SPECIAL'일 때  
**기본(BASIC) 제약:** subject_id NULL, record_type = 'BASIC'일 때

---

### 12. feedbacks

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK→students | |
| teacher_id | BIGINT | FK→teachers | 작성 교사 |
| category | VARCHAR(30) | NOT NULL | ACADEMIC / BEHAVIOR / ATTENDANCE / ATTITUDE / GENERAL |
| content | TEXT | NOT NULL | 평문 (검색 필요) |
| is_visible_to_student | BOOLEAN | DEFAULT false | 담임이 최종 설정 |
| is_visible_to_parent | BOOLEAN | DEFAULT false | 담임이 최종 설정 |
| created_at, updated_at | TIMESTAMPTZ | | |
| version | BIGINT | DEFAULT 0 | 낙관적 락 ⓪ |

**수정 권한:** 작성자(teacher_id) 또는 ADMIN

---

### 13. counselings (수정)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK→students | |
| teacher_id | BIGINT | FK→teachers | 작성 교사 |
| counsel_date | DATE | NOT NULL | |
| category | VARCHAR(30) | NOT NULL | HOMEROOM / CAREER / LIFE / PROFESSIONAL / OTHER |
| content | TEXT | NOT NULL | ★AES-256-GCM |
| next_plan | TEXT | | ★AES-256-GCM |
| next_counsel_date | DATE | | |
| created_at, updated_at | TIMESTAMPTZ | | |
| version | BIGINT | DEFAULT 0 | 낙관적 락 ⓪ |

> **is_shared 제거:** 전체 교사 공유로 확정. 상담은 학생/학부모 접근 없음.

**상담 카테고리 변경:**

| 구분 | 이전 | 변경 |
|------|------|------|
| 담임 상담 | ACADEMIC | HOMEROOM |
| 진로 상담 | CAREER | CAREER |
| 생활 상담 | BEHAVIOR | LIFE |
| 전문 상담 | PERSONAL | PROFESSIONAL |
| 기타 | OTHER | OTHER |

---

### 14. notifications

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| recipient_id | BIGINT | FK→users | |
| type | VARCHAR(30) | NOT NULL | SCORE_UPDATE / FEEDBACK_NEW / COUNSEL_UPDATE / SYSTEM |
| title | VARCHAR(200) | NOT NULL | |
| message | TEXT | NOT NULL | 민감정보 미포함 ("성적이 업데이트되었습니다" 형식) |
| reference_type | VARCHAR(30) | | SCORE / FEEDBACK / COUNSEL |
| reference_id | BIGINT | | |
| is_read | BOOLEAN | DEFAULT false | |
| created_at | TIMESTAMPTZ | NOT NULL | |

---

### 15. invite_tokens (신규)

SMS OTP 발송 및 검증 관리.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| phone_hash | VARCHAR(64) | NOT NULL | SHA-256 — 사전 등록 번호 매칭 |
| otp_code | VARCHAR(6) | NOT NULL | 6자리 숫자 OTP |
| purpose | VARCHAR(20) | NOT NULL | ACTIVATE / PW_RESET |
| expires_at | TIMESTAMPTZ | NOT NULL | 발급 후 5분 |
| used_at | TIMESTAMPTZ | | 사용 시각 (NULL이면 미사용) |
| attempt_count | INTEGER | DEFAULT 0 | 5회 초과 시 폐기 |
| created_at | TIMESTAMPTZ | NOT NULL | |

---

### 16. refresh_tokens (신규 — Redis 대체)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK→users ON DELETE CASCADE | |
| token_hash | VARCHAR(64) | UNIQUE NOT NULL | SHA-256 |
| expires_at | TIMESTAMPTZ | NOT NULL | 7일 |
| created_at | TIMESTAMPTZ | NOT NULL | |

**인덱스:** user_id, expires_at

---

### 17. token_blacklist (신규 — Redis 대체)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| token_hash | VARCHAR(64) | UNIQUE NOT NULL | 로그아웃된 AT의 SHA-256 |
| expires_at | TIMESTAMPTZ | NOT NULL | AT 잔여 만료시간까지 |

**인덱스:** expires_at (만료된 레코드 배치 삭제용)

---

### 18. audit_logs (신규)

모든 교육 데이터 변경 이력.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGSERIAL | PK | |
| table_name | VARCHAR(50) | NOT NULL | 변경된 테이블명 |
| record_id | BIGINT | NOT NULL | 변경된 레코드 ID |
| field_name | VARCHAR(100) | NOT NULL | 변경된 컬럼명 |
| old_value | TEXT | | 변경 전 값 |
| new_value | TEXT | | 변경 후 값 |
| changed_by | BIGINT | FK→users NULLABLE | NULL이면 시스템 변경 |
| changed_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**인덱스:** (table_name, record_id), changed_by, changed_at

---

## 관계 요약

```
users 1──1 teachers
users 1──1 students
users 1──1 parents
parents M──N students        (via parent_student, Admin 관리)

classes M──1 teachers        (homeroom_teacher_id)
students M──N classes        (via student_enrollments, 학년도별)
teachers M──N classes+subjects (via teacher_assignments, 학년도별)

students 1──N scores         (교과 담당 교사만 작성)
students 1──N student_records (BASIC=담임, SPECIAL=교과교사)
students 1──N feedbacks       (모든 교사 작성, 작성자만 수정)
students 1──N counselings     (모든 교사 작성, 작성자만 수정)
users    1──N notifications
users    1──N refresh_tokens
```

---

## 암호화 적용 현황

| 테이블.컬럼 | 방식 | 이유 |
|---|---|---|
| users.email | AES-256-GCM | PII — 로그인 ID |
| users.email_hash | SHA-256 | 조회/UNIQUE 전용 |
| users.phone | AES-256-GCM | PII — OTP 발송 대상 |
| users.phone_hash | SHA-256 | 사전 등록 확인 전용 |
| users.password_hash | bcrypt(12) | 단방향, 복호화 불가 |
| counselings.content | AES-256-GCM | 민감 상담 내용 |
| counselings.next_plan | AES-256-GCM | 민감 상담 내용 |
| users.name | **평문** | 검색 필요 |
| scores.score | **평문** | SQL 집계(AVG, RANK) 필요 |
| feedbacks.content | **평문** | 검색 필요 |
| student_records.content | **평문** | JSONB 쿼리 필요 |

---

## 권한 체크 로직 요약

| 기능 | 체크 방법 |
|------|-----------|
| 성적 수정 | `teacher_assignments`에서 (teacher, student의 class, subject, year) 존재 확인 |
| 기본 학생부 수정 | `classes.homeroom_teacher_id` = 현재 교사, 해당 academic_year |
| 세특 수정 | `teacher_assignments`에서 (teacher, student의 class, subject, year) 존재 확인 |
| 상담 수정 | `counselings.teacher_id` = 현재 교사 또는 ADMIN |
| 피드백 수정 | `feedbacks.teacher_id` = 현재 교사 또는 ADMIN |
| 공개 여부 설정 | 담임 확인 (`classes.homeroom_teacher_id`) 또는 ADMIN |
| STUDENT 조회 | `student_enrollments`로 본인 확인 + is_visible_to_student = true |
| PARENT 조회 | `parent_student`로 자녀 확인 + is_visible_to_parent = true |

---

## 낙관적 락 적용 현황

| 테이블 | version | 이유 |
|---|---|---|
| scores | O | 동시 성적 수정 lost-update 방지 |
| feedbacks | O | 동시 수정 방지 |
| counselings | O | 동시 수정 방지 |
| student_records | O | 동시 수정 방지 |
| users | X | 동시 수정 빈도 낮음 |
| notifications | X | 단순 is_read 업데이트 |
