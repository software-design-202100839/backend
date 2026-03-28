# 인증 API 스펙

- **Sprint:** 0
- **담당:** 이백엔드
- **리뷰:** 이큐에이
- **Swagger 태그:** `Auth`

---

## 기본 설정

- **Base URL:** `/api/v1/auth`
- **인증 방식:** JWT (Access Token + Refresh Token)
- **Access Token 만료:** 30분
- **Refresh Token 만료:** 7일
- **토큰 저장:** Access Token → 클라이언트(메모리 또는 httpOnly 쿠키), Refresh Token → Redis + httpOnly 쿠키
- **비밀번호 해시:** bcrypt (strength=12)

---

## API 목록

### 1. 회원가입

```
POST /api/v1/auth/signup
```

**Request Body:**
```json
{
  "email": "teacher@school.ac.kr",
  "password": "SecurePass123!",
  "name": "김영수",
  "phone": "010-1234-5678",
  "role": "TEACHER",
  "roleDetail": {
    "department": "수학"
  }
}
```

**role별 roleDetail 구조:**

TEACHER:
```json
{
  "department": "수학",
  "homeroomGrade": 2,
  "homeroomClass": 3
}
```

STUDENT:
```json
{
  "grade": 2,
  "classNum": 3,
  "studentNum": 15,
  "admissionYear": 2024
}
```

PARENT:
```json
{
  "studentIds": [1, 2],
  "relationships": ["FATHER", "FATHER"]
}
```

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "email": "teacher@school.ac.kr",
    "name": "김영수",
    "role": "TEACHER"
  }
}
```

**Error Cases:**
| 상태 코드 | 조건 | 메시지 |
|-----------|------|--------|
| 400 | 필수 필드 누락 / 유효성 검증 실패 | 필드별 에러 메시지 |
| 409 | 이메일 중복 | "이미 등록된 이메일입니다" |

---

### 2. 로그인

```
POST /api/v1/auth/login
```

**Request Body:**
```json
{
  "email": "teacher@school.ac.kr",
  "password": "SecurePass123!"
}
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "user": {
      "id": 1,
      "email": "teacher@school.ac.kr",
      "name": "김영수",
      "role": "TEACHER"
    }
  }
}
```

**Error Cases:**
| 상태 코드 | 조건 | 메시지 |
|-----------|------|--------|
| 401 | 이메일 또는 비밀번호 불일치 | "이메일 또는 비밀번호가 올바르지 않습니다" |
| 403 | 비활성화된 계정 | "비활성화된 계정입니다. 관리자에게 문의하세요" |

**보안 참고 (이큐에이):**
- 이메일이 존재하지 않는 경우와 비밀번호가 틀린 경우를 구분하지 않는다 → 사용자 열거 공격 방지
- 로그인 실패 5회 시 계정 잠금 (향후 Sprint 3에서 구현)

---

### 3. 토큰 갱신

```
POST /api/v1/auth/refresh
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbGci..."
}
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGci...(새 토큰)",
    "refreshToken": "eyJhbGci...(새 토큰, Rotation)",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

**Refresh Token Rotation:**
- 갱신 시 기존 Refresh Token을 무효화하고 새로운 Refresh Token 발급
- 탈취된 토큰 재사용 시 모든 세션 강제 로그아웃 (이큐에이 요구)

**Error Cases:**
| 상태 코드 | 조건 | 메시지 |
|-----------|------|--------|
| 401 | 유효하지 않은 / 만료된 Refresh Token | "인증이 만료되었습니다. 다시 로그인해주세요" |

---

### 4. 로그아웃

```
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
```

