/**
 * Kafka Consumer 중단/재개 테스트 — 장애 격리 검증.
 *
 * 시나리오:
 *   0~30s    Phase 1: 정상 운영 (Consumer 동작 중)
 *   30s      Consumer 일시 중단
 *   30~90s   Phase 2: Consumer 중단 상태에서 성적 수정 → API 성공률 100%
 *   90s      Consumer 재개
 *   90~150s  Phase 3: 밀린 이벤트 처리 → Analytics DB 반영
 *
 * 실행: k6 run --env BASE_URL=http://localhost:8080 k6/kafka-isolation-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import exec from 'k6/execution';

var scoreUpdateDuration = new Trend('score_update_duration', true);
var scoreUpdateErrors = new Rate('score_update_errors');
var scoreUpdateCount = new Counter('score_update_count');

var BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export var options = {
  scenarios: {
    score_updates: {
      executor: 'constant-vus',
      vus: 1,
      duration: '2m30s',
      exec: 'scoreUpdateScenario',
    },
    consumer_control: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      exec: 'consumerControlScenario',
    },
  },
  thresholds: {
    score_update_errors: ['rate<0.02'],
  },
};

export function setup() {
  var teacherRes = http.post(BASE_URL + '/api/v1/auth/login', JSON.stringify({
    email: 'teacher@sscm.dev', password: 'teacher1234'
  }), { headers: { 'Content-Type': 'application/json' } });
  var teacherToken = JSON.parse(teacherRes.body).data.accessToken;

  var adminRes = http.post(BASE_URL + '/api/v1/auth/login', JSON.stringify({
    email: 'admin@sscm.dev', password: 'admin1234'
  }), { headers: { 'Content-Type': 'application/json' } });
  var adminToken = JSON.parse(adminRes.body).data.accessToken;

  // 여러 학생에서 scoreId 수집 (VU별 분리를 위해 충분히 확보)
  var scoreIds = [];
  for (var sid = 2; sid <= 51; sid++) {
    var res = http.get(
      BASE_URL + '/api/v1/grades/students/' + sid + '?year=2026&semester=1',
      { headers: { 'Authorization': 'Bearer ' + teacherToken } }
    );
    if (res.status === 200) {
      var body = JSON.parse(res.body);
      var data = body.data;
      var scores = Array.isArray(data) ? data : (data.scores || []);
      for (var j = 0; j < scores.length; j++) {
        scoreIds.push(scores[j].id);
      }
    }
  }
  console.log('[setup] scoreIds count: ' + scoreIds.length);

  // VU별로 분리: 앞쪽 절반 / 뒤쪽 절반
  var half = Math.floor(scoreIds.length / 2);
  var vu0Ids = scoreIds.slice(0, half);
  var vu1Ids = scoreIds.slice(half);

  return {
    teacherToken: teacherToken,
    adminToken: adminToken,
    vuScoreIds: [vu0Ids, vu1Ids]
  };
}

// ── 성적 수정 (VU별 scoreId 분리, 순차 선택) ──
export function scoreUpdateScenario(data) {
  var vuIndex = (exec.vu.idInTest - 1) % 2;
  var myIds = data.vuScoreIds[vuIndex];
  if (!myIds || myIds.length === 0) {
    sleep(1);
    return;
  }

  var headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + data.teacherToken,
  };

  // 순차 선택 (같은 ID 동시 수정 방지)
  var scoreId = myIds[exec.vu.iterationInScenario % myIds.length];
  var newScore = 50 + Math.floor(Math.random() * 50);

  var start = Date.now();
  var res = http.put(
    BASE_URL + '/api/v1/grades/' + scoreId,
    JSON.stringify({ score: newScore }),
    { headers: headers }
  );
  scoreUpdateDuration.add(Date.now() - start);
  scoreUpdateCount.add(1);

  var ok = check(res, {
    'score update 200': function(r) { return r.status === 200; }
  });
  scoreUpdateErrors.add(!ok);

  sleep(0.8 + Math.random() * 0.4);
}

// ── Consumer 제어 시나리오 ──
export function consumerControlScenario(data) {
  var headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + data.adminToken,
  };

  console.log('[Phase 1] 정상 운영 중... (30s)');
  sleep(30);

  console.log('[Phase 2] Consumer 중단 요청...');
  var pauseRes = http.post(BASE_URL + '/api/v1/analytics/admin/consumers/pause', null, { headers: headers });
  check(pauseRes, { 'pause 200': function(r) { return r.status === 200; } });
  console.log('[Phase 2] pause 결과: ' + pauseRes.body);
  console.log('[Phase 2] Consumer 중단 상태에서 성적 수정 계속... (60s)');
  sleep(60);

  var statusRes = http.get(BASE_URL + '/api/v1/analytics/admin/consumers/status', { headers: headers });
  console.log('[Phase 2] Consumer 상태: ' + statusRes.body);

  console.log('[Phase 3] Consumer 재개 요청...');
  var resumeRes = http.post(BASE_URL + '/api/v1/analytics/admin/consumers/resume', null, { headers: headers });
  check(resumeRes, { 'resume 200': function(r) { return r.status === 200; } });
  console.log('[Phase 3] resume 결과: ' + resumeRes.body);
  console.log('[Phase 3] 밀린 이벤트 처리 중... (60s)');
  sleep(60);

  console.log('[완료] 테스트 종료');
}
