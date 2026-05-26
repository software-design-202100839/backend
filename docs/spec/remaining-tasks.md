# 남은 작업 계획 (발표 준비 완료까지)

---

## 문제점 분석

### 1. Prometheus 스크래핑 문제
- prod 설정: `targets: ['backend:8080']` — Docker Compose 내부 DNS용
- ECS 환경: Backend Task IP가 동적이라 `backend:8080`으로 접근 불가
- **Prometheus가 Backend 메트릭을 수집 못하고 있을 가능성 높음**
- 해결: ECS Service Discovery 또는 ALB 엔드포인트로 스크래핑

### 2. Grafana 대시보드 내용 부족
- 현재: Uptime, CPU, Threads, 5xx, JVM Heap, HTTP Rate (6개 패널)
- 부족: API 엔드포인트별 응답시간, DB 커넥션풀, Redis 지표, Kafka 지표
- 발표에서 "뭘 볼 거에요?" 질문에 구체적으로 보여줄 수 있어야 함

### 3. 프론트엔드 연동 미확인
- CloudFront → ALB API 호출이 되는지 실제 테스트 안 함
- CORS 설정 확인 필요

### 4. 스크린샷 근거자료 없음
- 발표 PPT에 넣을 실제 캡처가 없음
- AWS 콘솔, Grafana, k6 결과, ECS 서비스 화면 등

---

## 작업 순서

### Phase A: 모니터링 정상화 (Prometheus + Grafana)

**A-1. Prometheus 스크래핑 수정**
- prometheus-prod.yml에서 target을 ALB 엔드포인트로 변경
- Prometheus Docker 이미지 재빌드 + ECR 푸시 + ECS 재배포
- Prometheus targets 페이지에서 UP 확인

**A-2. Grafana 대시보드 보강**
기존 6개 패널 + 추가:
- API 엔드포인트별 p95 응답시간 (histogram_quantile)
- HikariCP 커넥션풀 (active/idle/max)
- Redis 연결 상태
- HTTP 요청 처리량 (req/s by endpoint)
- Kafka consumer lag (가능하면)

**A-3. Grafana 알림 확인**
- 5xx > 0, Heap > 90%, 커넥션풀 경고가 실제로 동작하는지

---

### Phase B: 프론트엔드 연동 확인

**B-1. CloudFront → API 호출 테스트**
- 브라우저에서 https://d2nrbxodaz2vy.cloudfront.net 접속
- 로그인 시도 → API 호출 성공/실패 확인
- 실패 시: CORS 설정 또는 프론트 API URL 수정

**B-2. 프론트 API URL 환경변수**
- 프론트 빌드 시 VITE_API_BASE_URL이 CloudFront 도메인 기준인지 확인
- 필요시 재빌드 + S3 업로드

---

### Phase C: 데모 리허설 + 스크린샷

**C-1. 전체 시나리오 1회 통과**
1. CloudFront → 프론트엔드 로딩
2. 교사 로그인 → 성적 등록
3. 분석 대시보드 확인
4. AI 챗봇 질의
5. Grafana 대시보드 확인

**C-2. 스크린샷 캡처**
- AWS ECS 콘솔 (서비스, Task 목록)
- AWS MSK 콘솔 (클러스터 상태)
- AWS RDS 콘솔 (인스턴스 2개)
- CloudFront 배포 상태
- Grafana 대시보드 (메트릭 그래프)
- k6 부하 테스트 결과 (터미널)
- 프론트엔드 대시보드 화면
- AI 챗봇 대화 화면

**C-3. 문제 발견 시 수정**

---

### Phase D: SonarCloud + 최종 정리

**D-1. SonarCloud 확인**
- GitHub에서 SonarCloud 품질 게이트 통과 여부 확인
- 커버리지, 보안 등급 확인
- 필요시 개선

**D-2. 최종 커밋 + 문서 정리**
- 모든 변경사항 커밋
- 문서 최종 검토

---

## 우선순위

```
🔴 필수 (발표 가능 여부에 직결):
A-1. Prometheus 스크래핑 수정
B-1. CloudFront → API 연동 확인
C-1. 데모 리허설

🟡 중요 (발표 품질):
A-2. Grafana 대시보드 보강
C-2. 스크린샷 캡처
D-1. SonarCloud 확인

🟢 선택 (있으면 좋음):
A-3. 알림 확인
B-2. 프론트 환경변수
D-2. 문서 최종 정리
```
