# SSCM-55: 개인정보 암호화 (AES-256-GCM)

- Assignee: 이백엔드 (Backend Lee)
- Sprint: 3
- Status: todo
- Created: 2026-03-28
- Commit date: 2026-03-31
- Epic: SSCM-9 (보안 강화)

## Requirement
프로덕션 배포 전 개인정보 보호법 대응. DB에 저장되는 개인정보를 암호화해야 한다.

## Why Now?
> 실제 서버에 올리기 전에 보안을 적용해야 한다. 배포 후 암호화하면 기존 데이터 마이그레이션이 복잡해진다.

## Design
### 암호화 대상 필드
| 엔티티 | 필드 | 이유 |
|--------|------|------|
| User | email | 개인 식별 정보 |
| User | phone | 개인 식별 정보 |
| StudentRecord | address | 개인 식별 정보 |
| StudentRecord | guardianContact | 보호자 연락처 |

### 기술 선택
- **AES-256-GCM** (인증된 암호화, IV 자동 생성)
- JPA `@Convert(converter = EncryptedStringConverter.class)`
- 키: 환경변수 `ENCRYPTION_KEY` (Parameter Store에서 주입)

### Tradeoff
| 방식 | 장점 | 단점 |
|------|------|------|
| JPA AttributeConverter | 코드 변경 최소, 투명한 암/복호화 | 검색 시 복호화 필요 |
| DB 레벨 (pgcrypto) | DB에서 직접 암/복호화 | 앱과 DB 결합도 증가 |
| → **AttributeConverter 선택**: 앱 레벨 제어, DB 독립적, 키 관리 용이 |

## Subtasks
- [ ] EncryptionUtil 클래스 (AES-256-GCM)
- [ ] EncryptedStringConverter (JPA AttributeConverter)
- [ ] 대상 엔티티 필드에 @Convert 적용
- [ ] Flyway 마이그레이션 (컬럼 타입 VARCHAR→TEXT 확장)
- [ ] 단위 테스트 (암호화/복호화 라운드트립)
- [ ] 기존 테스트 통과 확인

## Acceptance Criteria
- [ ] 암호화된 값이 DB에 저장됨 (평문 노출 안 됨)
- [ ] API 응답에서는 복호화된 평문 반환
- [ ] 기존 JUnit 테스트 전체 통과
