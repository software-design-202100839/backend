# Step 2: 부하 테스트 실행 가이드 (Before 수치 확보)

> 목적: Redis 도입 전 현재 상태의 성능 수치를 기록. 나중에 After와 비교.

---

## 사전 준비

### 1. 로컬 환경 띄우기

```bash
# 프로젝트 루트에서
docker-compose up -d

# 확인: 모든 서비스 running
docker-compose ps
# postgres, postgres-analytics, zookeeper, kafka, prometheus, grafana 모두 Up
```

### 2. 백엔드 실행

```bash
source .env
./gradlew bootRun --no-daemon
```

### 3. 테스트 데이터 확인

백엔드가 뜨면 시드 데이터가 들어가 있는지 확인:
```bash
curl -s http://localhost:8080/actuator/health | jq .
# status: "UP" 이면 OK
```

로그인 가능한지 확인:
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"teacher1@sscm.kr","password":"password123!"}' | jq .
```

> 만약 테스트 계정이 없으면 시드 데이터 생성 후 진행

### 4. k6 설치

```bash
# macOS
brew install k6

# Linux (Ubuntu/Debian)
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Windows (WSL에서)
sudo apt-get install k6
# 또는 직접 바이너리 다운로드
```

### 5. Grafana 접속 확인

- 브라우저: http://localhost:3000
- 로그인: admin / admin
- SSCM Overview 대시보드 확인

---

## 부하 테스트 실행

### 실행 명령어

```bash
cd infra/loadtest

# 로컬 서버 대상
k6 run --env BASE_URL=http://localhost:8080 k6-dashboard-api.js

# EC2 배포 시
k6 run --env BASE_URL=http://<EC2-IP>:8080 k6-dashboard-api.js
```

### 테스트 시나리오

| 단계 | 시간 | 동시 사용자 |
|------|------|------------|
| Ramp-up | 30초 | 0 → 50 |
| Hold | 1분 | 100 |
| Spike | 30초 | 100 → 200 |
| Hold | 1분 | 200 |
| Ramp-down | 30초 | 200 → 0 |

총 약 3분 30초 소요.

---

## 기록할 것 (Before 수치)

### k6 결과에서

| 항목 | 값 | 메모 |
|------|----|----|
| p50 응답시간 | ___ms | |
| p95 응답시간 | ___ms | ← 핵심 지표 |
| p99 응답시간 | ___ms | |
| 에러율 | ___% | |
| 총 요청 수 | ___ | |
| 초당 처리량 (req/s) | ___ | |

### Grafana에서 스크린샷

부하 테스트 실행 중 아래 패널 캡처:

1. **HTTP Request Rate** — 초당 요청 수 변화
2. **CPU Usage** — 부하 시 CPU 사용률
3. **JVM Heap Memory** — 메모리 사용 패턴
4. **Active Threads** — 스레드 수 변화

> 스크린샷 저장 위치: `docs/evidence/before-loadtest/` (디렉토리 생성)

### 추가 관찰 포인트

- API 응답시간이 급격히 튀는 지점 (VU 몇 명일 때?)
- 5xx 에러가 처음 발생하는 지점
- DB 커넥션 풀 관련 경고 로그 여부

---

## 문제가 생기면

| 증상 | 원인 | 해결 |
|------|------|------|
| 로그인 실패 | 시드 데이터 없음 | POST /api/v1/dev/seed 호출 또는 시드 스크립트 실행 |
| Connection refused | 서버 안 떠있음 | docker-compose ps + bootRun 확인 |
| 모든 요청 401 | 토큰 만료 | k6 스크립트의 setup()에서 로그인 로직 확인 |
| Grafana에 데이터 안 보임 | Prometheus 연결 안 됨 | http://localhost:9090/targets 확인 |

---

## 다음 단계

Before 수치를 기록한 후:
1. Redis 도입 (코드 변경)
2. 동일 테스트 재실행 (After 수치)
3. Before/After 비교표 작성
