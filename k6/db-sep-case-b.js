/**
 * DB 분리 A/B 테스트 — Case B: OLTP만 (분석 쿼리 없음).
 * 분석 DB가 분리된 상태를 시뮬레이션: OLTP만 운영 DB에서 실행.
 *
 * 실행: k6 run --env BASE_URL=http://localhost:8080 k6/db-sep-case-b.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

var scoreReadDuration = new Trend('score_read_duration', true);
var scoreReadErrors = new Rate('score_read_errors');

var BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

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
    // OLAP 시나리오 없음 — 분석 DB 분리 상태 시뮬레이션
  },
  thresholds: {
    score_read_duration: ['p(95)<2000'],
    score_read_errors: ['rate<0.05'],
  },
};

export function setup() {
  var teacherRes = http.post(BASE_URL + '/api/v1/auth/login', JSON.stringify({
    email: 'teacher@sscm.dev', password: 'teacher1234'
  }), { headers: { 'Content-Type': 'application/json' } });
  var teacherToken = JSON.parse(teacherRes.body).data.accessToken;
  return { teacherToken: teacherToken };
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
