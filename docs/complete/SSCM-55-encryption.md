# SSCM-55: 개인정보 암호화 (AES-256-GCM)

- Assignee: 이백엔드 (Backend Lee)
- Sprint: 3
- Status: in-progress
- Created: 2026-03-28
- Commit date: 2026-03-31
- Epic: SSCM-9 (보안 강화)

## Requirement
프로덕션 배포 전 개인정보 보호법 대응. DB에 저장되는 개인정보를 암호화해야 한다.

## Why Now?
> 실제 서버에 올리기 전에 보안을 적용해야 한다. 배포 후 암호화하면 기존 데이터 마이그레이션이 복잡해진다.

## Design

### 암호화 전략: 2계층 보호

| 계층 | 방식 | 대상 | 보호 수준 |
|------|------|------|----------|
| **컬럼 레벨** | AES-256-GCM (AttributeConverter) | PII + 민감 텍스트 | DB 접근해도 평문 노출 안 됨 |
| **스토리지 레벨** | AWS RDS 암호화 at Rest (KMS) | 전체 DB (성적 포함) | 디스크 레벨 보호, 쿼리 정상 동작 |

### 컬럼 레벨 암호화 대상
| 엔티티 | 필드 | 이유 | 비고 |
|--------|------|------|------|
| User | email | 개인 식별 정보 | email_hash(SHA-256) 추가 — 조회/UNIQUE용 |
| User | phone | 개인 식별 정보 | |
| Counseling | content | 상담 내용 (고도 민감) | |
| Counseling | nextPlan | 상담 계획 | |

### 컬럼 레벨 암호화 제외 (스토리지 레벨로 보호)
| 엔티티 | 필드 | 제외 이유 |
|--------|------|----------|
| Score | score, gradeLetter, rank | 석차 계산, 통계, 정렬 등 SQL 연산 필수 |
| StudentRecord | content (JSONB) | GIN 인덱스 활용, 카테고리별 검색 필요 |

### 기술 선택
- **AES-256-GCM** (인증된 암호화, IV 자동 생성, 12바이트)
- JPA `@Convert(converter = EncryptedStringConverter.class)`
- 키: 환경변수 `ENCRYPTION_KEY` (Parameter Store에서 주입)
- 이메일 조회: `email_hash` 컬럼 (SHA-256, UNIQUE)

### Tradeoff — 암호화 방식 선택

#### 1. 전체 전략: 컬럼 vs 스토리지 vs 혼합

| 방식 | 장점 | 단점 |
|------|------|------|
| 컬럼 레벨만 | DB 접근해도 안전 | 연산 필요 필드 기능 파괴 |
| 스토리지 레벨만 (TDE/RDS) | 쿼리 정상 동작, 변경 최소 | DB 접속하면 평문 노출 |
| **혼합 (선택)** | PII는 컬럼 레벨로 강력 보호, 연산 데이터는 스토리지 보호 | 구현 복잡도 약간 증가 |

> **판단**: PII(이메일, 전화번호, 상담내용)는 유출 시 직접적 피해가 크므로 컬럼 레벨 필수.
> 성적은 학생 FK로 연결되어 PII 암호화로 간접 보호되며, SQL 연산이 필수이므로 스토리지 레벨로 보호.

#### 2. 암호화 알고리즘: AES-256-GCM vs AES-256-CBC

| 방식 | 장점 | 단점 |
|------|------|------|
| AES-256-CBC | 구현 단순 | 무결성 검증 없음, 패딩 오라클 공격 취약 |
| **AES-256-GCM (선택)** | 인증된 암호화 (무결성+기밀성), 패딩 불필요 | 약간 복잡 |

> **판단**: GCM은 암호화와 무결성 검증을 동시에 제공. 보안 표준(NIST SP 800-38D) 권장.

#### 3. 암호화 위치: JPA AttributeConverter vs DB 레벨 (pgcrypto)

| 방식 | 장점 | 단점 |
|------|------|------|
| **JPA AttributeConverter (선택)** | 코드 변경 최소, DB 독립적, 키 관리 앱 레벨 | 검색 시 복호화 필요 |
| DB pgcrypto | DB에서 직접 처리 | 앱-DB 결합도 증가, 키가 SQL에 노출 |

> **판단**: 앱 레벨 제어로 DB 벤더 독립성 유지. 키가 SQL 쿼리에 노출되지 않아 더 안전.

#### 4. 이메일 조회: 해시 vs 결정적 암호화 vs 전체 스캔

| 방식 | 장점 | 단점 |
|------|------|------|
| **SHA-256 해시 컬럼 (선택)** | 빠른 조회, UNIQUE 유지 | 해시 컬럼 추가 필요 |
| 결정적 암호화 (SIV) | 추가 컬럼 불필요 | 동일 평문 = 동일 암호문 (패턴 노출) |
| 전체 복호화 후 비교 | 구현 단순 | O(n) 성능, 대규모 불가 |

> **판단**: SHA-256 해시는 단방향이라 원문 복원 불가. 조회 성능 O(1) 유지. 결정적 암호화는 패턴 분석에 취약.

## Subtasks
- [ ] EncryptionUtil 클래스 (AES-256-GCM + SHA-256)
- [ ] EncryptedStringConverter (JPA AttributeConverter)
- [ ] 대상 엔티티 필드에 @Convert 적용
- [ ] Flyway 마이그레이션 (컬럼 타입 확장 + email_hash 추가)
- [ ] 단위 테스트 (암호화/복호화 라운드트립)
- [ ] 기존 테스트 통과 확인
- [ ] ADR 문서 작성

## Acceptance Criteria
- [ ] 암호화된 값이 DB에 저장됨 (평문 노출 안 됨)
- [ ] API 응답에서는 복호화된 평문 반환
- [ ] 이메일 로그인 정상 동작 (해시 기반 조회)
- [ ] 기존 JUnit 테스트 전체 통과
