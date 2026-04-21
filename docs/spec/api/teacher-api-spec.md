# 교사 기능 API 스펙

- **Sprint:** 6 (권한 모델 재설계 반영)
- **담당:** 이백엔드
- **리뷰:** 이큐에이
- **Swagger 태그:** `Score`, `StudentRecord`, `Counseling`, `Feedback`

> **변경 이력**
> - Sprint 1~4: 기본 CRUD, 단순 역할 기반 권한
> - Sprint 6: 학년도 기반 담당 교사 권한 모델로 전면 재설계

---

## 핵심 권한 원칙

| 기능 | 조회 | 작성 | 수정 |
|------|------|------|------|
| 성적 | 전체 교사 | 해당 과목 담당 교사 | 해당 과목 담당 교사 |
| 기본 학생부 | 전체 교사 | 해당 학년도 담임 | 해당 학년도 담임 |
| 세특 | 전체 교사 | 해당 과목 담당 교사 | 해당 과목 담당 교사 |
| 상담 | 전체 교사 | 모든 교사 | 작성자 |
| 피드백 | 전체 교사 | 모든 교사 | 작성자 |
| 공개 여부 설정 | — | 담임 | 담임 |

> **삭제는 전부 불가.** 모든 수정은 `audit_logs`에 기록.

---

## 학생 조회

### 학생 목록 검색

```
GET /api/v1/students
Authorization: TEACHER / ADMIN
```

**Query Parameters:**
| 파라미터 | 설명 |
|---------|------|
| name | 이름 검색 |
| academicYear | 학년도 (default: 현재) |
| grade | 학년 |
| classNum | 반 |

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "이승희",
        "enrollments": [
          { "academicYear": 2025, "grade": 2, "classNum": 1, "studentNum": 7 },
          { "academicYear": 2026, "grade": 3, "classNum": 2, "studentNum": 5 }
        ]
      }
    ]
  }
}
```

---

### 학생 상세 조회

```
GET /api/v1/students/{studentId}?academicYear=2026
Authorization: TEACHER / ADMIN
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "name": "이승희",
    "admissionYear": 2024,
    "selectedYear": {
      "academicYear": 2026,
      "grade": 3,
      "classNum": 2,
      "studentNum": 5,
      "homeroomTeacher": { "id": 5, "name": "박담임" }
    },
    "availableYears": [2024, 2025, 2026]
  }
}
```

---

---

## 성적 (Scores)

### 성적 조회

```
GET /api/v1/scores?studentId=1&year=2026&semester=1
Authorization: TEACHER (전체) / STUDENT (본인, 조회만) / PARENT (자녀, 조회만)
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "studentId": 1,
    "studentName": "이승희",
    "year": 2026,
    "semester": 1,
    "scores": [
      {
        "id": 1,
        "subject": { "id": 2, "name": "수학", "code": "MATH" },
        "score": 92.5,
        "gradeLetter": "A",
        "rank": 3,
        "enteredBy": { "id": 3, "name": "박수학" },
        "canEdit": true
      }
    ],
    "summary": {
      "total": 555.0,
      "average": 92.5,
      "subjectCount": 6
    }
  }
}
```

> `canEdit`: 요청한 교사가 해당 과목 담당 여부. 프론트엔드에서 수정 버튼 표시에 사용.

---

### 성적 입력

```
POST /api/v1/scores
Authorization: TEACHER (담당 교사만)
```

**Request Body:**
```json
{
  "studentId": 1,
  "subjectId": 2,
  "year": 2026,
  "semester": 1,
  "score": 92.5
}
```

**권한 체크:**
```
teacher_assignments에서
(현재 교사, 해당 학생의 class_id, subjectId, year) 존재 여부 확인
→ 없으면 403
```

**처리 로직:**
1. 권한 체크
2. grade_letter 자동 계산 (95↑A+, 90↑A, 85↑B+, ...)
3. rank 자동 계산 (같은 학년·과목·학기 기준)
4. Score 저장
5. 해당 학생·학부모에게 알림 발송 ("성적이 업데이트되었습니다")
6. audit_logs 기록

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "subject": { "name": "수학" },
    "score": 92.5,
    "gradeLetter": "A",
    "rank": 3
  }
}
```

**Error Cases:**
| 코드 | 조건 |
|------|------|
| 403 | 해당 과목 담당 교사가 아님 |
| 409 | 해당 학기·과목 성적 이미 존재 (수정 API 사용) |
| 409 | 낙관적 락 충돌 |

---

### 성적 수정

```
PUT /api/v1/scores/{scoreId}
Authorization: TEACHER (담당 교사만)
```

