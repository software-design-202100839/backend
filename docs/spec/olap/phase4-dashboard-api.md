# Phase 4: Dashboard REST API

> 작성일: 2026-05-24
> 상태: 완료

## 목표

분석 DB에 저장된 집계 데이터를 조회하는 REST API를 만든다.

## 왜 이 작업이 필요한가?

Phase 3까지 하면 분석 DB에 데이터는 쌓이지만, 외부에서 조회할 방법이 없다.
REST API를 만들어야 프론트엔드 대시보드에서 데이터를 볼 수 있다.

핵심: 이 API는 **분석 DB만 조회**한다. 운영 DB에는 접근하지 않는다.
→ 아무리 많은 대시보드 요청이 와도 운영 서비스에 영향 없음 (OLAP 분리의 의미)

---

## 엔드포인트 목록

### 학생별 분석 (모든 역할 접근 가능, 권한 검증 있음)

| Method | Path | 설명 | 파라미터 |
|--------|------|------|----------|
| GET | `/api/v1/analytics/students/{id}/score-summary` | 성적 요약 | year, semester |
| GET | `/api/v1/analytics/students/{id}/score-trend` | 학기별 성적 추이 | 없음 (전체 학기) |
| GET | `/api/v1/analytics/students/{id}/attendance-summary` | 출결/기록 요약 | year, semester |
| GET | `/api/v1/analytics/students/{id}/feedback-summary` | 피드백 요약 | year, semester |
| GET | `/api/v1/analytics/students/{id}/counseling-summary` | 상담 요약 | year, semester |
| GET | `/api/v1/analytics/students/{id}/dashboard` | 종합 대시보드 | year, semester |

### 과목 통계 (교사, 관리자만)

| Method | Path | 설명 | 파라미터 |
|--------|------|------|----------|
| GET | `/api/v1/analytics/subjects/statistics` | 전체 과목 통계 | year, semester |
| GET | `/api/v1/analytics/subjects/{id}/statistics` | 특정 과목 통계 | year, semester |

### 관리자 (ADMIN만)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/analytics/admin/backfill` | 기존 데이터 일괄 적재 |

---

## 접근 권한 설계

```
TEACHER, ADMIN → 모든 학생 데이터 조회 가능
STUDENT        → 본인 데이터만 (studentId가 본인과 일치하는지 검증)
PARENT         → 자녀 데이터만 (parent_student 테이블에 관계가 있는지 검증)
```

AnalyticsAccessChecker가 매 요청마다 이 검증을 수행한다.
기존 ScoreService.checkStudentAccess()와 같은 패턴.

---

## 변경 파일 목록

### 신규 파일

| 파일 | 역할 |
|------|------|
| `analytics/dto/StudentScoreSummaryDto.java` | 성적 요약 응답 |
| `analytics/dto/ScoreTrendDto.java` | 성적 추이 응답 |
| `analytics/dto/StudentAttendanceSummaryDto.java` | 출결/기록 요약 응답 |
| `analytics/dto/StudentFeedbackSummaryDto.java` | 피드백 요약 응답 |
| `analytics/dto/StudentCounselingSummaryDto.java` | 상담 요약 응답 |
| `analytics/dto/SubjectStatisticsDto.java` | 과목 통계 응답 |
| `analytics/dto/StudentDashboardDto.java` | 종합 대시보드 응답 |
| `analytics/service/AnalyticsAccessChecker.java` | 역할별 접근 권한 검증 |
| `analytics/service/AnalyticsDashboardService.java` | 분석 DB 조회 + DTO 변환 |
| `analytics/controller/AnalyticsDashboardController.java` | 학생별 분석 엔드포인트 |
| `analytics/controller/AnalyticsSubjectController.java` | 과목 통계 엔드포인트 |
| `analytics/controller/AnalyticsAdminController.java` | 관리자 backfill 엔드포인트 |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `auth/repository/ParentRepository.java` | `findByUser_Id(Long userId)` 메서드 추가 |

---

## 핵심 개념 정리

### API가 분석 DB만 조회하는 이유

```
❌ 대시보드 API가 운영 DB를 직접 집계:
   → 복잡한 JOIN + GROUP BY가 운영 DB에서 실행
   → 다른 사용자의 성적 등록이 느려짐

✅ 대시보드 API가 분석 DB의 미리 집계된 데이터를 조회:
   → 단순 SELECT (WHERE student_id = ? AND academic_year = ?)
   → 운영 DB에 영향 없음
   → 응답도 빠름
```

이것이 OLAP의 핵심 가치.

### Controller → Service → JdbcTemplate 흐름

```
Controller (권한 검증 + 파라미터 수신)
  ↓
Service (분석 DB 조회 + DTO 변환)
  ↓
analyticsJdbc (분석 DB에 SELECT 실행)
  ↓
분석 DB의 집계 테이블에서 결과 반환
```

운영 DB의 Controller → Service → Repository(JPA) 패턴과 유사하지만,
분석 DB는 JPA 대신 JdbcTemplate을 사용한다 (Phase 1에서 설명한 이유).

### @PreAuthorize vs AccessChecker

- `@PreAuthorize("hasRole('ADMIN')")`: 역할 자체로 접근 차단 (과목 통계, backfill)
- `AccessChecker.checkAccess()`: 역할 + 데이터 소유권 검증 (학생 대시보드)

학생 대시보드는 역할만으로 판단할 수 없다.
"학생이 접근 가능하지만, **본인 데이터만**" 같은 세밀한 제어가 필요해서 AccessChecker를 쓴다.

---

## 응답 예시

### GET /api/v1/analytics/students/5/dashboard?year=2026&semester=1

```json
{
  "status": "success",
  "data": {
    "studentId": 5,
    "studentName": "김철수",
    "academicYear": 2026,
    "semester": 1,
    "avgScore": 85.00,
    "scoreTrend": "UP",
    "attendanceCount": 2,
    "awardCount": 1,
    "totalFeedbackCount": 3,
    "totalCounselCount": 2,
    "lastCounselDate": "2026-04-15",
    "riskLevel": "LOW"
  }
}
```

---

## 검증 방법

Phase 4 완료 후 통합 테스트 진행:

```bash
# 1. 인프라 기동
docker-compose up -d

# 2. Spring Boot 앱 시작
./gradlew bootRun

# 3. Swagger UI에서 테스트
# http://localhost:8080/swagger-ui.html

# 4. 또는 curl로 직접 호출
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/api/v1/analytics/students/1/dashboard?year=2026&semester=1"
```
