# 인증 API 스펙

- **Sprint:** 6 (전면 재설계)
- **담당:** 이백엔드
- **리뷰:** 이큐에이
- **Swagger 태그:** `Auth`

> **변경 이력**
> - Sprint 0: JWT + Redis 기반 자유 회원가입
> - Sprint 6: Controlled Onboarding + SMS OTP 기반 활성화로 전면 교체. Redis 제거.

---

## 설계 원칙

- **자유 회원가입 없음**: Admin이 사전 등록한 전화번호로만 계정 활성화 가능
- **SMS OTP 단일 채널**: 활성화·비밀번호 찾기 모두 동일한 OTP 흐름
- **Zero-knowledge**: Admin 포함 누구도 사용자 비밀번호를 알 수 없음. 비밀번호는 본인이 직접 설정
- **JWT (AT + RT)**: Access Token 30분, Refresh Token 7일, Redis 대신 PostgreSQL 저장

---

## Base URL

```
/api/v1/auth
```

---

## 계정 활성화 플로우

```
[1단계] 전화번호 입력
POST /auth/otp/send
  → Admin 사전 등록 여부 확인
  → 등록된 번호면 OTP 발송 (5분 만료, 5회 실패 시 폐기)

[2단계] OTP 입력 + ID/PW 설정
POST /auth/activate
  → phone + otpCode + email + password 한 번에 제출
  → 검증 통과 시 계정 활성화 완료
```

---

## 비밀번호 찾기 플로우

```
[1단계] 전화번호 입력
POST /auth/password/reset/request
  → 활성화된 계정의 등록 번호면 OTP 발송

[2단계] OTP + 새 비밀번호 설정
POST /auth/password/reset/confirm
  → phone + otpCode + newPassword 제출
```

---

## API 목록

---

### 1. OTP 발송

```
POST /api/v1/auth/otp/send
```

**Request Body:**
```json
{
  "phone": "010-1234-5678",
  "purpose": "ACTIVATE"
}
```

| 필드 | 설명 |
|------|------|
| phone | 활성화할 전화번호 |
| purpose | `ACTIVATE` (최초 활성화) / `PW_RESET` (비밀번호 찾기) |

**처리 로직:**
1. 입력 전화번호를 SHA-256 해시 → `users.phone_hash`와 대조
2. 미등록이면 동일 응답 반환 (전화번호 열거 공격 방지)
3. `ACTIVATE`: 이미 활성화된 계정이면 에러
4. `PW_RESET`: 미활성화 계정이면 에러
5. 기존 미사용 OTP 폐기 → 신규 6자리 OTP 생성 → SMS 발송 → `invite_tokens` 저장

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "인증번호가 발송되었습니다"
}
```

> 미등록 번호도 동일 응답 반환 — 보안상 미등록 여부를 외부에 노출하지 않음

**Error Cases:**
| 코드 | 조건 | 메시지 |
|------|------|--------|
| 400 | purpose가 유효하지 않음 | 유효성 검증 실패 |
| 409 | ACTIVATE인데 이미 활성화된 계정 | "이미 활성화된 계정입니다" |
| 409 | PW_RESET인데 미활성화 계정 | "활성화되지 않은 계정입니다" |
| 429 | OTP 재발송 요청 과다 (1분 이내 재요청) | "잠시 후 다시 시도해주세요" |

---

### 2. 계정 활성화

```
POST /api/v1/auth/activate
```

**Request Body:**
```json
{
  "phone": "010-1234-5678",
  "otpCode": "482910",
  "email": "teacher01@school.ac.kr",
  "password": "SecurePass123!"
}
```

**처리 로직:**
1. phone_hash로 `invite_tokens` 조회 (purpose=ACTIVATE, used_at IS NULL)
2. 만료 여부 확인 (expires_at > NOW())
3. attempt_count 확인 (5회 초과 시 폐기된 토큰)
4. otpCode 일치 여부 확인 → 불일치 시 attempt_count +1
5. 이메일 중복 확인 (email_hash로 조회)
6. 비밀번호 bcrypt(12) 해시
7. users 레코드 업데이트: email, email_hash, password_hash, is_active=true
8. invite_tokens.used_at 기록

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "계정이 활성화되었습니다",
  "data": {
    "id": 1,
    "email": "teacher01@school.ac.kr",
    "name": "김철수",
    "role": "TEACHER"
  }
}
```

