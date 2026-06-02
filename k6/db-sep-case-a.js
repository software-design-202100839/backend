/**
 * DB 분리 A/B 테스트 — Case A: 분석 쿼리 + OLTP 동시 실행.
 * 운영 DB에서 무거운 분석 쿼리가 돌아가는 동안 성적 조회 API p95 측정.
 *
 * 실행: k6 run --env BASE_URL=http://localhost:8080 k6/db-sep-case-a.js
 */
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

var scoreReadDuration = new Trend('score_read_duration', true);
var analyticsQueryDuration = new Trend('analytics_query_duration', true);
var scoreReadErrors = new Rate('score_read_errors');

var BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
var SCHOOL_ID = __ENV.SCHOOL_ID || '1';

export var options = {
  scenarios: {
    oltp_reads: {
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
    olap_queries: {
      executor: 'constant-vus',
      vus: 10,
      duration: '2m30s',
      exec: 'olapScenario',
    },
  },
  thresholds: {
    score_read_duration: ['p(95)<2000'],
    score_read_errors: ['rate<0.05'],
  },
};

export function setup() {
  var adminRes = http.post(BASE_URL + '/api/v1/auth/login', JSON.stringify({
    email: 'admin@sscm.dev', password: 'admin1234'
  }), { headers: { 'Content-Type': 'application/json' } });
  var adminToken = JSON.parse(adminRes.body).data.accessToken;

  var teacherRes = http.post(BASE_URL + '/api/v1/auth/login', JSON.stringify({
    email: 'teacher@sscm.dev', password: 'teacher1234'
  }), { headers: { 'Content-Type': 'application/json' } });
  var teacherToken = JSON.parse(teacherRes.body).data.accessToken;

  return { adminToken: adminToken, teacherToken: teacherToken };
}

export function oltpScenario(data) {
  var headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + data.teacherToken,
  };

  var studentId = Math.floor(Math.random() * 100) + 2;
  var start = Date.now();
  var res = http.get(BASE_URL + '/api/v1/grades/students/' + studentId + '?year=2026&semester=1', { headers: headers });
  scoreReadDuration.add(Date.now() - start);

  var ok = check(res, { 'OLTP 200': function(r) { return r.status === 200; } });
  scoreReadErrors.add(!ok);

  sleep(0.1 + Math.random() * 0.3);
}

export function olapScenario(data) {
  var headers = { 'Authorization': 'Bearer ' + data.adminToken };

  var start = Date.now();
  http.get(BASE_URL + '/api/v1/analytics/loadtest/school-subject-avg', { headers: headers });
  analyticsQueryDuration.add(Date.now() - start);

  start = Date.now();
  http.get(BASE_URL + '/api/v1/analytics/loadtest/student-risk-analysis?schoolId=' + SCHOOL_ID, { headers: headers });
  analyticsQueryDuration.add(Date.now() - start);

  start = Date.now();
  http.get(BASE_URL + '/api/v1/analytics/loadtest/feedback-counseling-summary?schoolId=' + SCHOOL_ID, { headers: headers });
  analyticsQueryDuration.add(Date.now() - start);

  sleep(0.5);
}
