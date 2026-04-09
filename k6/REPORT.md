# k6 부하 테스트 결과 보고서

- 실행일: 2026-04-09
- 대상: ECS Fargate (512 CPU, 1024 MB)
- ALB: sscm-alb-1703346258.ap-northeast-2.elb.amazonaws.com

## 테스트 시나리오

| 단계 | 시간 | 가상 사용자(VU) |
|------|------|----------------|
| 워밍업 | 30초 | 0 → 10 |
| 부하 증가 | 1분 | 10 → 30 |
| 피크 | 1분 | 30 → 50 |
| 쿨다운 | 30초 | 50 → 0 |

각 VU는 다음 시나리오를 반복 실행:
1. POST /api/v1/auth/login (로그인)
2. GET /api/v1/auth/me (내 정보)
3. GET /api/v1/students (학생 목록)
4. GET /api/v1/grades/subjects (과목 목록)
5. GET /api/v1/grades/students/1 (학생 성적 조회)
6. GET /api/v1/students/1/records (학생 기록 조회)
7. GET /api/v1/notifications/unread-count (알림 수)
8. GET /api/v1/counselings/my (상담 기록)

## 결과 요약

| 메트릭 | 값 | 목표 | 판정 |
|--------|-----|------|------|
| 총 요청 수 | 1,344건 | - | - |
| p95 응답시간 | 12.21초 | < 2초 | **FAIL** |
| 에러율 | 25% | < 10% | **FAIL** |
| 초당 요청 수 | 7.87 req/s | - | - |
| 로그인 평균 응답시간 | 10.9초 | - | **병목** |

## API별 성공률

| API | 성공률 | 비고 |
|-----|--------|------|
| POST /auth/login | 100% | 매우 느림 (평균 10.9초) |
| GET /auth/me | 100% | 정상 |
| GET /students | 100% | 정상 |
| GET /grades/subjects | 100% | 정상 |
| GET /grades/students/1 | 0% | 404 (테스트 데이터 부재) |
| GET /students/1/records | 0% | 404 (테스트 데이터 부재) |
| GET /notifications/unread-count | 100% | 정상 |
| GET /counselings/my | 100% | 정상 |

## 병목 분석

### 1. 로그인 API 극도로 느림 (핵심 병목)
- **원인**: bcrypt 해싱은 CPU-intensive 작업. Fargate 512 CPU (0.5 vCPU)에서 동시 50명 로그인 시 CPU 포화
- **증거**: 로그인 평균 10.9초, 최대 18.9초. 다른 API는 정상 응답
- **대응 방안**:
  - CPU 스케일업: 512 → 1024 (즉시 효과)
  - bcrypt rounds 조정: 기본 10 → 8로 낮춤 (보안 vs 성능 트레이드오프)
  - Redis 세션 캐싱: 토큰 검증 결과 캐싱 가능

### 2. 조회 API 에러 (테스트 데이터 부재)
- grades/students/1, students/1/records가 404 반환
- 실제 에러가 아닌 테스트 데이터 부재 문제
- 실 운영 시에는 해당 없음

### 3. Redis @Cacheable 도입 판단
- **학생 목록 (GET /students)**: 자주 조회, 변경 빈도 낮음 → **캐싱 적합**
- **과목 목록 (GET /grades/subjects)**: 거의 불변 → **캐싱 적합**
- **성적/기록 조회**: 변경 빈도 중간 → 조건부 캐싱 가능
- **로그인**: 캐싱 대상 아님 (매번 인증 필요)

## 결론 및 권장사항

1. **즉시**: Fargate CPU를 512 → 1024로 스케일업 (로그인 병목 완화)
2. **단기**: Redis @Cacheable 적용 대상 — 학생 목록, 과목 목록 (SSCM-58과 병행)
3. **참고**: 현재 규모(교사 수십 명)에서는 동시 50명이 동시 로그인하는 상황은 극단적이며, 실 운영에서는 문제 없을 수준
