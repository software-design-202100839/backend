# ADMIN API 스펙

- **Sprint:** 6 (신규)
- **담당:** 이백엔드
- **리뷰:** 이큐에이
- **Swagger 태그:** `Admin`

> 모든 엔드포인트는 `ADMIN` 역할만 접근 가능.  
> Spring Security: `hasRole('ADMIN')` 전역 적용 (`/api/v1/admin/**`)

---

## Base URL

```
/api/v1/admin
```

---

## 1. 교사 관리

### 1-1. 교사 목록 조회

```
GET /api/v1/admin/teachers
```

**Query Parameters:**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| page | int | 페이지 번호 (default: 0) |
| size | int | 페이지 크기 (default: 20) |
| isActive | boolean | 활성화 상태 필터 |

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "김철수",
        "phone": "010-1234-5678",
        "email": "teacher01@school.ac.kr",
        "department": "수학",
        "isActive": true,
        "isActivated": true,
        "currentClass": { "grade": 2, "classNum": 3, "isHomeroom": true }
      }
    ],
    "totalElements": 25,
    "totalPages": 2
  }
}
```

---

### 1-2. 교사 등록

```
POST /api/v1/admin/teachers
```

**Request Body:**
```json
{
  "name": "김철수",
  "phone": "010-1234-5678",
  "department": "수학"
}
```

**처리 로직:**
1. phone_hash 생성 → 중복 확인
2. `users` 레코드 생성 (role=TEACHER, is_active=false, 이메일/비밀번호 미설정)
3. `teachers` 레코드 생성

> 계정 활성화는 교사가 직접 OTP 인증 후 수행. 등록 시점에 비밀번호 없음.

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "name": "김철수",
    "phone": "010-1234-5678",
    "department": "수학",
    "isActivated": false
  }
}
```

**Error Cases:**
| 코드 | 조건 |
|------|------|
| 409 | 전화번호 중복 |

---

### 1-3. 교사 정보 수정

```
PUT /api/v1/admin/teachers/{teacherId}
```

**Request Body:**
```json
{
  "name": "김철수",
  "phone": "010-9999-8888",
  "department": "수학"
}
```

> 전화번호 변경 시 phone_hash 재생성. 기존 OTP 폐기.

---

### 1-4. 교사 계정 비활성화

```
POST /api/v1/admin/teachers/{teacherId}/deactivate
```

**처리 로직:**
- `users.is_active = false`
- 해당 교사의 모든 `refresh_tokens` 삭제 (강제 로그아웃)

---

### 1-5. 교사 계정 재활성화

```
POST /api/v1/admin/teachers/{teacherId}/reactivate
```

---

---

## 2. 학생 관리

### 2-1. 학생 목록 조회

```
GET /api/v1/admin/students
```

**Query Parameters:**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| academicYear | int | 학년도 필터 |
| grade | int | 학년 필터 |
| classNum | int | 반 필터 |
| isActivated | boolean | 활성화 여부 |

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "이승희",
        "phone": "010-1111-2222",
        "admissionYear": 2024,
        "isActivated": true,
        "currentEnrollment": {
          "academicYear": 2026,
          "grade": 3,
          "classNum": 2,
          "studentNum": 5
        }
      }
    ]
  }
}
```

---

### 2-2. 학생 등록

```
POST /api/v1/admin/students
```

**Request Body:**
```json
{
  "name": "이승희",
  "phone": "010-1111-2222",
  "admissionYear": 2024
}
```

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "name": "이승희",
    "admissionYear": 2024,
    "isActivated": false
  }
}
```

---

### 2-3. 학생 정보 수정

```
PUT /api/v1/admin/students/{studentId}
```

**Request Body:**
```json
{
  "name": "이승희",
  "phone": "010-1111-3333",
  "admissionYear": 2024
}
```

---

---

## 3. 학부모 관리

### 3-1. 학부모 목록 조회

```
GET /api/v1/admin/parents
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "이부모",
        "phone": "010-3333-4444",
        "isActivated": true,
        "children": [
          { "studentId": 1, "studentName": "이승희", "relationship": "FATHER" }
        ]
      }
    ]
  }
}
```

---

### 3-2. 학부모 등록

```
POST /api/v1/admin/parents
```

**Request Body:**
```json
{
  "name": "이부모",
  "phone": "010-3333-4444"
}
```

---

### 3-3. 학부모 정보 수정

```
PUT /api/v1/admin/parents/{parentId}
```

---

---

## 4. 학생-학부모 연결

### 4-1. 연결 목록 조회

```
GET /api/v1/admin/students/{studentId}/parents
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": [
    { "parentId": 1, "name": "이부모", "relationship": "FATHER" },
    { "parentId": 2, "name": "김모친", "relationship": "MOTHER" }
  ]
}
```

---

### 4-2. 연결 추가

```
POST /api/v1/admin/students/{studentId}/parents
```

**Request Body:**
```json
{
  "parentId": 1,
  "relationship": "FATHER"
}
```