**Error Cases:**
| 코드 | 조건 | 메시지 |
|------|------|--------|
| 400 | OTP 불일치 | "인증번호가 올바르지 않습니다 (N회 남음)" |
| 400 | OTP 만료 | "인증번호가 만료되었습니다. 다시 요청해주세요" |
| 400 | OTP 5회 초과 폐기 | "인증번호가 폐기되었습니다. 다시 요청해주세요" |
| 409 | 이메일 중복 | "이미 사용 중인 이메일입니다" |
| 422 | 비밀번호 규칙 위반 | 필드별 에러 메시지 |

---

### 3. 비밀번호 찾기 — OTP 발송

```
POST /api/v1/auth/password/reset/request
```

**Request Body:**
```json
{
  "phone": "010-1234-5678"
}
```

처리 로직: OTP 발송 API와 동일 (purpose=PW_RESET 자동 적용)

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "인증번호가 발송되었습니다"
}
```

---

### 4. 비밀번호 찾기 — 새 비밀번호 설정

```
POST /api/v1/auth/password/reset/confirm
```

**Request Body:**
```json
{
  "phone": "010-1234-5678",
  "otpCode": "193847",
  "newPassword": "NewSecurePass456!"
}
```

**처리 로직:**
1. OTP 검증 (purpose=PW_RESET)
2. 새 비밀번호 bcrypt(12) 해시
3. users.password_hash 업데이트
4. 해당 사용자의 모든 refresh_tokens 삭제 (기존 세션 강제 만료)
5. invite_tokens.used_at 기록

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "비밀번호가 변경되었습니다. 다시 로그인해주세요"
}
```

---

### 5. 로그인

```
POST /api/v1/auth/login
```

**Request Body:**
```json
{
  "email": "teacher01@school.ac.kr",
  "password": "SecurePass123!"
}
```

**처리 로직:**
1. email_hash로 사용자 조회
2. is_active 확인
3. login_locked_until 확인 (잠금 중이면 거부)
4. bcrypt 비밀번호 검증
5. 실패 시: failed_login_count +1, 5회 도달 시 login_locked_until = NOW() + 30분
6. 성공 시: failed_login_count = 0, AT/RT 생성, refresh_tokens 저장

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
      "email": "teacher01@school.ac.kr",
      "name": "김철수",
      "role": "TEACHER"
    }
  }
}
```

**Error Cases:**
| 코드 | 조건 | 메시지 |
|------|------|--------|
| 401 | 이메일/비밀번호 불일치 | "이메일 또는 비밀번호가 올바르지 않습니다" |
| 401 | 미활성화 계정 | "활성화되지 않은 계정입니다" |
| 403 | 비활성화 계정 | "비활성화된 계정입니다. 관리자에게 문의하세요" |
| 423 | 계정 잠금 | "로그인 시도 초과로 잠금되었습니다. N분 후 다시 시도해주세요" |

> 이메일 존재 여부와 비밀번호 오류를 동일 메시지로 반환 — 사용자 열거 공격 방지

---

### 6. 토큰 갱신

```
POST /api/v1/auth/refresh
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbGci..."
}
```

**처리 로직:**
1. RT 파싱 → token_hash(SHA-256) 생성
2. `refresh_tokens` 테이블에서 조회 (만료 여부 확인)
3. 기존 RT 삭제 (Rotation)
4. 새 AT + 새 RT 발급 → `refresh_tokens`에 저장

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGci...(새 토큰)",
    "refreshToken": "eyJhbGci...(새 토큰)",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

**Error Cases:**
| 코드 | 조건 | 메시지 |
|------|------|--------|
| 401 | 유효하지 않은 / 만료된 RT | "인증이 만료되었습니다. 다시 로그인해주세요" |

---

### 7. 로그아웃

```
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
```

**처리 로직:**
1. AT의 token_hash(SHA-256) → `token_blacklist`에 저장 (expires_at = AT 만료 시각)
2. 요청 바디에 RT 포함 시 `refresh_tokens`에서 삭제

**Response (200 OK):**
```json
{
  "status": "success",
  "message": "로그아웃되었습니다"
}
```

---

### 8. 내 정보 조회

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
    "email": "teacher01@school.ac.kr",
    "name": "김철수",
    "role": "TEACHER",
    "roleDetail": {
      "department": "수학",
      "currentClass": {
        "academicYear": 2026,
        "grade": 2,
        "classNum": 3,
        "isHomeroom": true
      },
      "assignments": [
        { "grade": 2, "classNum": 3, "subject": "수학", "academicYear": 2026 }
      ]
    },
    "createdAt": "2026-03-01T09:00:00"
  }
}
```