**Request Body:**
```json
{
  "score": 95.0,
  "version": 1
}
```

> `version` 필드 필수 — 낙관적 락. 응답의 version 값을 그대로 전송.

---

---

## 학생부 (StudentRecords)

### 학생부 조회

```
GET /api/v1/student-records?studentId=1&academicYear=2026
Authorization: TEACHER (전체) / STUDENT (공개분만) / PARENT (공개분만)
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "studentId": 1,
    "academicYear": 2026,
    "basicRecords": [
      {
        "id": 1,
        "recordType": "BASIC",
        "category": "ATTENDANCE",
        "content": {
          "totalDays": 190,
          "present": 185,
          "absent": 2,
          "late": 2,
          "earlyLeave": 1
        },
        "isVisibleToStudent": true,
        "isVisibleToParent": true,
        "reviewStatus": "APPROVED",
        "canEdit": true
      }
    ],
    "specialRecords": [
      {
        "id": 5,
        "recordType": "SPECIAL",
        "category": "SPECIAL_NOTE",
        "subject": { "id": 2, "name": "수학" },
        "content": { "content": "미적분 개념에 탁월한 이해력..." },
        "isVisibleToStudent": false,
        "isVisibleToParent": false,
        "reviewStatus": "DRAFT",
        "writtenBy": { "id": 3, "name": "박수학" },
        "canEdit": true
      }
    ]
  }
}
```

> `canEdit`: BASIC은 담임 여부, SPECIAL은 해당 과목 담당 여부로 판단

---

### 기본 학생부 작성/수정

```
POST /api/v1/student-records/basic
PUT  /api/v1/student-records/{recordId}/basic
Authorization: TEACHER (담임만)
```

**Request Body:**
```json
{
  "studentId": 1,
  "academicYear": 2026,
  "semester": 1,
  "category": "ATTENDANCE",
  "content": {
    "totalDays": 190,
    "present": 185,
    "absent": 2,
    "late": 2,
    "earlyLeave": 1
  },
  "version": 0
}
```

**권한 체크:**
```
classes에서 homeroom_teacher_id = 현재 교사
AND academic_year = 요청 academicYear
AND 해당 반에 studentId가 enrolled
→ 아니면 403
```

---

### 세특 작성/수정

```
POST /api/v1/student-records/special
PUT  /api/v1/student-records/{recordId}/special
Authorization: TEACHER (담당 교과 교사만)
```

**Request Body:**
```json
{
  "studentId": 1,
  "academicYear": 2026,
  "semester": 1,
  "subjectId": 2,
  "content": {
    "content": "미적분 개념에 탁월한 이해력을 보임..."
  },
  "version": 0
}
```

**권한 체크:**
```
teacher_assignments에서
(현재 교사, 해당 학생의 class_id, subjectId, academicYear) 존재 확인
→ 없으면 403
```

---

### 학생부 공개 여부 설정 (담임 전용)

```
PATCH /api/v1/student-records/{recordId}/visibility
Authorization: TEACHER (담임만) / ADMIN
```

**Request Body:**
```json
{
  "isVisibleToStudent": true,
  "isVisibleToParent": false
}
```

---

### 세특 검토 상태 설정 (담임 전용)

```
PATCH /api/v1/student-records/{recordId}/review-status
Authorization: TEACHER (담임만) / ADMIN
```

**Request Body:**
```json
{
  "reviewStatus": "REVIEWED",
  "comment": "표현 수정 필요"
}
```

| reviewStatus | 의미 |
|-------------|------|
| DRAFT | 미검토 (기본값) |
| REVIEWED | 검토 완료 |
| APPROVED | 최종 승인 |

---

---

## 상담 (Counselings)

### 상담 목록 조회

```
GET /api/v1/counselings?studentId=1
Authorization: TEACHER (전체 — 모든 교사 공유)
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "counselDate": "2026-04-15",
        "category": "HOMEROOM",
        "content": "학습 의욕 저하에 대해 면담...",
        "nextPlan": "1주 후 추가 면담 예정",
        "nextCounselDate": "2026-04-22",
        "writtenBy": { "id": 5, "name": "박담임" },
        "canEdit": true,
        "createdAt": "2026-04-15T14:30:00"
      }
    ]
  }
}
```

> `canEdit`: 작성자(writtenBy.id = 현재 교사)이거나 ADMIN이면 true  
> 상담 내용은 복호화 후 반환 (AES-256-GCM)

---

### 상담 작성

```
POST /api/v1/counselings
Authorization: TEACHER (모든 교사 가능)
```

