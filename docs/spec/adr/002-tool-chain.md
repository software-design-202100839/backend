# ADR-002: 도구 체인 선정 및 제외 근거

- **상태:** 확정
- **작성일:** 2025-03-12
- **최종 수정:** 2026-03-27 (인프라 ECS 전환, 모니터링 이원화, SonarCloud 전환)
- **작성자:** 이백엔드 (PM), 이데브 (DevOps)

## 맥락 (Context)
수업에서 제시된 도구 목록이 있으며, 이 중 프로젝트에 실제로 필요한 것만 선별하고, 목록에 없더라도 필요한 도구는 추가한다. 모든 선택/제외에는 명확한 이유가 있어야 한다.

## 사용하는 도구

### 코드 품질
| 도구 | 이유 |
|------|------|
| **ESLint** | React+TS 프론트엔드 코드 컨벤션 통일. 4명 페르소나가 각자 코드를 짜므로 스타일 통일 필수. |
| **SonarCloud** | 백엔드(Java) + 프론트엔드(TS) 코드 품질 정량 측정. 커버리지, 중복, 보안 핫스팟을 대시보드로 시각화. 오픈소스 무료, 서버 관리 불필요. |

### API 개발/문서화
| 도구 | 이유 |
|------|------|
| **Swagger (SpringDoc OpenAPI)** | Spring Boot 네이티브 통합. API 명세를 코드에서 자동 생성. 프론트엔드 개발자가 백엔드 API를 보고 개발하는 협업 시나리오 지원. |
| **Postman + Postbot** | Postman은 API 수동 테스트, Postbot은 AI 기반 테스트 케이스 자동 생성. 수십 개 API의 엣지 케이스를 빠르게 커버. |

### 테스트
| 도구 | 이유 |
|------|------|
| **JUnit 5** | Spring Boot 백엔드 단위/통합 테스트의 표준. |
| **JaCoCo** | Java 코드 커버리지 측정. SonarCloud에 데이터를 넘겨 품질 게이트 설정. |
| **Testcontainers** | 실제 PostgreSQL + Redis 컨테이너로 통합 테스트. H2로는 JSONB 등 PG 전용 기능이 동작하지 않으므로 프로덕션 동일 환경 테스트 필수. |
| **Playwright** | E2E 브라우저 테스트. 요구사항 명세서의 사용자 시나리오 3개를 그대로 테스트 케이스로 구현. Cypress 대비 멀티 브라우저 지원, Selenium 대비 빠르고 설정 간편. |
| **k6** | 부하 테스트. 배포 후 "동시 100명 접속 시 응답시간 X초 이내" 수치 확보. |

### 보안
| 도구 | 이유 |
|------|------|
| **Snyk** | 빌드 타임 의존성 취약점 스캔. 학생 개인정보를 다루므로 보안 요구사항 명시적. GitHub Actions에 한 줄 통합. |
| **Burp Suite** | DAST(동적 보안 테스트). Snyk이 코드/의존성 레벨이라면 Burp Suite는 실제 구동 중인 앱 보안 검증. "런타임에서도 검증했다"는 증거. |

### CI/CD
| 도구 | 이유 |
|------|------|
| **GitHub Actions** | CI/CD 전체 파이프라인. PR → 린트 → 테스트 → SonarCloud → Snyk → Docker 빌드 → ECR 푸시 → ECS 서비스 업데이트. GitHub 네이티브 통합으로 별도 도구 불필요. |

### 컨테이너/인프라
| 도구 | 이유 |
|------|------|
| **Docker** | 프론트/백엔드 컨테이너 이미지 빌드. |
| **AWS ECR** | 프라이빗 컨테이너 레지스트리. ECS와 같은 AWS IAM 체계 안에서 보안 일원화. |
| **AWS ECS Fargate** | 서버리스 컨테이너 오케스트레이션. 컨테이너 정의만 하면 AWS가 인프라 관리. 2~3개 서비스 규모에 최적. |
| **AWS ALB** | HTTPS 터미네이션, 경로 기반 라우팅(/api → backend, / → frontend), 헬스체크를 모두 제공. ECS Fargate 네이티브 연동. |
| **Nginx** | 프론트엔드 Docker 이미지 내 정적 파일 서빙 용도로만 사용. Ingress Controller 역할은 ALB가 대체. |
| **AWS Parameter Store** | 시크릿 관리. SecureString 타입으로 암호화 지원. ECS Task Definition에서 직접 참조 가능. 표준 파라미터 무료. |

