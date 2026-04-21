# SSCM-56 프로덕션 배포 트러블슈팅

## 1. ECR 리포지토리 미생성
- **증상**: `name unknown: The repository with name 'sscm-backend' does not exist in the registry`
- **원인**: CD 워크플로우가 ECR push를 시도했으나 ECR 리포지토리가 AWS에 없었음
- **해결**: AWS 콘솔에서 `sscm-backend`, `sscm-frontend` ECR 리포지토리 수동 생성 (Private, ap-northeast-2)

## 2. ECS 클러스터 미생성
- **증상**: `ClusterNotFoundException: Cluster not found`
- **원인**: CloudFormation 템플릿은 있으나 실제 AWS에 스택 배포를 안 한 상태
- **해결**: `cfn-alb.yml` → `cfn-ecs-cluster.yml` 순서로 CloudFormation 스택 생성

## 3. Fargate 태스크 인터넷 접근 불가
- **증상**: ECS 서비스 CREATE_IN_PROGRESS에서 30분 이상 멈춤 → ROLLBACK
- **원인**: `AssignPublicIp: DISABLED`로 설정되어 Fargate 태스크가 ECR에서 이미지를 pull 못함
- **해결**: `cfn-ecs-cluster.yml`에서 `AssignPublicIp: ENABLED`로 변경

## 4. JWT Secret 키 길이 부족
- **증상**: `WeakKeyException: The specified key byte array is 88 bits which is not secure enough`
- **원인**: Parameter Store `/sscm/prod/jwt-secret` 값이 29자(232비트) — HMAC-SHA는 최소 256비트 필요
- **해결**: 48자 이상의 시크릿으로 교체 (`sscm-jwt-secret-key-2026-production-secure-value`)

## 5. DB 비밀번호 불일치
- **증상**: `FATAL: password authentication failed for user "sscm"`
- **원인**: ALB 스택 생성 시 임시 비밀번호(`TempPass123!`)를 넣었고, Data 스택의 RDS는 다른 비밀번호(`sscm1234!`)로 생성
- **해결**: RDS 마스터 비밀번호 변경 + Parameter Store `/sscm/prod/db-password` 동기화

## 6. Encryption Key 길이 부족
- **증상**: `ENCRYPTION_KEY must be 32 bytes (256 bits), got 29`
- **원인**: Base64 디코딩 후 29바이트인 값을 넣었음. AES-256은 정확히 32바이트 필요
- **해결**: `python3 -c "import base64,os; print(base64.b64encode(os.urandom(32)).decode())"` 으로 생성한 키로 교체

## 7. Actuator 헬스체크 인증 차단
- **증상**: ALB 타겟 그룹 "0 정상 1 비정상", 태스크 반복 종료 (`Task failed ELB health checks`)
- **원인**: Spring Security에서 `/actuator/**`가 `permitAll`에 포함되지 않아 401 반환
- **해결**: `SecurityConfig.java`의 `requestMatchers`에 `"/actuator/**"` 추가

## 8. Health Check Grace Period 미설정
- **증상**: 태스크가 시작되자마자 비정상 판정 → 즉시 종료 → 재시작 루프
- **원인**: ECS 서비스의 상태 검사 유예 기간이 0초. Spring Boot 시작에 ~62초 소요
- **해결**: ECS 서비스 업데이트에서 `HealthCheckGracePeriodSeconds: 120`으로 설정

## 9. API baseURL 하드코딩
- **증상**: 프로덕션에서 회원가입/로그인 시 네트워크 에러
- **원인**: `api.ts`에 `baseURL: 'http://localhost:8080/api/v1'` 하드코딩
- **해결**: `VITE_API_BASE_URL` 환경변수 분리. 프로덕션: `/api/v1`, 개발: `http://localhost:8080/api/v1`

## 10. 학생 회원가입 admissionYear NPE
- **증상**: 브라우저에서 학생 회원가입 시 500 에러
- **원인**: 프론트엔드 폼에 입학년도 필드 없음 → 백엔드에서 `(Integer) null` 캐스팅 NPE
- **해결**: 백엔드에 기본값(현재 연도) 추가 + 프론트엔드에 입학년도 필드 추가

## 교훈
- Parameter Store 초기값은 **실제 동작하는 값**으로 검증 후 설정할 것
- ECS 서비스에는 반드시 `HealthCheckGracePeriodSeconds` 설정 (Spring Boot는 최소 120초)
- Spring Security `permitAll`에 헬스체크 경로 포함 필수
- 프론트엔드 API URL은 환경변수로 관리
