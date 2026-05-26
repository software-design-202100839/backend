# 발표 슬라이드 구성안 (10분)

---

## 슬라이드 1: 서비스 소개 (1분)

### SSCM — Smart School Class Management

**문제**: 학교 성적/상담/학생부 관리가 종이+엑셀 기반 → 비효율
**해결**: 웹 기반 통합 관리 + 실시간 알림 + 데이터 분석

**핵심 기능**:
- 성적 등록/조회 (교사 → 학생/학부모 실시간 알림)
- 상담 관리 (타임라인, 후속 계획)
- 학생부 통합 뷰 (출결, 수상, 봉사, 세특)
- **OLAP 분석 대시보드 + AI 챗봇**

**기술 스택**: Spring Boot 3.5 / React 19 / PostgreSQL 16 / Kafka / AWS ECS

---

## 슬라이드 2: 전체 기능 요약 (1분)

| 모듈 | 기능 | 접근 권한 |
|------|------|-----------|
| 인증 | JWT + RBAC, OTP 활성화 | 전체 |
| 성적 | 등록/수정/삭제, 등급 자동계산 | 교사 |
| 학생부 | 출결/수상/봉사/세특/종합의견 | 교사 |
| 상담 | 등록/수정, 카테고리별 관리 | 교사 |
| 피드백 | 학업/행동/태도 피드백 | 교사 |
| 알림 | SMS(Solapi) + WebSocket 실시간 | 학부모/학생 |
| **분석** | **OLAP 대시보드 + AI 챗봇** | 교사/관리자 |

사용자: 교사, 학생, 학부모, 관리자 (4개 역할)

---

## 슬라이드 3: 아키텍처 다이어그램 (2분)

**(architecture-diagram.md의 1번 다이어그램 삽입)**

핵심 포인트:
- **CloudFront + S3**: 프론트엔드 CDN (정적 파일)
- **ALB + ECS Fargate**: 백엔드 Auto Scaling (CPU 70%, 1→3)
- **RDS x2**: 운영/분석 DB 물리적 분리 (OLAP)
- **MSK (Kafka)**: 이벤트 파이프라인 (결합도 분리)
- **ElastiCache Redis**: JWT 캐시 (커넥션 풀 보호)
- **Prometheus + Grafana**: 메트릭 모니터링

> "모든 기술 선택에 대안 비교와 수치 근거가 있습니다 (Q&A에서 설명)"

---

## 슬라이드 4: OLAP 데이터 파이프라인 (2분)

**(architecture-diagram.md의 2번 다이어그램 삽입)**

```
운영 서비스 → Spring Event → Kafka → Consumer → 집계 → 분석 DB → Dashboard API
```

**왜 분리?**
- 분석 쿼리(GROUP BY + JOIN)가 운영 트랜잭션과 자원 경합
- Kafka 버퍼로 장애 격리: 분석 DB 장애 → 운영 서비스 무관

**AI 챗봇**:
- "김철수 학생 성적 알려줘" → Gemini가 Tool Use로 분석 DB 조회 → 자연어 응답

---

## 슬라이드 5: 부하 테스트 + 모니터링 (2분)

### 부하 테스트 결과 (k6, 200 VU)

| 항목 | Before | After (Redis + 데이터) |
|------|--------|----------------------|
| p95 | 906ms | **791ms (13% 개선)** |
| 처리량 | 133 req/s | **140 req/s** |
| 에러율 | 0% | **0%** |

### 시도 → 측정 → 판단
- Redis JWT 캐시: 13% 개선 ✅
- 응답 캐시: 오히려 악화 → 제거 ✅ (0.5vCPU 직렬화 오버헤드)
- **"캐시가 항상 답은 아닙니다"**

### 모니터링 (Grafana 스크린샷)
- API 응답시간, CPU, JVM Heap, 커넥션 풀
- 알림: 5xx > 0 → critical, Heap > 90% → warning

---

## 슬라이드 6: 라이브 데모 (2분)

### 데모 시나리오

1. **CloudFront URL 접속** → 프론트엔드 로딩 확인
   - https://d2nrbxodaz2vy.cloudfront.net

2. **교사 로그인** → 성적 등록
   - teacher@sscm.dev / teacher1234
   - 성적 등록 → Kafka 이벤트 → 분석 DB 자동 집계

3. **분석 대시보드** → 학생 성적 요약/추이 확인
   - 차트, 등급 분포, 위험도 지표

4. **AI 챗봇** → "학생1의 이번 학기 학습 현황 요약해줘"
   - Gemini Tool Use → 자연어 응답

5. **Grafana 대시보드** → 실시간 메트릭 확인
   - http://sscm-alb-xxx.elb.amazonaws.com/grafana/

---

## 발표 마무리 멘트

> "SSCM은 단순히 기능을 구현한 것이 아니라,
> 대규모 트래픽을 고려한 아키텍처 설계,
> 실측 데이터를 기반한 기술 선택,
> 그리고 운영 가능한 모니터링 체계까지 갖춘 시스템입니다."

---

## 데모 사전 준비 체크리스트

발표 전 확인:
- [ ] AWS 스택 전부 UP
- [ ] `curl /actuator/health` → UP
- [ ] seed/all + seed/bulk + backfill 완료
- [ ] CloudFront 프론트엔드 접속 확인
- [ ] Grafana 접속 확인 (admin/admin)
- [ ] teacher@sscm.dev 로그인 확인
- [ ] AI 챗봇 Gemini API 키 유효한지 확인
