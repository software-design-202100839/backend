# ERD 설계: 학생 성적 및 상담 관리 시스템

- **상태:** 확정
- **작성일:** 2025-03-12
- **작성자:** 이백엔드 (PM)
- **리뷰:** 이큐에이 (보안 관점 검토)

---

## 설계 원칙

1. **정규화 우선:** 성적 데이터는 학생↔과목↔학기 관계가 명확하므로 3NF 기준 설계
2. **JSONB 활용:** 학생부의 "특기사항" 등 비정형 항목은 PostgreSQL JSONB로 유연하게 처리
3. **Soft Delete:** 학생 데이터는 법적 보존 의무가 있으므로 물리 삭제 대신 `deleted_at` 활용
4. **감사 추적:** 모든 테이블에 `created_at`, `updated_at`, `created_by`, `updated_by` 포함
5. **개인정보 암호화 대상 컬럼:** 이름, 연락처, 이메일 → Sprint 3에서 암호화 적용 예정

---

## ERD 다이어그램

```
┌─────────────────┐       ┌─────────────────┐
│     users        │       │    subjects      │
├─────────────────┤       ├─────────────────┤
│ PK id            │       │ PK id            │
│    email ★       │       │    name          │
│    password_hash │       │    code          │
│    name ★        │       │    description   │
│    phone ★       │       │    created_at    │
│    role          │       └────────┬────────┘
│    is_active     │                │
│    created_at    │                │
│    updated_at    │                │
│    deleted_at    │                │
└───┬─────┬───┬───┘                │
    │     │   │                    │
    ▼     ▼   ▼                    │
┌───────┐ ┌───────┐ ┌────────┐    │
│teacher│ │student│ │ parent │    │
├───────┤ ├───────┤ ├────────┤    │
│PK/FK  │ │PK/FK  │ │PK/FK   │    │
│user_id│ │user_id│ │user_id │    │
│dept   │ │grade  │ │        │    │
│       │ │class  │ └───┬────┘    │
│       │ │number │     │         │
└──┬────┘ └┬──┬───┘     │         │
   │       │  │    ┌────┘         │
   │       │  │    ▼              │
   │       │  │ ┌──────────────┐  │
   │       │  │ │parent_student│  │
   │       │  │ │(M:N 관계)    │  │
   │       │  │ └──────────────┘  │
   │       │  │                   │
   │       ▼  │                   ▼
   │    ┌─────┴───────────────────────┐
   │    │          scores              │
   │    ├─────────────────────────────┤
   │    │ PK id                        │
   │    │ FK student_id → students     │
   │    │ FK subject_id → subjects     │
   │    │ FK teacher_id → teachers     │
   │    │    semester                   │
   │    │    year                       │
   │    │    score                      │
   │    │    grade_letter               │
   │    │    rank                       │
   │    │    created_at, updated_at     │
   │    │    created_by, updated_by     │
   │    └─────────────────────────────┘
   │
   │    ┌─────────────────────────────┐
   │    │      student_records         │
   │    ├─────────────────────────────┤
   │    │ PK id                        │
   │    │ FK student_id → students     │
   │    │    year, semester             │
   │    │    category                   │
   │    │    content (JSONB)            │
   │    │    created_at, updated_at     │
   │    │    created_by, updated_by     │
   │    └─────────────────────────────┘
   │
   ▼
┌─────────────────────────────────┐
│          feedbacks               │
├─────────────────────────────────┤
│ PK id                            │
│ FK student_id → students         │
│ FK teacher_id → teachers         │
│    category                      │
│    content                       │
│    is_visible_to_student         │
│    is_visible_to_parent          │
│    created_at, updated_at        │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│        counselings               │
├─────────────────────────────────┤
│ PK id                            │
│ FK student_id → students         │
│ FK teacher_id → teachers         │
│    counsel_date                  │
│    category                      │
│    content                       │
│    next_plan                     │
│    next_counsel_date             │
│    is_shared                     │
│    created_at, updated_at        │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│        notifications             │
├─────────────────────────────────┤
│ PK id                            │
│ FK recipient_id → users          │
│    type                          │
│    title                         │
│    message                       │
│    reference_type                │
│    reference_id                  │
│    is_read                       │
│    created_at                    │
└─────────────────────────────────┘

★ = Sprint 3에서 암호화 적용 대상
```

---

## 엔티티 상세 설명

### 1. users (사용자 기본 테이블)

