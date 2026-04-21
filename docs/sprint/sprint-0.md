# Sprint 0: 앱 기반 구축 + 인증

- **기간:** Week 2~3 (2026.03.12 ~ 2026.03.25)
- **목표:** 로컬에서 프론트+백엔드+DB가 돌아가고, 로그인이 되는 상태

## 이슈 목록

### 이백엔드
| Jira | 제목 | 왜 지금? |
|------|------|----------|
| SSCM-12 | GitHub 모노레포 생성 + 브랜치 전략 | 코드 저장소 필요 |
| SSCM-13 | ERD 설계 + Flyway 마이그레이션 | API에 DB 필요 |
| SSCM-14 | Spring Boot 프로젝트 초기화 | 백엔드 기반 |
| SSCM-19 | Spring Security JWT 인증 구현 | 모든 기능의 전제조건 |
| SSCM-20 | 사용자 Role 구현 및 권한 분리 | 이후 기능 개발 전제 |
| SSCM-25 | ADR 문서 리뷰 및 최종 커밋 | 기술 스택 근거 문서화 |

### 이프론트
| Jira | 제목 | 왜 지금? |
|------|------|----------|
| SSCM-15 | React+TS+Vite 프로젝트 초기화 | 프론트엔드 기반 |
| SSCM-26 | ESLint(Airbnb) + Prettier 설정 | 코드 규칙 선행 |
| SSCM-27 | 로그인 페이지 UI | 인증 API 연동 |
| SSCM-28 | 회원가입 페이지 UI | 사용자 생성 |
| SSCM-29 | 라우팅 + 인증 가드 | 접근 제어 |

### 이데브
| Jira | 제목 | 왜 지금? |
|------|------|----------|
| SSCM-30 | Docker Compose (PG + Redis) | 로컬 DB 필요 |
| SSCM-31 | Redis 설정 (Spring Boot 연동) | JWT 블랙리스트 |

### 이큐에이
| Jira | 제목 | 왜 지금? |
|------|------|----------|
| SSCM-32 | 인증 API Postman 컬렉션 | API 테스트 |
| SSCM-33 | Postbot 엣지 케이스 테스트 | 테스트 확장 |

## 의존 관계
```
SSCM-12 (레포) ✅
  ├→ SSCM-14 (Spring Boot) ✅ → SSCM-13 (ERD) ✅ ──┐
  ├→ SSCM-30 (Docker Compose) ✅ → SSCM-31 (Redis) ✅┘→ SSCM-19 (JWT) ✅ → SSCM-20 (Role) ✅
  ├→ SSCM-15 (React) ✅ → SSCM-26 (ESLint) ✅ → SSCM-27~29 (UI) ✅
  └→ SSCM-25 (ADR) ✅
SSCM-19 → SSCM-32 (Postman) ✅ → SSCM-33 (Postbot) ✅
```

## 종료 기준
- [x] `docker-compose up -d` → PG + Redis 구동 (SSCM-30)
- [x] Spring Boot 앱 기동 + Swagger UI 접속 (SSCM-14, SSCM-19)
- [x] React 앱 기동 + 로그인 화면 렌더링 (SSCM-15, SSCM-27)
- [x] 3 Role 회원가입 → 로그인 → 토큰 발급 (SSCM-19, SSCM-28)
- [x] 로그아웃 → Redis 블랙리스트 동작 (SSCM-19, SSCM-31)
- [x] 미인증 → 로그인 리다이렉트 (SSCM-29)
- [x] Postman 인증 테스트 통과 (SSCM-32, SSCM-33)

## 실제 구현 사항 메모
- Spring Boot **3.5.0** 사용 (Spring Initializr 최소 지원 버전 변경으로 3.2.x에서 업그레이드)
- React **19** + Vite **8** (생성 시점 최신 안정 버전)
- ESLint **9** flat config (Airbnb 스타일 규칙 적용, 레거시 config 대신 flat 사용)