**Request Body:**
```json
{
  "studentId": 1,
  "counselDate": "2026-04-15",
  "category": "HOMEROOM",
  "content": "학습 의욕 저하에 대해 면담...",
  "nextPlan": "1주 후 추가 면담 예정",
  "nextCounselDate": "2026-04-22"
}
```

| category | 의미 |
|---------|------|
| HOMEROOM | 담임 상담 |
| CAREER | 진로 상담 |
| LIFE | 생활 상담 |
| PROFESSIONAL | 전문 상담 |
| OTHER | 기타 |

**처리 로직:**
1. content, next_plan AES-256-GCM 암호화 후 저장
2. audit_logs 기록

---

### 상담 수정

```
PUT /api/v1/counselings/{counselingId}
Authorization: TEACHER (작성자만) / ADMIN
```

**Request Body:**
```json
{
  "content": "수정된 상담 내용...",
  "nextPlan": "수정된 후속 계획...",
  "nextCounselDate": "2026-04-29",
  "version": 1
}
```

**권한 체크:**
```
counselings.teacher_id = 현재 교사 OR 현재 역할 = ADMIN
→ 아니면 403
```

---

---

## 피드백 (Feedbacks)

### 피드백 목록 조회

```
GET /api/v1/feedbacks?studentId=1
Authorization: TEACHER (전체) / STUDENT (공개분) / PARENT (공개분)
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "category": "ACADEMIC",
        "content": "수학 문제 해결 능력이 뛰어남...",
        "isVisibleToStudent": true,
        "isVisibleToParent": false,
        "writtenBy": { "id": 3, "name": "박수학" },
        "canEdit": true,
        "createdAt": "2026-04-10T10:00:00"
      }
    ]
  }
}
```

> STUDENT/PARENT 요청 시: `isVisibleToStudent/Parent = true`인 것만 반환

---

### 피드백 작성

```
POST /api/v1/feedbacks
Authorization: TEACHER (모든 교사 가능)
```

**Request Body:**
```json
{
  "studentId": 1,
  "category": "ACADEMIC",
  "content": "수학 문제 해결 능력이 뛰어남...",
  "isVisibleToStudent": false,
  "isVisibleToParent": false
}
```

| category | 의미 |
|---------|------|
| ACADEMIC | 학업 |
| BEHAVIOR | 행동 |
| ATTENDANCE | 출결 |
| ATTITUDE | 태도 |
| GENERAL | 종합 |

---

### 피드백 수정

```
PUT /api/v1/feedbacks/{feedbackId}
Authorization: TEACHER (작성자만) / ADMIN
```

**Request Body:**
```json
{
  "content": "수정된 피드백...",
  "isVisibleToStudent": true,
  "isVisibleToParent": false,
  "version": 1
}
```

---

### 피드백 공개 여부 설정 (담임 전용)

```
PATCH /api/v1/feedbacks/{feedbackId}/visibility
Authorization: TEACHER (담임만) / ADMIN
```

**Request Body:**
```json
{
  "isVisibleToStudent": true,
  "isVisibleToParent": true
}
```

---

---

## 알림 (Notifications)

### 알림 목록 조회

```
GET /api/v1/notifications
Authorization: 전체 역할
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "type": "SCORE_UPDATE",
        "title": "성적 업데이트",
        "message": "성적이 업데이트되었습니다",
        "referenceType": "SCORE",
        "referenceId": 42,
        "isRead": false,
        "createdAt": "2026-04-20T14:00:00"
      }
    ],
    "unreadCount": 3
  }
}
```

> SMS로 발송하는 알림 내용에는 민감정보 미포함. 시스템 내 알림은 referenceId로 해당 화면 이동.

---

### 알림 읽음 처리

```
PATCH /api/v1/notifications/{notificationId}/read
Authorization: 전체 역할 (본인 알림만)
```

---

### 전체 읽음 처리

```
PATCH /api/v1/notifications/read-all
Authorization: 전체 역할
```

---

---

## 공통 에러 응답

```json
{
  "status": "error",
  "code": "SCORE_001",
  "message": "해당 과목의 담당 교사가 아닙니다"
}
```

| 코드 | 설명 |
|------|------|
| SCORE_001 | 성적 수정 권한 없음 (담당 교사 아님) |
| RECORD_001 | 학생부 수정 권한 없음 (담임 아님) |
| RECORD_002 | 세특 수정 권한 없음 (담당 교사 아님) |
| COUNSEL_001 | 상담 수정 권한 없음 (작성자 아님) |
| FEEDBACK_001 | 피드백 수정 권한 없음 (작성자 아님) |
| COMMON_004 | 동시 수정 충돌 (version 불일치, 재조회 후 재시도) |
