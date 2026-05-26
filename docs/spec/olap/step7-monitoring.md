# Step 7: Monitoring 배포 — Prometheus + Grafana on ECS

> 작성일: 2026-05-26
> 목적: ECS 환경에서 메트릭 수집 + 대시보드 시각화

---

## 구성

| 서비스 | 역할 | 포트 |
|--------|------|------|
| Prometheus | Backend의 /actuator/prometheus 메트릭 스크래핑 | 9090 |
| Grafana | 대시보드 시각화 + 알림 | 3000 |

둘 다 ECS Fargate의 단일 Task에서 사이드카로 실행.

---

## 접근 경로

- Grafana: `http://sscm-alb-942993728.ap-northeast-2.elb.amazonaws.com/grafana/`
- 로그인: admin / admin
- ALB 리스너 규칙 priority 5로 `/grafana/*` → Grafana Target Group

---

## CloudFormation

- 스택: `sscm-monitoring`
- 템플릿: `infra/cfn-monitoring.yml`
- ECR 이미지: `sscm-prometheus:latest`, `sscm-grafana:latest`
- 사전 조건: `sscm-alb`, `sscm-ecs` 스택 존재 (Export 값 참조)

---

## 모니터링 지표

### Grafana 대시보드에서 볼 수 있는 것

| 지표 | 의미 | 이상 시 |
|------|------|---------|
| HTTP Request Rate | 초당 요청 수 | 부하 확인 |
| HTTP 5xx Error Rate | 서버 에러 비율 | 0 초과 시 critical |
| JVM Heap Memory | 메모리 사용 | 90% 이상 시 warning |
| CPU Usage | CPU 사용률 | 70% 이상 시 주의 |
| HikariCP Active Connections | DB 커넥션 풀 | 포화 시 응답 지연 |

### 알림 규칙 (사전 구성됨)
- HTTP 5xx > 0 → critical
- JVM Heap > 90% 2분 지속 → warning
- HikariCP pending connections → warning

---

## 비용

- ECS Fargate (0.25 vCPU, 0.5GB): ~$0.015/hr
- 전체 인프라 비용의 ~5%
