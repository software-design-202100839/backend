# 성적/학생부 API 통합 테스트 계획

- **작성자:** 이큐에이 (QA Lee)
- **작성일:** 2026-03-13
- **대상 API:** 성적 관리, 학생부 관리
- **도구:** Postman + Postbot

---

## 1. 성적 API 테스트 시나리오

### 1.1 정상 흐름

| # | 시나리오 | 메서드 | 엔드포인트 | 기대 결과 |
|---|---------|--------|-----------|----------|
| 1 | 과목 목록 조회 | GET | /api/v1/grades/subjects | 12개 과목 반환 |
| 2 | 성적 등록 (교사) | POST | /api/v1/grades | 201, gradeLetter 자동 계산 |
| 3 | 성적 단건 조회 | GET | /api/v1/grades/{id} | 등록된 성적 정보 |
| 4 | 학생별 학기 성적 조회 | GET | /api/v1/grades/students/{id}?year=2026&semester=1 | 총점/평균/등급 포함 |
| 5 | 성적 수정 (교사) | PUT | /api/v1/grades/{id} | gradeLetter 재계산, rank 갱신 |
| 6 | 성적 삭제 (교사) | DELETE | /api/v1/grades/{id} | 200, rank 재계산 |

### 1.2 권한 테스트

| # | 시나리오 | 기대 결과 |
|---|---------|----------|
| 7 | 학생이 성적 등록 시도 | 403 Forbidden |
| 8 | 학부모가 성적 수정 시도 | 403 Forbidden |
| 9 | 미인증 사용자가 성적 조회 | 401 Unauthorized |

### 1.3 유효성 검증

| # | 시나리오 | 기대 결과 |
|---|---------|----------|
| 10 | 점수 -1 입력 | 400, 유효성 실패 |
| 11 | 점수 101 입력 | 400, 유효성 실패 |
| 12 | 학기 3 입력 | 400, 유효성 실패 |
| 13 | 존재하지 않는 학생 ID | 404, GRADE_004 |
| 14 | 존재하지 않는 과목 ID | 404, GRADE_003 |
| 15 | 동일 학생-과목-학기 중복 등록 | 409, GRADE_002 |

### 1.4 자동 계산 검증

| # | 점수 | 기대 등급 |
|---|------|----------|
| 16 | 95 | A+ |
| 17 | 90 | A |
| 18 | 85 | B+ |
| 19 | 70 | C |
| 20 | 59.99 | F |

---

## 2. 학생부 API 테스트 시나리오

### 2.1 정상 흐름

| # | 시나리오 | 메서드 | 엔드포인트 | 기대 결과 |
|---|---------|--------|-----------|----------|
| 21 | 학생 목록 조회 | GET | /api/v1/students | 등록된 학생 전체 |
| 22 | 학생 기본정보 조회 | GET | /api/v1/students/{id} | 이름/학년/반/번호 |
| 23 | 학생부 출결 등록 | POST | /api/v1/students/records | 201, JSONB 저장 |
| 24 | 학생부 특기사항 등록 | POST | /api/v1/students/records | 201 |
| 25 | 학생별 학기 학생부 조회 | GET | /api/v1/students/{id}/records | 해당 학기 전체 항목 |
| 26 | 카테고리 필터 조회 | GET | ...?category=ATTENDANCE | ATTENDANCE만 반환 |
| 27 | 학생부 수정 | PUT | /api/v1/students/records/{id} | content 변경 확인 |
| 28 | 학생부 삭제 | DELETE | /api/v1/students/records/{id} | 200 |

### 2.2 권한 테스트

| # | 시나리오 | 기대 결과 |
|---|---------|----------|
| 29 | 학생이 학생부 등록 시도 | 403 |
| 30 | 학부모가 학생부 삭제 시도 | 403 |

### 2.3 엣지 케이스

| # | 시나리오 | 기대 결과 |
|---|---------|----------|
| 31 | 빈 JSONB content 등록 | 400 |
| 32 | 유효하지 않은 카테고리 | 400 |
| 33 | 존재하지 않는 학생 ID | 404 |
| 34 | 존재하지 않는 레코드 ID 수정 | 404 |

---

## 3. Postbot 엣지 케이스 자동 생성

Postbot AI를 활용하여 위 시나리오 외에 자동 생성된 추가 엣지 케이스:
- 극단적 입력값 (소수점 경계, 최대 문자열 길이)
- SQL Injection 시도 (과목명, 학생부 content)
- XSS 시도 (content 필드에 스크립트 태그)
- 대량 데이터 조회 성능 (100명 학생 x 12과목)
- 동시 요청 충돌 (같은 성적 동시 수정)

---

## 실행 방법

```bash
# Postman CLI로 실행 (Newman)
newman run docs/test/SSCM-Grade-API.postman_collection.json \
  --environment docs/test/SSCM-Local.postman_environment.json
```
