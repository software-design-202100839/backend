import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration');
const apiDuration = new Trend('api_duration');

// 테스트 설정
const BASE_URL = __ENV.BASE_URL || 'http://sscm-alb-1703346258.ap-northeast-2.elb.amazonaws.com';

// 단계별 부하 증가
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // 워밍업: 10 VU까지 증가
    { duration: '1m',  target: 30 },   // 부하 증가: 30 VU
    { duration: '1m',  target: 50 },   // 피크: 50 VU
    { duration: '30s', target: 0 },    // 쿨다운
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // p95 응답시간 2초 이내
    errors: ['rate<0.1'],               // 에러율 10% 미만
  },
};

// 테스트용 계정 (교사)
const TEACHER_CREDENTIALS = {
  email: 'teacher@sscm.dev',
  password: 'Teacher1234!',
};

// 로그인 후 토큰 획득
function login() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify(TEACHER_CREDENTIALS),
    { headers: { 'Content-Type': 'application/json' } }
  );

  loginDuration.add(res.timings.duration);

  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      return (body.data && body.data.accessToken) || body.accessToken || null;
    } catch (e) {
      return null;
    }
  }
  return null;
}

// 인증 헤더
function authHeaders(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };
}

export default function () {
  // 1. 로그인
  const token = login();
  const loggedIn = check(token, { 'login succeeded': (t) => t !== null });
  errorRate.add(!loggedIn);

  if (!token) {
    sleep(1);
    return;
  }

  const headers = authHeaders(token);

  // 2. 내 정보 조회
  {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, headers);
    check(res, { 'GET /auth/me: 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
    apiDuration.add(res.timings.duration);
  }

  sleep(0.5);

  // 3. 학생 목록 조회
  {
    const res = http.get(`${BASE_URL}/api/v1/students`, headers);
    check(res, { 'GET /students: 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
    apiDuration.add(res.timings.duration);
  }

  sleep(0.5);

  // 4. 과목 목록 조회
  {
    const res = http.get(`${BASE_URL}/api/v1/grades/subjects`, headers);
    check(res, { 'GET /grades/subjects: 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
    apiDuration.add(res.timings.duration);
  }

  sleep(0.5);

  // 5. 특정 학생 성적 조회
  {
    const res = http.get(`${BASE_URL}/api/v1/grades/students/1?year=2026&semester=1`, headers);
    check(res, { 'GET /grades/students/1: 2xx': (r) => r.status >= 200 && r.status < 300 });
    errorRate.add(res.status >= 400);
    apiDuration.add(res.timings.duration);
  }

  sleep(0.5);

  // 6. 특정 학생 기록 조회
  {
    const res = http.get(`${BASE_URL}/api/v1/students/1/records?year=2026&semester=1`, headers);
    check(res, { 'GET /students/1/records: 2xx': (r) => r.status >= 200 && r.status < 300 });
    errorRate.add(res.status >= 400);
    apiDuration.add(res.timings.duration);
  }

  sleep(0.5);

  // 7. 알림 조회
  {
    const res = http.get(`${BASE_URL}/api/v1/notifications/unread-count`, headers);
    check(res, { 'GET /notifications/unread-count: 2xx': (r) => r.status >= 200 && r.status < 300 });
    errorRate.add(res.status >= 400);
    apiDuration.add(res.timings.duration);
  }

  sleep(0.5);

  // 8. 상담 기록 조회
  {
    const res = http.get(`${BASE_URL}/api/v1/counselings/my`, headers);
    check(res, { 'GET /counselings/my: 2xx': (r) => r.status >= 200 && r.status < 300 });
    errorRate.add(res.status >= 400);
    apiDuration.add(res.timings.duration);
  }

  sleep(1);
}