모든 사용자(교사/학생/학부모)의 공통 정보. Single Table 상속 방식으로 `role` 컬럼으로 구분.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | 사용자 ID |
| email | VARCHAR(255) | UNIQUE, NOT NULL | 로그인 이메일 ★암호화 대상 |
| password_hash | VARCHAR(255) | NOT NULL | bcrypt 해시 |
| name | VARCHAR(100) | NOT NULL | 사용자 이름 ★암호화 대상 |
| phone | VARCHAR(20) | | 연락처 ★암호화 대상 |
| role | VARCHAR(20) | NOT NULL | TEACHER / STUDENT / PARENT |
| is_active | BOOLEAN | DEFAULT true | 활성 상태 |
| created_at | TIMESTAMP | NOT NULL | 생성일 |
| updated_at | TIMESTAMP | NOT NULL | 수정일 |
| deleted_at | TIMESTAMP | | Soft Delete용 |

**인덱스:** `email` (UNIQUE), `role`, `deleted_at`

### 2. teachers (교사 상세 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → users, UNIQUE | 사용자 참조 |
| department | VARCHAR(50) | | 담당 교과 |
| homeroom_grade | INTEGER | | 담임 학년 (nullable) |
| homeroom_class | INTEGER | | 담임 반 (nullable) |

### 3. students (학생 상세 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → users, UNIQUE | 사용자 참조 |
| grade | INTEGER | NOT NULL | 학년 (1~3) |
| class_num | INTEGER | NOT NULL | 반 |
| student_num | INTEGER | NOT NULL | 번호 |
| admission_year | INTEGER | NOT NULL | 입학년도 |

**인덱스:** `(grade, class_num, student_num)` UNIQUE (같은 학년-반-번호 중복 방지)

### 4. parents (학부모 상세 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → users, UNIQUE | 사용자 참조 |

### 5. parent_student (학부모-학생 M:N 관계)

한 학부모가 여러 자녀를 둘 수 있고, 한 학생에 부모 2명(부/모)이 있을 수 있으므로 M:N.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| parent_id | BIGINT | FK → parents | |
| student_id | BIGINT | FK → students | |
| relationship | VARCHAR(20) | NOT NULL | FATHER / MOTHER / GUARDIAN |

**PK:** (parent_id, student_id)

### 6. subjects (과목 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| name | VARCHAR(100) | NOT NULL | 과목명 (국어, 수학, ...) |
| code | VARCHAR(20) | UNIQUE, NOT NULL | 과목 코드 (KOR, MATH, ...) |
| description | TEXT | | 과목 설명 |
| created_at | TIMESTAMP | NOT NULL | |

### 7. scores (성적 테이블) ← 핵심

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK → students, NOT NULL | |
| subject_id | BIGINT | FK → subjects, NOT NULL | |
| teacher_id | BIGINT | FK → teachers, NOT NULL | 입력한 교사 |
| year | INTEGER | NOT NULL | 학년도 (2025) |
| semester | INTEGER | NOT NULL | 학기 (1 또는 2) |
| score | DECIMAL(5,2) | NOT NULL | 점수 (0.00 ~ 100.00) |
| grade_letter | VARCHAR(5) | | 등급 (A+, A, B+, ...) — 자동 계산 |
| rank | INTEGER | | 석차 — 자동 계산 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |
| created_by | BIGINT | FK → users | 입력자 |
| updated_by | BIGINT | FK → users | 수정자 |

**UNIQUE:** `(student_id, subject_id, year, semester)` — 동일 학생의 같은 학기 같은 과목 성적 중복 방지
**인덱스:** `(student_id, year, semester)` — 학생별 학기별 조회, `(subject_id, year, semester)` — 과목별 조회

**자동 계산 로직 (Application 레벨):**
- `grade_letter`: score 범위에 따라 A+ ~ F 자동 산출
- `rank`: 같은 학년-과목-학기 내 석차 계산
- 총점/평균: 조회 시 집계 쿼리로 계산 (별도 저장하지 않음 — 정규화 원칙)

### 8. student_records (학생부 테이블)

학생부는 항목이 다양하고 유동적(출결, 특기사항, 봉사활동 등)이므로 **category + JSONB content** 구조.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK → students, NOT NULL | |
| year | INTEGER | NOT NULL | 학년도 |
| semester | INTEGER | NOT NULL | 학기 |
| category | VARCHAR(50) | NOT NULL | ATTENDANCE / SPECIAL_NOTE / AWARD / VOLUNTEER / OTHER |
| content | JSONB | NOT NULL | 카테고리별 유연한 데이터 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |
| created_by | BIGINT | FK → users | |
| updated_by | BIGINT | FK → users | |

