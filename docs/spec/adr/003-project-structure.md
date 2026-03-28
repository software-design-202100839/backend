# ADR-003: 프로젝트 디렉토리 구조

- **상태:** 개정 (초기 모노레포 → 분리 레포로 변경)
- **작성일:** 2025-03-12
- **최종 수정:** 2026-03-27 (분리 레포 현행화 + 인프라 ECS 반영)
- **작성자:** 이백엔드 (PM)

## 초기 결정: 모노레포 (Sprint 0)

Sprint 0 시작 시 프론트엔드와 백엔드를 하나의 레포에서 관리하기로 결정했다.
4인 팀 규모에서 멀티레포는 PR 리뷰와 CI/CD 관리 비용이 불필요하게 증가한다는 판단.

## 변경: 분리 레포 (Sprint 1 이후)

개발 진행 중 아래 이유로 **분리 레포**로 전환했다:

- **CI 독립성**: 백엔드 변경 시 프론트엔드 CI가 돌 필요 없고, 반대도 마찬가지
- **CD 독립성**: 각 레포에 독립적인 `cd.yml`로 ECR push → ECS 배포
- **빌드 속도**: 모노레포에서 전체 빌드 vs 분리 레포에서 해당 서비스만 빌드
- **Dockerfile 분리**: 각 레포 루트에 Dockerfile이 있으면 Docker context가 깔끔

**레포 구조:**
```
Organization: software-design-202100839

software-design-202100839/backend    → /mnt/c/Users/seung/workspace/sscm-backend
software-design-202100839/frontend   → /mnt/c/Users/seung/workspace/sscm-frontend
```

**docs/ 관리:**
- `docs/`는 로컬 워크스페이스(`/mnt/c/Users/seung/workspace/sscm/docs/`)에서 중앙 관리
- 양쪽 레포 `.gitignore`에 `docs/` 포함 → Git에 올라가지 않음
- Claude Code가 세션 시작 시 읽을 수 있도록 로컬에 유지
- `docker-compose.yml`은 backend 루트에 위치

## 현재 디렉토리 구조

### Backend 레포 (`software-design-202100839/backend`)
```
sscm-backend/
├── build.gradle.kts
├── Dockerfile                       # Sprint 2에서 추가
├── docker-compose.yml               # 로컬 개발환경 (PG + Redis)
├── src/
│   ├── main/java/com/sscm/
│   │   ├── SscmApplication.java
│   │   ├── common/                  # 공통 (예외, 응답, 설정)
│   │   │   ├── config/
│   │   │   ├── exception/
│   │   │   └── response/
│   │   ├── auth/                    # 인증/인가
│   │   ├── grade/                   # 성적 관리 (Sprint 1)
│   │   ├── student/                 # 학생부 (Sprint 1)
│   │   ├── feedback/                # 피드백 (Sprint 2)
│   │   ├── counsel/                 # 상담 내역 (Sprint 2)
│   │   ├── notification/            # 알림 (Sprint 2)
│   │   └── report/                  # 보고서 (Sprint 3)
│   └── main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── db/migration/            # Flyway 마이그레이션
│           └── V1__init_schema.sql
├── src/test/
└── .github/workflows/
    ├── ci.yml                       # Sprint 1에서 추가
    └── cd.yml                       # Sprint 2에서 추가
```

### Frontend 레포 (`software-design-202100839/frontend`)
```
sscm-frontend/
├── package.json
├── Dockerfile                       # Sprint 2에서 추가
├── vite.config.ts
├── tsconfig.json
├── eslint.config.js                 # ESLint 9 flat config
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── routes/
│   ├── features/                    # 기능별 폴더
│   │   ├── auth/
│   │   ├── grade/                   # Sprint 1
│   │   ├── student/                 # Sprint 1
│   │   ├── feedback/                # Sprint 2
│   │   ├── counsel/                 # Sprint 2
│   │   ├── notification/            # Sprint 2
│   │   └── report/                  # Sprint 3 (새 프론트엔드 스택 적용)
│   ├── components/
│   ├── hooks/
│   ├── services/
│   ├── store/
│   └── types/
├── e2e/                             # Playwright (Sprint 2)
└── .github/workflows/
    ├── ci.yml
    └── cd.yml
```

### 로컬 docs 디렉토리 (Git 미포함)
```
sscm/                                # /mnt/c/Users/seung/workspace/sscm
├── CLAUDE.md
├── README.md
├── .claude/
│   ├── personas/README.md
│   └── internal/
├── docs/
│   ├── status.md
│   ├── requires/requirements.md
│   ├── spec/
│   │   ├── adr/                     # ADR-001~003
│   │   ├── api/                     # API 스펙
│   │   ├── db/                      # ERD, 마이그레이션
│   │   └── infra/                   # 아키텍처
│   ├── sprint/                      # 로드맵, 스프린트 계획
│   ├── tasks/                       # 활성 태스크
│   ├── complete/                    # 완료 태스크
│   ├── history/                     # 세션 히스토리
│   ├── troubleshooting/             # 트러블슈팅
│   ├── jira/                        # Jira 이슈 기록
│   └── test/                        # 테스트 계획
└── infra/                           # Sprint 3에서 추가
    └── ecs/                         # ECS Task Definition, ALB 설정 등
```

## 패키지 구조 근거

### Backend: 도메인별 패키지 (`com.sscm.{domain}.{layer}`)
- 기능 단위로 코드가 응집되어 수정 시 영향 범위 명확
- 각 팀원이 담당 도메인 폴더에서 독립 작업 가능
- 향후 마이크로서비스 분리 시 도메인 단위 추출 가능

### Frontend: feature-based 구조
- 기능별 컴포넌트/훅/타입이 한 폴더에 모임
- 기능 간 결합도를 낮추고 순차 개발 가능
- 공통 UI는 `/components/`, 공통 로직은 `/hooks/`와 `/services/`에 분리
