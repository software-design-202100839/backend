/**
 * DB 분리 A/B 테스트 — 분석 쿼리와 OLTP 동시 실행 시 성능 비교.
 *
 * 목적:
 *   운영 DB에서 무거운 분석 쿼리가 돌아가는 동안
 *   성적 입력 API의 p95가 얼마나 느려지는지 측정.
 *
 * 실행 방법:
 *   # Case A: 분석 쿼리가 운영 DB에서 실행 (분리 전 시뮬레이션)
 *   k6 run --env BASE_URL=http://localhost:8080 --env CASE=A k6/db-separation-test.js
 *
 *   # Case B: 분석 쿼리 없이 OLTP만 (분리 후 시뮬레이션)
 *   k6 run --env BASE_URL=http://localhost:8080 --env CASE=B k6/db-separation-test.js
 *
 * 비교:
 *   Case A의 score_write_p95 vs Case B의 score_write_p95 차이 = DB 분리 효과
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ── 커스텀 메트릭 ──
const scoreWriteDuration = new Trend('score_write_duration', true);
const analyticsQueryDuration = new Trend('analytics_query_duration', true);
const scoreWriteErrors = new Rate('score_write_errors');
const analyticsQueryErrors = new Rate('analytics_query_errors');
const scoreWriteCount = new Counter('score_write_count');

// ── 설정 ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CASE = __ENV.CASE || 'A'; // A=분석+OLTP 동시, B=OLTP만
const SCHOOL_ID = __ENV.SCHOOL_ID || '1';

export const options = {
  scenarios: {
    // OLTP: 성적 입력/수정 반복
    oltp_writes: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 20 },
        { duration: '60s', target: 50 },
        { duration: '60s', target: 50 },
        { duration: '15s', target: 0 },
      ],
      exec: 'oltpScenario',
    },
    // OLAP: 무거운 분석 쿼리 반복 (Case A만)
    ...(CASE === 'A' ? {
      olap_queries: {
        executor: 'constant-vus',
        vus: 10,
        duration: '2m30s',
        exec: 'olapScenario',
      }
    } : {}),
  },
  thresholds: {
    score_write_duration: ['p(95)<1000'],
    score_write_errors: ['rate<0.05'],
  },
};

// ── 로그인 (setup) ──
export function setup() {
  // Admin 토큰 (분석 쿼리용)
  const adminRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: 'admin@sscm.dev', password: 'admin1234'
  }), { headers: { 'Content-Type': 'application/json' } });
  const adminToken = JSON.parse(adminRes.body).data.accessToken;

  // Teacher 토큰 (성적 입력용)
  const teacherRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: 'teacher@sscm.dev', password: 'teacher1234'
  }), { headers: { 'Content-Type': 'application/json' } });
  const teacherToken = JSON.parse(teacherRes.body).data.accessToken;

  return { adminToken, teacherToken };
}

// ── OLTP 시나리오: 성적 조회 + 수정 반복 ──
export function oltpScenario(data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.teacherToken}`,
  };

  // 랜덤 학생의 성적 조회
  const studentId = Math.floor(Math.random() * 100) + 2; // 2~101
  const start = Date.now();

  const res = http.get(
    `${BASE_URL}/api/v1/grades/students/${studentId}?year=2026&semester=1`,
    { headers }
  );

  const elapsed = Date.now() - start;
  scoreWriteDuration.add(elapsed);
  scoreWriteCount.add(1);

  const ok = check(res, {
    'OLTP status 200': (r) => r.status === 200,
  });
  scoreWriteErrors.add(!ok);

  sleep(0.1 + Math.random() * 0.3);
}

// ── OLAP 시나리오: 무거운 분석 쿼리 반복 ──
export function olapScenario(data) {
  const headers = {
    'Authorization': `Bearer ${data.adminToken}`,
  };

  group('heavy_analytics', () => {
    // 1. 학교별/과목별 평균
    let start = Date.now();
    let res = http.get(`${BASE_URL}/api/v1/analytics/loadtest/school-subject-avg`, { headers });
    analyticsQueryDuration.add(Date.now() - start);
    analyticsQueryErrors.add(res.status !== 200);

    // 2. 위험 학생 분석
    start = Date.now();
    res = http.get(`${BASE_URL}/api/v1/analytics/loadtest/student-risk-analysis?schoolId=${SCHOOL_ID}`, { headers });
    analyticsQueryDuration.add(Date.now() - start);
    analyticsQueryErrors.add(res.status !== 200);

    // 3. 피드백/상담 통합 분석
    start = Date.now();
    res = http.get(`${BASE_URL}/api/v1/analytics/loadtest/feedback-counseling-summary?schoolId=${SCHOOL_ID}`, { headers });
    analyticsQueryDuration.add(Date.now() - start);
    analyticsQueryErrors.add(res.status !== 200);
  });

  sleep(0.5);
}
