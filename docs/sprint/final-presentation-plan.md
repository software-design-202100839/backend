# 최종 발표 준비 계획

> 작성일: 2026-05-26
> 목표: 소프트웨어 설계 수업 최종 발표 (10분 발표 + 30분 질의)

---

## 개요

- 서비스 전체 발표 (OLAP에 집중하되, 인증/성적/상담/알림 전체 커버)
- 대규모 설계를 가정한 아키텍처. 실제로 AWS에 올려서 라이브 시연
- 모든 기술 선택에 수치 근거 (Before/After 부하 테스트 또는 공식 벤치마크)
- 매 단계마다 근거 자료 기록 (스크린샷, 수치, 문서)

---

## 실행 순서

| # | 작업 | 목적 | 산출물 |
|---|------|------|--------|
| 1 | 테스트 코드 작성 (Phase 6) | CI 통과 + 동작 증명 | 테스트 통과 스크린샷, 커버리지 리포트 |
| 2 | 배포 (Redis 없이) + 부하 테스트 | Before 수치 확보 | k6 결과, Grafana 스크린샷 |
| 3 | Redis 도입 + WebSocket 수정 | 캐시 + 다중 인스턴스 대응 | 코드 커밋, 설계 문서 |
| 4 | 배포 (Redis 포함) + 부하 테스트 | After 수치 확보 | Before/After 비교표 |
| 5 | 아키텍처 다이어그램 | PPT용 | draw.io PNG 3장 |
| 6 | AWS 풀 배포 (MSK, CloudFront 등) | 발표용 환경 | AWS 콘솔 스크린샷 |
| 7 | 최종 부하 테스트 + 모니터링 | 풀 환경 검증 | 최종 수치 리포트 |
| 8 | 트레이드오프 수치 정리 | 질의 대비 | 기술 비교 문서 |
| 9 | 발표 자료 완성 | PPT + 데모 | 최종 PPT, 데모 시나리오 |

---

## 대규모 아키텍처 구성

| 서비스 | 용도 | 왜 필요한가 |
|--------|------|-------------|
| ECS Fargate | Backend + Consumer | Stateless. Auto Scaling |
| ALB | 경로 기반 라우팅 | /api/*, /ws/* 분배 |
| S3 + CloudFront | Frontend | CDN. 컨테이너 서빙은 낭비 |
| RDS Multi-AZ | 운영 DB | failover. 단일장애점 방지 |
| RDS | 분석 DB | OLAP 전용 (자원 경합 방지) |
| Amazon MSK | Kafka | Stateful. 관리형 사용 |
| ElastiCache Redis | JWT 캐시 + WS Pub/Sub | DB hit 방지 + 다중 인스턴스 |
| CloudWatch Logs | 중앙 로깅 | 다중 인스턴스 로그 수집 |
| Prometheus + Grafana | 메트릭/대시보드 | 커스텀 메트릭, 알림 |

---

## 기술 선택 근거 (수치 기반)

### 직접 측정 (Before/After)

| 기술 | Before | After | 측정 방법 |
|------|--------|-------|-----------|
| Redis (JWT 캐시) | API p95 Xms | Yms | k6 200 concurrent |
| DB 분리 (OLAP) | 분석 중 운영 응답 증가 | 운영 무영향 | 동시 부하 |
| ECS Auto Scaling | 에러율 X% | 에러율 0% | ramp-up 테스트 |

### 공식 수치 인용

| 기술 | 대안 | 선택 이유 |
|------|------|-----------|
| S3+CloudFront | ECS 서빙 | CDN 엣지 400+, 레이턴시 50-70% 감소 |
| RDS Multi-AZ | 단일 AZ | failover 60~120초, RPO=0 |
| MSK | 자체 Kafka | 가용성 99.9% SLA |
| Redis | DB 조회 | GET <1ms, 100K+ ops/sec |
| ECS Fargate | EKS | EKS control plane $0.10/hr 추가 오버헤드 |

---

## 발표 구성 (10분)

| # | 내용 | 시간 |
|---|------|------|
| 1 | 서비스 소개 | 1분 |
| 2 | 전체 기능 요약 | 1분 |
| 3 | 아키텍처 다이어그램 | 2분 |
| 4 | OLAP 파이프라인 | 2분 |
| 5 | 부하 테스트 + 모니터링 결과 | 2분 |
| 6 | 라이브 데모 | 2분 |

---

## 질의 대비 자료 체크리스트

- [ ] Before/After 수치 (Redis, DB 분리, Auto Scaling)
- [ ] Grafana 스크린샷 (부하 중 지표)
- [ ] k6 결과 리포트
- [ ] 서비스별 비용 계산표
- [ ] 장애 시나리오 대응 문서
- [ ] CI/CD 실행 스크린샷
- [ ] 테스트 커버리지 리포트
- [ ] AWS 콘솔 스크린샷
