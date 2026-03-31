# ADR-004: 개인정보 암호화 전략

- 상태: 승인
- 작성일: 2026-03-31
- 작성자: 이백엔드 (Backend Lee)
- 관련 이슈: SSCM-55

## 배경

프로덕션 배포 전 개인정보 보호법 대응이 필요하다. DB에 저장되는 개인정보가 평문으로 노출되면 안 되며, 배포 후 암호화하면 기존 데이터 마이그레이션이 복잡해지므로 배포 전에 적용한다.

SSCM에는 두 종류의 민감 데이터가 있다:
1. **PII (개인식별정보)**: 이메일, 전화번호 — 유출 시 직접적 피해
2. **민감 텍스트**: 상담 내용/계획 — 학생의 사적 정보
3. **연산 필요 데이터**: 성적, 석차 — SQL 집계/정렬 필수

## 결정 1: 2계층 암호화 (혼합 전략)

### 선택지

| 방식 | 장점 | 단점 |
|------|------|------|
| A. 컬럼 레벨 전체 적용 | DB 접근해도 전부 안전 | 성적 연산/정렬/집계 불가, 앱에서 전체 로드 후 처리 → 성능 저하 |
| B. 스토리지 레벨만 (AWS RDS 암호화) | 쿼리 100% 정상, 코드 변경 없음 | DB에 접속하면 평문 그대로 노출 |
| **C. 혼합 (선택)** | PII는 DB 접근해도 안전, 연산 데이터는 쿼리 정상 | 구현 복잡도 약간 증가 |

### 판단 근거

- **PII (이메일, 전화번호, 상담내용)**: 유출 시 직접적 피해가 크고, SQL 검색이 필요하지 않거나 해시로 대체 가능 → 컬럼 레벨 AES-256-GCM
- **성적 (score, gradeLetter, rank)**: 석차 계산(`ORDER BY score DESC`), 통계(`AVG(score)`), 등급 필터링 등 SQL 연산이 필수. 컬럼 레벨 암호화 시 이 모든 기능이 깨짐 → 스토리지 레벨 (RDS 암호화) + `@PreAuthorize` 접근 제어
- 성적은 student FK로 연결되어 있어, PII(이메일, 이름)가 암호화되면 간접적으로 보호됨 (성적만으로는 누구의 것인지 알 수 없음)

### 최종 대상

| 계층 | 대상 | 방식 |
|------|------|------|
| 컬럼 레벨 | User.email, User.phone | AES-256-GCM (JPA AttributeConverter) |
| 컬럼 레벨 | Counseling.content, Counseling.nextPlan | AES-256-GCM (JPA AttributeConverter) |
| 스토리지 레벨 | Score 등 전체 DB | AWS RDS 암호화 at Rest (KMS) |
| 접근 제어 | 모든 API | Spring Security @PreAuthorize |

## 결정 2: AES-256-GCM (알고리즘)

### 선택지

| 알고리즘 | 장점 | 단점 |
|---------|------|------|
| AES-256-CBC | 구현 단순, 널리 알려짐 | 무결성 검증 없음, 패딩 오라클 공격 취약, 별도 HMAC 필요 |
| **AES-256-GCM (선택)** | AEAD — 기밀성 + 무결성 동시 보장, 패딩 불필요, NIST 표준 | IV 재사용 시 보안 파괴 (SecureRandom으로 해결) |
| ChaCha20-Poly1305 | 소프트웨어 성능 우수 | JDK 17 기본 지원이지만 생태계 지원 적음 |

### 판단 근거

- GCM은 암호화와 무결성 검증을 한 번에 수행 (Authenticated Encryption with Associated Data)
- NIST SP 800-38D 권장 표준
- Java `javax.crypto`에서 기본 지원, 외부 라이브러리 불필요
- IV(12바이트)를 `SecureRandom`으로 생성하여 IV 재사용 방지

## 결정 3: JPA AttributeConverter (암호화 위치)

### 선택지

| 위치 | 장점 | 단점 |
|------|------|------|
| **JPA AttributeConverter (선택)** | 코드 변경 최소 (`@Convert` 한 줄), DB 벤더 독립적, 키가 SQL에 노출 안 됨 | 검색 시 전체 복호화 필요 (해시 컬럼으로 해결) |
| DB pgcrypto 확장 | DB 내에서 직접 처리, SQL 함수로 암/복호화 | 키가 SQL 쿼리에 노출, PostgreSQL 종속, 앱-DB 결합도 증가 |
| Spring AOP | 유연한 적용 | 과도한 추상화, 디버깅 어려움, 명시적이지 않음 |

### 판단 근거

- `@Convert` 한 줄로 필드에 선언적 적용 → 코드 리뷰 명확
- 암호화 키가 SQL 쿼리에 절대 노출되지 않음 (pgcrypto는 `pgp_sym_encrypt(data, 'key')` 형태로 키가 SQL에 포함)
- 추후 DB를 PostgreSQL에서 다른 DB로 교체해도 암호화 로직 변경 불필요

## 결정 4: SHA-256 해시 컬럼 (이메일 조회)

### 문제

AES-256-GCM은 랜덤 IV를 사용하므로 같은 이메일을 암호화해도 매번 다른 암호문이 생성된다. 따라서:
- `WHERE email = ?` 조회 불가
- `UNIQUE` 제약 조건 무의미

### 선택지

| 방식 | 장점 | 단점 |
|------|------|------|
| **SHA-256 해시 컬럼 (선택)** | O(1) 조회, UNIQUE 유지, 단방향이라 원문 복원 불가 | 해시 컬럼 추가 필요 (email_hash) |
| 결정적 암호화 (AES-SIV) | 추가 컬럼 불필요, 동일 입력 → 동일 암호문 | 패턴 분석 가능 (빈도 공격), 보안 약화 |
| 전체 스캔 후 복호화 비교 | 구현 단순 | O(n) 성능, 사용자 수 증가 시 불가 |
| Blind Index (HMAC) | 해시보다 보안 강화 | 추가 키 관리 필요, 복잡도 증가 |

### 판단 근거

- 이메일은 로그인 시 매 요청마다 조회 → O(1) 필수
- SHA-256은 단방향이므로 해시에서 원문 복원 불가 → UNIQUE 인덱스로 충분
- 결정적 암호화는 같은 이메일이 항상 같은 암호문 → 빈도 분석으로 추측 가능
- 학교 시스템 규모(수천 명)에서 Blind Index(HMAC)까지는 과잉

## 구현 요약

```
User.email → [앱] AES-256-GCM 암호화 → [DB] TEXT (암호문)
User.email → [앱] SHA-256 해시 → [DB] email_hash VARCHAR(64) UNIQUE
User.phone → [앱] AES-256-GCM 암호화 → [DB] TEXT (암호문)
Counseling.content → [앱] AES-256-GCM 암호화 → [DB] TEXT (암호문)
Counseling.nextPlan → [앱] AES-256-GCM 암호화 → [DB] TEXT (암호문)
```

## 영향

- Flyway V2 마이그레이션: email/phone → TEXT, email_hash 추가, UNIQUE 인덱스 이동
- UserRepository: `findByEmail()` → `findByEmailHash()`
- AuthService: 로그인/회원가입 시 SHA-256 해시로 조회
- 키 관리: `ENCRYPTION_KEY` 환경변수 (AWS Parameter Store)
- 기존 테스트 106개 전체 통과 확인

## 참고

- NIST SP 800-38D: Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM)
- KISA 개인정보 암호화 조치 안내서
- OWASP Cryptographic Storage Cheat Sheet