---

## 공통 응답 형식

### 성공
```json
{ "status": "success", "data": { ... } }
```

### 에러
```json
{
  "status": "error",
  "code": "AUTH_001",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다",
  "errors": [
    { "field": "password", "message": "비밀번호는 8자 이상이어야 합니다" }
  ]
}
```

---

## 에러 코드 체계

| 코드 | 설명 |
|------|------|
| AUTH_001 | 이메일/비밀번호 불일치 |
| AUTH_002 | 토큰 만료 |
| AUTH_003 | 유효하지 않은 토큰 |
| AUTH_004 | 권한 없음 |
| AUTH_005 | 비활성화 계정 |
| AUTH_006 | 계정 잠금 (로그인 5회 실패) |
| AUTH_007 | OTP 불일치 |
| AUTH_008 | OTP 만료 |
| AUTH_009 | OTP 폐기 (5회 초과) |
| AUTH_010 | 미활성화 계정 (OTP 발송 불가) |
| COMMON_001 | 유효성 검증 실패 |
| COMMON_002 | 리소스 없음 |
| COMMON_003 | 중복 리소스 |
| COMMON_004 | 동시 수정 충돌 (낙관적 락) |

---

## 유효성 검증 규칙

| 필드 | 규칙 |
|------|------|
| phone | 한국 전화번호 형식 (010-XXXX-XXXX) |
| otpCode | 6자리 숫자 |
| email | 이메일 형식, 최대 255자 |
| password | 최소 8자, 영문 대소문자 + 숫자 + 특수문자 |
| newPassword | 동일 |

---

## JWT 토큰 구조

### Access Token Payload
```json
{
  "sub": "1",
  "email": "teacher01@school.ac.kr",
  "role": "TEACHER",
  "iat": 1745000000,
  "exp": 1745001800
}
```

### Refresh Token Payload
```json
{
  "sub": "1",
  "tokenId": "uuid-v4",
  "iat": 1745000000,
  "exp": 1745604800
}
```

---

## Spring Security 필터 체인

```
HTTP Request
    │
    ▼
JwtAuthenticationFilter
    ├─ Authorization 헤더에서 AT 추출
    ├─ AT 파싱 + 서명 검증
    ├─ token_blacklist 조회 (로그아웃 여부)
    ├─ SecurityContext에 Authentication 설정
    │
    ▼
AuthorizationFilter
    ├─ /api/v1/auth/otp/send       → permitAll
    ├─ /api/v1/auth/activate       → permitAll
    ├─ /api/v1/auth/login          → permitAll
    ├─ /api/v1/auth/refresh        → permitAll
    ├─ /api/v1/auth/password/**    → permitAll
    ├─ /api/v1/admin/**            → hasRole(ADMIN)
    ├─ /api/v1/**                  → authenticated
    │
    ▼
Controller
```

---

## Redis → PostgreSQL 변경 비교

| 항목 | 기존 (Redis) | 변경 (PostgreSQL) |
|------|-------------|-------------------|
| RT 저장 | `auth:refresh:{userId}` → token | `refresh_tokens` 테이블 |
| AT 블랙리스트 | `auth:blacklist:{token}` → "true" | `token_blacklist` 테이블 |
| TTL 관리 | Redis 자동 만료 | `@Scheduled` 배치 (매일 03:00) |
| 인프라 | Redis 별도 서버 필요 | PostgreSQL 재사용, 추가 인프라 없음 |

> **선택 이유:** 100명 규모 학교 시스템에서 Redis 세션 저장소는 과잉. PostgreSQL 단일 DB로 장애 지점 감소, 운영 단순화.
