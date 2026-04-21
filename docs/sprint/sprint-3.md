# Sprint 3: 클라우드 배포 + 보안

- **기간:** 2026-03-29 ~ 2026-04-04
- **목표:** ECS Fargate 프로덕션 배포 + 개인정보 암호화 + DAST 보안 테스트

## 이슈 목록

### 반드시 (Must) — 배포 + 보안
| Jira | 담당 | 제목 | 결과 |
|------|------|------|------|
| SSCM-53 | 이데브 | ECS Fargate 클러스터 + Task Definition | 완료 (3/29) |
| SSCM-54 | 이데브 | ALB 경로 라우팅 + Parameter Store | 완료 (3/30) |
| SSCM-55 | 이백엔드 | 개인정보 암호화 (AES-256-GCM) | 완료 (3/31) |
| SSCM-56 | 이데브 | CD 완성 (ECR→ECS) + 프로덕션 배포 | 완료 (4/1~4/3) |
| SSCM-57 | 이큐에이 | OWASP ZAP DAST (Burp Suite 대체) | 완료 (4/3) |

### 가능하면 (Nice-to-have)
| Jira | 담당 | 제목 | 결과 |
|------|------|------|------|
| SSCM-58 | 이백엔드 | @Version + @Retryable | 미착수 → Sprint 4 이월 |

## 종료 기준 달성 여부
- [x] ECS Fargate에서 backend + frontend 컨테이너 Running
- [x] ALB URL로 전 기능 접속 가능
- [x] develop push → 자동 배포 (ECR→ECS) 동작
- [x] 개인정보 암호화 적용 (DB 평문 노출 안 됨) — AES-256-GCM, PR #4
- [x] DAST 보안 테스트 완료 (OWASP ZAP: FAIL 0, WARN 9, Critical 0, High 0)
- [x] JUnit 테스트 전체 통과 유지 — 106개 (0 fail)
- [x] Playwright E2E 프로덕션 7/7 통과 (32.8s)

> **DAST 도구 변경:** 원래 계획은 Burp Suite였으나 OWASP ZAP baseline scan으로 대체.
> ZAP은 오픈소스이며 CI 통합이 용이하고, 결과 기준(Critical 0, High 0)을 동일하게 충족.

## 주요 성과

### 1. AWS 인프라 구축 (CloudFormation IaC)
- **3개 스택:** sscm-alb (ALB+리스너), sscm-data (RDS+Redis), sscm-ecs (Fargate 클러스터)
- **비용:** 전체 가동 시 ~$1.5/일, ECS 스택 삭제로 과금 중지 가능
- 모든 인프라를 CloudFormation 템플릿으로 관리 (재현 가능)

### 2. CD 파이프라인 완성
- develop push → Docker 빌드 → ECR push → ECS force-new-deployment → services-stable 대기
- Backend PR #5, Frontend PR #3

### 3. 프로덕션 안정화
- actuator 헬스체크 인증 차단 해결 (심플 /health 엔드포인트)
- 학생 회원가입 admissionYear NPE 수정
- API baseURL 환경변수 분리 (VITE_API_BASE_URL)
- ECS HealthCheckGracePeriodSeconds 120초 설정

### 4. 학생 UX 개선 (추가 작업)
- 로그인 응답에 roleEntityId 추가
- 교사 전용 학생 선택 드롭다운, 학생은 자동 조회

### 5. 보안
- AES-256-GCM 개인정보 암호화 (이름, 전화번호, 이메일)
- OWASP ZAP DAST: Critical 0, High 0 달성

## 트러블슈팅
- 10건 기록: `docs/troubleshooting/SSCM-56-production-deploy.md`
- 주요: ECS 퍼블릭 IP, 헬스체크 경로, Redis health indicator, Parameter Store 설정

## 회고

### 잘한 것
- CloudFormation IaC로 인프라 전체를 코드 관리 → 재현/삭제 용이
- 프로덕션 배포 후 즉시 E2E 검증 (Playwright 7/7)
- 트러블슈팅 10건을 체계적으로 기록
- 비용 관리: 사용하지 않을 때 스택 삭제로 과금 중지

### 아쉬운 것
- SSCM-58 (@Version + @Retryable) 2회 연속 이월 (Sprint 2 → 3 → 4)
- Burp Suite → ZAP 변경이 사전 합의 없이 진행 (결과적으론 적절)
- ECS 헬스체크 문제로 반나절 소요 (HealthCheckGracePeriodSeconds 미설정)

### 다음에 개선할 점
- 헬스체크 설정을 CloudFormation 템플릿에 포함 (콘솔 수동 수정 지양)
- Nice-to-have 이월 항목은 스프린트 초반에 일정 확보 또는 명시적 드롭

## 미완료 → Sprint 4 이월
| Jira | 제목 | 사유 |
|------|------|------|
| SSCM-58 | @Version + @Retryable | Must 항목에 집중, 시간 부족 |

## 발견된 개선 사항 (Sprint 4 검토)
- 학생부 content JSON 표시 → 카테고리별 렌더링 필요
- 피드백 isVisibleToStudent 기본값 → 학생 공개 로직 점검
- 상담내역 학생 권한 조회 허용 (@PreAuthorize에 STUDENT 추가)