**Request Body:** 없음 (또는 refreshToken 포함)

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "로그아웃되었습니다"
}
```

**처리 로직:**
1. Access Token을 Redis 블랙리스트에 추가 (남은 만료시간만큼 TTL 설정)
2. Refresh Token을 Redis에서 삭제

---

### 5. 내 정보 조회

```
GET /api/v1/auth/me
Authorization: Bearer {accessToken}
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "email": "teacher@school.ac.kr",
    "name": "김영수",
    "phone": "010-1234-5678",
    "role": "TEACHER",
    "roleDetail": {
      "department": "수학",
      "homeroomGrade": 2,
      "homeroomClass": 3
    },
    "createdAt": "2025-03-12T09:00:00"
  }
}
```

**Error Cases:**
| 상태 코드 | 조건 | 메시지 |
|-----------|------|--------|
| 401 | 토큰 없음 / 만료 / 블랙리스트 | "인증이 필요합니다" |

---

## 공통 응답 형식

### 성공 응답
```json
{
  "status": "success",
  "data": { ... }
}
```

### 에러 응답
```json
{
  "status": "error",
  "code": "AUTH_001",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다",
  "errors": [
    {
      "field": "password",
      "message": "비밀번호는 8자 이상이어야 합니다"
    }
  ]
}
```

### 에러 코드 체계
| 코드 | 설명 |
|------|------|
| AUTH_001 | 인증 실패 (이메일/비밀번호 불일치) |
| AUTH_002 | 토큰 만료 |
| AUTH_003 | 유효하지 않은 토큰 |
| AUTH_004 | 권한 없음 (접근 불가) |
| AUTH_005 | 비활성화된 계정 |
| COMMON_001 | 유효성 검증 실패 |
| COMMON_002 | 리소스를 찾을 수 없음 |
| COMMON_003 | 중복된 리소스 |

---

## 유효성 검증 규칙

| 필드 | 규칙 |
|------|------|
| email | 이메일 형식, 최대 255자 |
| password | 최소 8자, 영문 대소문자 + 숫자 + 특수문자 포함 |
| name | 최소 2자, 최대 100자 |
| phone | 한국 전화번호 형식 (010-XXXX-XXXX) |
| role | TEACHER / STUDENT / PARENT 중 하나 |

---

## JWT 토큰 구조

### Access Token Payload
```json
{
  "sub": "1",
  "email": "teacher@school.ac.kr",
  "role": "TEACHER",
  "iat": 1710230400,
  "exp": 1710232200
}
```

### Refresh Token Payload
```json
{
  "sub": "1",
  "tokenId": "uuid-v4",
  "iat": 1710230400,
  "exp": 1710835200
}
```

---

## Redis 키 설계

| 키 패턴 | 값 | TTL | 용도 |
|---------|-----|-----|------|
| `auth:refresh:{userId}` | Refresh Token ID | 7일 | 유효한 RT 확인 |
| `auth:blacklist:{accessToken}` | "true" | AT 남은 만료시간 | 로그아웃된 AT 무효화 |

---

## Spring Security 필터 체인

```
HTTP Request
    │
    ▼
JwtAuthenticationFilter (OncePerRequestFilter)
    │
    ├─ Authorization 헤더 확인
    ├─ 토큰 파싱 + 유효성 검증
    ├─ Redis 블랙리스트 확인
    ├─ SecurityContext에 Authentication 설정
    │
    ▼
AuthorizationFilter
    │
    ├─ /api/v1/auth/signup, /api/v1/auth/login, /api/v1/auth/refresh → permitAll
    ├─ /api/v1/** → authenticated
    ├─ Role 기반 접근 제어 (추후 스프린트에서 세분화)
    │
    ▼
Controller
```

---

## Postman 테스트 시나리오 (이큐에이)

1. **정상 흐름:** 회원가입 → 로그인 → 내 정보 조회 → 토큰 갱신 → 로그아웃
2. **인증 실패:** 잘못된 비밀번호로 로그인 → 401
3. **토큰 만료:** 만료된 AT로 요청 → 401 → RT로 갱신 → 새 AT로 재요청
4. **로그아웃 후 접근:** 로그아웃 → 기존 AT로 요청 → 401 (블랙리스트)
5. **권한 검증:** 학생 토큰으로 교사 전용 API 접근 → 403
6. **중복 가입:** 같은 이메일로 재가입 → 409
7. **입력값 검증:** 비밀번호 6자 → 400, 이메일 형식 오류 → 400