### 모니터링/운영
| 도구 | 이유 |
|------|------|
| **Prometheus + Grafana** | 상시 모니터링 메인. 무료, Spring Boot Actuator + Micrometer가 Prometheus 포맷 네이티브 지원. ECS에서 sidecar 패턴으로 메트릭 수집. |
| **Dynatrace** | APM 보조. AI 기반 근본 원인 분석. 15일 무료 체험으로 상용 APM 비교 분석 ADR 작성 → 프로젝트 깊이 상승. |
| **PagerDuty** | Prometheus AlertManager → PagerDuty 연동. 이상 감지 시 인시던트 생성. 무료 tier로 충분. |

### DB 관리
| 도구 | 이유 |
|------|------|
| **Flyway** | DB 마이그레이션 버전 관리. 스키마 변경도 코드처럼 관리. |

### 프로젝트 관리
| 도구 | 이유 |
|------|------|
| **Jira** | 스프린트/이슈 관리. 조교가 매일 확인하는 도구. |

---

## 사용하지 않는 도구 (제외 근거)

| 도구 | 제외 이유 |
|------|-----------|
| **EKS (Kubernetes)** | EKS 컨트롤 플레인만 월 $73. 2~3개 서비스 규모에 클러스터 업그레이드, 노드 관리, RBAC 설정 등 운영 오버헤드가 과함. ECS Fargate가 비용 1/5, 운영 부담 1/3. EKS는 마이크로서비스 10개 이상, 멀티 클라우드, 서비스 메시(Istio) 필요 시 적합. |
| **Argo CD** | Kubernetes 전용 GitOps 도구. ECS 환경에서는 불필요. GitHub Actions에서 `aws ecs update-service`로 직접 배포하면 하나의 워크플로우 파일에 완결. GitOps 원칙은 ECS Task Definition을 Git에서 관리하는 것으로 충족. |
| **Nginx Ingress Controller** | ECS Fargate + ALB 조합에서 Ingress Controller 불필요. ALB가 HTTPS 터미네이션, 경로 기반 라우팅, 헬스체크를 모두 제공. |
| **SonarQube (셀프호스트)** | SonarCloud와 동일 기능이나 별도 서버 운영 필요. SonarCloud는 오픈소스 프로젝트 무료, GitHub Actions 연동이 더 간단. |
| **AWS Secrets Manager** | 시크릿당 과금. Parameter Store 표준 파라미터는 무료이면서 SecureString 암호화 지원. ECS Task Definition에서 동일하게 참조 가능. |
| **Jenkins** | GitHub Actions가 전체 CI/CD를 커버함. Jenkins를 추가하면 별도 서버 운영 필요. 4인 팀 + 단일 레포에서 CI/CD 도구 2개 운영은 관리 포인트만 늘리는 오버엔지니어링. |
| **GitLab CI** | 소스 코드가 GitHub에 있는데 GitLab으로 미러링해서 CI를 돌리는 것은 불필요한 복잡성. |
| **Docker Hub** | AWS ECR 사용. ECR은 ECS와 같은 AWS IAM 체계 안에서 보안 일원화. Docker Hub는 rate limit 문제. GHCR은 ECS에서 pull 시 secret 설정 추가 필요. |
| **Kubiya** | AI 기반 DevOps 자동화 도구이나, ECS 서비스 2~3개 규모에서는 자동화할 복잡성 자체가 없음. |
| **Sysdig** | 컨테이너 런타임 보안 모니터링. Snyk(빌드 타임) + Burp Suite(앱 레벨) + Prometheus(런타임)가 이미 3단계 보안/모니터링 커버. Sysdig 추가 시 모니터링 과포화. |

## 결과 (Consequences)
- GitHub Actions 단일 파이프라인으로 CI + CD 관리 포인트 최소화.
- ECS Fargate + ALB + ECR AWS 생태계 일원화로 인프라 운영 간소화.
- SonarCloud로 서버 관리 없이 코드 품질 정량 측정.
- Prometheus + Grafana(상시 무료) + Dynatrace(보조 비교)로 "오픈소스 vs 상용 APM 비교 분석" ADR 작성 가능.
- 3단계 보안 체계(Snyk → Burp Suite → Prometheus + PagerDuty)로 빌드/런타임/운영 전 구간 커버.
- 제외 도구에 대해 "왜 안 쓰는지" 명확한 근거 확보.