| relationship | 의미 |
|-------------|------|
| FATHER | 부 |
| MOTHER | 모 |
| GUARDIAN | 보호자 |

---

### 4-3. 연결 해제

```
DELETE /api/v1/admin/students/{studentId}/parents/{parentId}
```

---

---

## 5. 학년도·반 관리

### 5-1. 반 목록 조회

```
GET /api/v1/admin/classes
```

**Query Parameters:**
| 파라미터 | 설명 |
|---------|------|
| academicYear | 학년도 (default: 현재 연도) |
| grade | 학년 필터 |

**Response (200 OK):**
```json
{
  "status": "success",
  "data": [
    {
      "id": 1,
      "academicYear": 2026,
      "grade": 2,
      "classNum": 3,
      "homeroomTeacher": { "id": 5, "name": "박담임", "department": "국어" },
      "studentCount": 28
    }
  ]
}
```

---

### 5-2. 반 생성

```
POST /api/v1/admin/classes
```

**Request Body:**
```json
{
  "academicYear": 2026,
  "grade": 2,
  "classNum": 3
}
```

---

### 5-3. 담임 배정

```
PUT /api/v1/admin/classes/{classId}/homeroom
```

**Request Body:**
```json
{
  "teacherId": 5
}
```

---

### 5-4. 학생 반 배정

```
POST /api/v1/admin/classes/{classId}/students
```

**Request Body:**
```json
{
  "studentId": 1,
  "studentNum": 5
}
```

**처리 로직:**
- `student_enrollments` 레코드 생성
- 해당 학생이 이미 같은 학년도에 배정되어 있으면 409

---

---

## 6. 과목 담당 배정

### 6-1. 배정 목록 조회

```
GET /api/v1/admin/assignments
```

**Query Parameters:**
| 파라미터 | 설명 |
|---------|------|
| academicYear | 학년도 |
| classId | 반 ID |
| teacherId | 교사 ID |

**Response (200 OK):**
```json
{
  "status": "success",
  "data": [
    {
      "id": 1,
      "teacher": { "id": 3, "name": "박수학", "department": "수학" },
      "class": { "grade": 2, "classNum": 3 },
      "subject": { "id": 2, "name": "수학", "code": "MATH" },
      "academicYear": 2026
    }
  ]
}
```

---

### 6-2. 배정 등록

```
POST /api/v1/admin/assignments
```

**Request Body:**
```json
{
  "teacherId": 3,
  "classId": 1,
  "subjectId": 2,
  "academicYear": 2026
}
```

---

### 6-3. 배정 해제

```
DELETE /api/v1/admin/assignments/{assignmentId}
```

---

---

## 7. 전체 데이터 조회·예외 수정

> ADMIN은 모든 교육 데이터를 조회하고 예외적으로 수정할 수 있음.  
> 수정 시 audit_logs에 ADMIN 수정으로 기록됨.

### 7-1. 성적 수정 (예외)

```
PUT /api/v1/admin/scores/{scoreId}
```

**Request Body:**
```json
{
  "score": 92.0,
  "reason": "입력 오류 정정"
}
```

---

### 7-2. 상담 내역 수정 (예외)

```
PUT /api/v1/admin/counselings/{counselingId}
```

---

### 7-3. 피드백 수정 (예외)

```
PUT /api/v1/admin/feedbacks/{feedbackId}
```

---

### 7-4. 학생부 수정 (예외)

```
PUT /api/v1/admin/student-records/{recordId}
```

---

---

## 8. 로그 조회

### 8-1. 변경 이력 조회

```
GET /api/v1/admin/audit-logs
```

**Query Parameters:**
| 파라미터 | 설명 |
|---------|------|
| tableName | 테이블명 필터 (scores, feedbacks 등) |
| recordId | 특정 레코드 ID |
| changedBy | 수정자 user ID |
| from | 시작 일시 |
| to | 종료 일시 |
| page / size | 페이지네이션 |

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "tableName": "scores",
        "recordId": 42,
        "fieldName": "score",
        "oldValue": "85.0",
        "newValue": "90.0",
        "changedBy": { "id": 1, "name": "Admin", "role": "ADMIN" },
        "changedAt": "2026-04-20T14:30:00"
      }
    ]
  }
}
```

---

---

## 권한 요약

| 엔드포인트 | ADMIN | TEACHER | STUDENT | PARENT |
|-----------|-------|---------|---------|--------|
| 교사 관리 | ✅ | ❌ | ❌ | ❌ |
| 학생 관리 | ✅ | ❌ | ❌ | ❌ |
| 학부모 관리 | ✅ | ❌ | ❌ | ❌ |
| 학생-학부모 연결 | ✅ | ❌ | ❌ | ❌ |
| 학년도·반 관리 | ✅ | ❌ | ❌ | ❌ |
| 과목 담당 배정 | ✅ | ❌ | ❌ | ❌ |
| 예외 데이터 수정 | ✅ | ❌ | ❌ | ❌ |
| 로그 조회 | ✅ | ❌ | ❌ | ❌ |
