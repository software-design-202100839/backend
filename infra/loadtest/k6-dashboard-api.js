/**
 * k6 부하 테스트 스크립트 — Dashboard API
 *
 * 목적: Redis 도입 전/후 응답시간 비교를 위한 Before 수치 확보
 *
 * 실행 방법:
 *   k6 run --env BASE_URL=http://<서버IP>:8080 k6-dashboard-api.js
 *
 * 결과 해석:
 *   - http_req_duration p(95): 사용자 95%가 경험하는 응답시간
 *   - http_req_failed: 에러율 (5xx 비율)
 *   - iterations: 초당 처리량
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const scoreSummaryDuration = new Trend('score_summary_duration');
const dashboardDuration = new Trend('dashboard_duration');

// 테스트 시나리오: 점진적 부하 증가
export const options = {
    stages: [
        { duration: '30s', target: 50 },   // 30초간 50명까지 증가
        { duration: '1m', target: 100 },   // 1분간 100명 유지
        { duration: '30s', target: 200 },  // 30초간 200명까지 증가
        { duration: '1m', target: 200 },   // 1분간 200명 유지
        { duration: '30s', target: 0 },    // 30초간 0으로 감소
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],  // p95 < 500ms 목표
        errors: ['rate<0.1'],              // 에러율 < 10%
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 시작 전 로그인해서 토큰 받기
export function setup() {
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
        email: 'teacher@sscm.dev',
        password: 'teacher1234'
    }), {
        headers: { 'Content-Type': 'application/json' },
    });

    if (loginRes.status !== 200) {
        console.error(`로그인 실패: ${loginRes.status} ${loginRes.body}`);
        return { token: '' };
    }

    const body = JSON.parse(loginRes.body);
    return { token: body.data.accessToken };
}

export default function (data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.token}`,
    };

    // 1. 학생 성적 요약 조회 (JWT 블랙리스트 체크 포함)
    const scoreSummary = http.get(
        `${BASE_URL}/api/v1/analytics/students/1/score-summary?year=2026&semester=1`,
        { headers }
    );
    scoreSummaryDuration.add(scoreSummary.timings.duration);
    check(scoreSummary, {
        'score-summary status 200 or 404': (r) => r.status === 200 || r.status === 404,
    });
    errorRate.add(scoreSummary.status >= 500);

    sleep(0.5);

    // 2. 종합 대시보드 조회
    const dashboard = http.get(
        `${BASE_URL}/api/v1/analytics/students/1/dashboard?year=2026&semester=1`,
        { headers }
    );
    dashboardDuration.add(dashboard.timings.duration);
    check(dashboard, {
        'dashboard status 200 or 404': (r) => r.status === 200 || r.status === 404,
    });
    errorRate.add(dashboard.status >= 500);

    sleep(0.5);

    // 3. 과목 통계 조회
    const subjectStats = http.get(
        `${BASE_URL}/api/v1/analytics/subjects/statistics?year=2026&semester=1`,
        { headers }
    );
    check(subjectStats, {
        'subject-stats status 200 or 404': (r) => r.status === 200 || r.status === 404,
    });
    errorRate.add(subjectStats.status >= 500);

    sleep(0.5);

    // 4. 성적 추이 조회
    const scoreTrend = http.get(
        `${BASE_URL}/api/v1/analytics/students/1/score-trend`,
        { headers }
    );
    check(scoreTrend, {
        'score-trend status 200': (r) => r.status === 200,
    });
    errorRate.add(scoreTrend.status >= 500);

    sleep(0.5);
}

export function handleSummary(data) {
    const p95 = data.metrics.http_req_duration.values['p(95)'];
    const p50 = data.metrics.http_req_duration.values['p(50)'];
    const errRate = data.metrics.errors ? data.metrics.errors.values.rate : 0;
    const totalReqs = data.metrics.http_reqs.values.count;

    console.log('\n========== 부하 테스트 결과 요약 ==========');
    console.log(`총 요청 수: ${totalReqs}`);
    console.log(`응답시간 p50: ${p50.toFixed(2)}ms`);
    console.log(`응답시간 p95: ${p95.toFixed(2)}ms`);
    console.log(`에러율: ${(errRate * 100).toFixed(2)}%`);
    console.log('==========================================\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    };
}

function textSummary(data, opts) {
    // k6 기본 summary 출력
    return '';
}