**JSONB content 예시:**

출결 (ATTENDANCE):
```json
{
  "total_days": 190,
  "present": 185,
  "absent": 2,
  "late": 2,
  "early_leave": 1,
  "absence_reasons": [
    {"date": "2025-04-15", "reason": "병결", "note": "감기"}
  ]
}
```

특기사항 (SPECIAL_NOTE):
```json
{
  "content": "수학 경시대회 참여, 과학 동아리 활동 우수",
  "written_by": "담임교사"
}
```

**인덱스:** `(student_id, year, semester)`, `category`

### 9. feedbacks (피드백 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK → students, NOT NULL | |
| teacher_id | BIGINT | FK → teachers, NOT NULL | 작성 교사 |
| category | VARCHAR(30) | NOT NULL | ACADEMIC / BEHAVIOR / ATTENDANCE / ATTITUDE / GENERAL |
| content | TEXT | NOT NULL | 피드백 내용 |
| is_visible_to_student | BOOLEAN | DEFAULT false | 학생에게 공개 여부 |
| is_visible_to_parent | BOOLEAN | DEFAULT false | 학부모에게 공개 여부 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**인덱스:** `(student_id, created_at DESC)`, `(teacher_id)`, `category`

### 10. counselings (상담 내역 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| student_id | BIGINT | FK → students, NOT NULL | |
| teacher_id | BIGINT | FK → teachers, NOT NULL | 상담 교사 |
| counsel_date | DATE | NOT NULL | 상담 날짜 |
| category | VARCHAR(30) | NOT NULL | ACADEMIC / CAREER / BEHAVIOR / PERSONAL / OTHER |
| content | TEXT | NOT NULL | 상담 내용 |
| next_plan | TEXT | | 후속 상담 계획 |
| next_counsel_date | DATE | | 다음 상담 예정일 |
| is_shared | BOOLEAN | DEFAULT true | 다른 교사에게 공유 여부 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**인덱스:** `(student_id, counsel_date DESC)`, `(teacher_id)`, `(is_shared)`, `category`

**교사 간 공유 로직:**
- `is_shared = true`인 상담은 모든 교사가 조회 가능
- `is_shared = false`인 상담은 작성 교사만 조회 가능
- 검색/필터링: 학생명, 날짜 범위, 카테고리, 교사별

### 11. notifications (알림 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGSERIAL | PK | |
| recipient_id | BIGINT | FK → users, NOT NULL | 수신자 |
| type | VARCHAR(30) | NOT NULL | SCORE_UPDATE / FEEDBACK_NEW / COUNSEL_UPDATE / SYSTEM |
| title | VARCHAR(200) | NOT NULL | 알림 제목 |
| message | TEXT | NOT NULL | 알림 내용 |
| reference_type | VARCHAR(30) | | 참조 엔티티 타입 (SCORE, FEEDBACK, COUNSEL) |
| reference_id | BIGINT | | 참조 엔티티 ID |
| is_read | BOOLEAN | DEFAULT false | 읽음 여부 |
| created_at | TIMESTAMP | NOT NULL | |

**인덱스:** `(recipient_id, is_read, created_at DESC)` — 미읽은 알림 우선 조회

**알림 발생 시점:**
- 성적 입력/수정 시 → 해당 학생 + 학부모에게
- 피드백 작성 시 (공개 설정된 경우) → 해당 학생/학부모에게
- 상담 내역 업데이트 시 → 해당 교사들에게

---

## 관계 요약

```
users 1──1 teachers
users 1──1 students
users 1──1 parents
parents M──N students (via parent_student)

students 1──N scores
subjects 1──N scores
teachers 1──N scores

students 1──N student_records
students 1──N feedbacks
teachers 1──N feedbacks

students 1──N counselings
teachers 1──N counselings

users 1──N notifications
```

---

## 보안 고려사항 (이큐에이 리뷰)

1. **암호화 대상 컬럼:** users.email, users.name, users.phone → Sprint 3에서 AES-256 암호화 적용
2. **비밀번호:** bcrypt(strength=12) 해시 저장, 평문 절대 저장 금지
3. **Soft Delete:** 학생 데이터 삭제 시 `deleted_at` 타임스탬프만 기록
4. **감사 추적:** 성적, 학생부 테이블에 `created_by`, `updated_by` 포함
5. **상담 공유 권한:** `is_shared` 플래그로 제어, API 레벨에서 추가 검증 필요
