package com.sscm.analytics.controller;

import com.sscm.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DB 분리 A/B 테스트용 무거운 분석 쿼리 엔드포인트.
 *
 * 목적: 운영 DB에서 직접 무거운 GROUP BY + JOIN 쿼리를 실행하여
 *       동시에 실행되는 OLTP 쿼리(성적 입력)의 p95에 미치는 영향을 측정.
 *
 * Case A (분리 전): 이 엔드포인트 + 성적 입력 API가 같은 DB에서 동시 실행
 * Case B (분리 후): 이 엔드포인트는 사용하지 않고, 기존 analytics API(분석 DB) 사용
 *
 * ADMIN만 접근 가능. 부하 테스트 시 ADMIN 토큰으로 호출.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics/loadtest")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsLoadTestController {

    private final JdbcTemplate jdbcTemplate;          // @Primary = 운영 DB
    private final JdbcTemplate analyticsJdbcTemplate;  // 분석 DB

    public AnalyticsLoadTestController(
            JdbcTemplate jdbcTemplate,
            @Qualifier("analyticsJdbc") JdbcTemplate analyticsJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.analyticsJdbcTemplate = analyticsJdbcTemplate;
    }

    /**
     * 학교별/학년별/과목별 평균 점수 집계.
     * 3-way GROUP BY + scores-subjects-students-users JOIN.
     */
    @GetMapping("/school-subject-avg")
    public ApiResponse<List<Map<String, Object>>> schoolSubjectAvg() {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("""
            SELECT u.school_id, sc.year, sc.semester, sub.name AS subject_name,
                   COUNT(*) AS student_count,
                   ROUND(AVG(sc.score), 2) AS avg_score,
                   ROUND(STDDEV_POP(sc.score), 2) AS stddev_score,
                   MAX(sc.score) AS max_score,
                   MIN(sc.score) AS min_score,
                   COUNT(CASE WHEN sc.score >= 90 THEN 1 END) AS a_count,
                   COUNT(CASE WHEN sc.score >= 80 AND sc.score < 90 THEN 1 END) AS b_count,
                   COUNT(CASE WHEN sc.score >= 70 AND sc.score < 80 THEN 1 END) AS c_count,
                   COUNT(CASE WHEN sc.score < 60 THEN 1 END) AS f_count
            FROM scores sc
            JOIN students st ON st.id = sc.student_id
            JOIN users u ON u.id = st.user_id
            JOIN subjects sub ON sub.id = sc.subject_id
            GROUP BY u.school_id, sc.year, sc.semester, sub.name
            ORDER BY u.school_id, sc.year, sc.semester, sub.name
        """);
        return ApiResponse.success(result);
    }

    /**
     * 학생별 6학기 성적 추이 + 위험도 판정.
     * scores + students + users + feedbacks + counselings 다중 JOIN + 서브쿼리.
     */
    @GetMapping("/student-risk-analysis")
    public ApiResponse<List<Map<String, Object>>> studentRiskAnalysis(
            @RequestParam(defaultValue = "1") Long schoolId) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("""
            SELECT st.id AS student_id, u.name AS student_name,
                   ROUND(AVG(sc.score), 2) AS overall_avg,
                   COUNT(DISTINCT CONCAT(sc.year, '-', sc.semester)) AS semester_count,
                   (SELECT COUNT(*) FROM feedbacks f
                    WHERE f.student_id = st.id
                    AND f.category IN ('BEHAVIOR', 'ATTITUDE')) AS negative_feedback_count,
                   (SELECT COUNT(*) FROM counselings c
                    WHERE c.student_id = st.id) AS counseling_count,
                   CASE
                       WHEN AVG(sc.score) < 60 THEN 'HIGH'
                       WHEN AVG(sc.score) < 70 THEN 'MEDIUM'
                       ELSE 'LOW'
                   END AS risk_level
            FROM students st
            JOIN users u ON u.id = st.user_id
            JOIN scores sc ON sc.student_id = st.id
            WHERE u.school_id = ?
            GROUP BY st.id, u.name
            HAVING COUNT(*) >= 5
            ORDER BY AVG(sc.score) ASC
        """, schoolId);
        return ApiResponse.success(result);
    }

    /**
     * 피드백/상담 통합 분석.
     * feedbacks + counselings + students + users 다중 JOIN + category별 피벗.
     */
    @GetMapping("/feedback-counseling-summary")
    public ApiResponse<List<Map<String, Object>>> feedbackCounselingSummary(
            @RequestParam(defaultValue = "1") Long schoolId) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("""
            SELECT st.id AS student_id, u.name,
                   COUNT(DISTINCT f.id) AS total_feedbacks,
                   COUNT(DISTINCT CASE WHEN f.category = 'ACADEMIC' THEN f.id END) AS academic_fb,
                   COUNT(DISTINCT CASE WHEN f.category = 'BEHAVIOR' THEN f.id END) AS behavior_fb,
                   COUNT(DISTINCT CASE WHEN f.category = 'ATTITUDE' THEN f.id END) AS attitude_fb,
                   COUNT(DISTINCT c.id) AS total_counselings,
                   COUNT(DISTINCT CASE WHEN c.category = 'ACADEMIC' THEN c.id END) AS academic_co,
                   COUNT(DISTINCT CASE WHEN c.category = 'CAREER' THEN c.id END) AS career_co,
                   MAX(c.counsel_date) AS last_counsel_date
            FROM students st
            JOIN users u ON u.id = st.user_id
            LEFT JOIN feedbacks f ON f.student_id = st.id
            LEFT JOIN counselings c ON c.student_id = st.id
            WHERE u.school_id = ?
            GROUP BY st.id, u.name
            ORDER BY COUNT(DISTINCT f.id) DESC
        """, schoolId);
        return ApiResponse.success(result);
    }

    /**
     * 전체 분석 쿼리를 순차 실행하고 각 쿼리 소요시간 반환.
     * k6에서 이 단일 엔드포인트를 반복 호출하면 운영 DB에 분석 부하를 줌.
     */
    @GetMapping("/heavy-all")
    public ApiResponse<Map<String, Object>> heavyAll(
            @RequestParam(defaultValue = "1") Long schoolId) {
        Map<String, Object> result = new LinkedHashMap<>();

        long t1 = System.currentTimeMillis();
        schoolSubjectAvg();
        result.put("schoolSubjectAvgMs", System.currentTimeMillis() - t1);

        long t2 = System.currentTimeMillis();
        studentRiskAnalysis(schoolId);
        result.put("studentRiskAnalysisMs", System.currentTimeMillis() - t2);

        long t3 = System.currentTimeMillis();
        feedbackCounselingSummary(schoolId);
        result.put("feedbackCounselingSummaryMs", System.currentTimeMillis() - t3);

        result.put("totalMs", System.currentTimeMillis() - t1);
        return ApiResponse.success(result);
    }

    // ══════════════════════════════════════════════════════════
    // Analytics DB 전용 쿼리 (Case B-2: DB 분리 상태에서 분석 쿼리)
    // ══════════════════════════════════════════════════════════

    /**
     * Analytics DB에서 학교별 성적 통합 분석.
     * subject_statistics + student_score_summary + student_learning_dashboard JOIN.
     */
    @GetMapping("/heavy-analytics-db")
    public ApiResponse<Map<String, Object>> heavyAnalyticsDb(
            @RequestParam(defaultValue = "1") Long schoolId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 학교별 과목 통계 집계 (subject_statistics)
        long t1 = System.currentTimeMillis();
        analyticsJdbcTemplate.queryForList("""
            SELECT school_id, academic_year, semester, subject_name,
                   SUM(student_count) AS total_students,
                   ROUND(AVG(average_score), 2) AS avg_score,
                   ROUND(AVG(std_deviation), 2) AS avg_stddev,
                   SUM(grade_a_count) AS total_a, SUM(grade_b_count) AS total_b,
                   SUM(grade_c_count) AS total_c, SUM(grade_f_count) AS total_f
            FROM subject_statistics
            WHERE school_id = ?
            GROUP BY school_id, academic_year, semester, subject_name
            ORDER BY academic_year, semester, subject_name
        """, schoolId);
        result.put("subjectStatsMs", System.currentTimeMillis() - t1);

        // 2. 위험 학생 종합 분석 (student_learning_dashboard + score_summary + feedback_summary)
        long t2 = System.currentTimeMillis();
        analyticsJdbcTemplate.queryForList("""
            SELECT d.student_id, d.student_name, d.risk_level,
                   d.avg_score, d.score_trend,
                   d.total_feedback_count, d.total_counsel_count,
                   s.subject_count, s.highest_score, s.lowest_score,
                   f.behavior_count, f.attitude_count,
                   c.last_counsel_date
            FROM student_learning_dashboard d
            LEFT JOIN student_score_summary s
                ON s.student_id = d.student_id AND s.academic_year = d.academic_year AND s.semester = d.semester
            LEFT JOIN student_feedback_summary f
                ON f.student_id = d.student_id AND f.academic_year = d.academic_year AND f.semester = d.semester
            LEFT JOIN student_counseling_summary c
                ON c.student_id = d.student_id AND c.academic_year = d.academic_year AND c.semester = d.semester
            WHERE d.school_id = ?
            ORDER BY d.avg_score ASC NULLS LAST
        """, schoolId);
        result.put("riskAnalysisMs", System.currentTimeMillis() - t2);

        // 3. 학기별 성적 추이 + 피드백/상담 상관관계 (3-way JOIN + GROUP BY)
        long t3 = System.currentTimeMillis();
        analyticsJdbcTemplate.queryForList("""
            SELECT s.student_id, s.student_name, s.academic_year, s.semester,
                   s.average_score,
                   COALESCE(f.total_feedback_count, 0) AS feedback_count,
                   COALESCE(f.behavior_count, 0) + COALESCE(f.attitude_count, 0) AS negative_fb,
                   COALESCE(c.total_counsel_count, 0) AS counsel_count,
                   COALESCE(a.attendance_count, 0) AS attendance_records
            FROM student_score_summary s
            LEFT JOIN student_feedback_summary f
                ON f.student_id = s.student_id AND f.academic_year = s.academic_year AND f.semester = s.semester
            LEFT JOIN student_counseling_summary c
                ON c.student_id = s.student_id AND c.academic_year = s.academic_year AND c.semester = s.semester
            LEFT JOIN student_attendance_summary a
                ON a.student_id = s.student_id AND a.academic_year = s.academic_year AND a.semester = s.semester
            WHERE s.school_id = ?
            ORDER BY s.student_id, s.academic_year, s.semester
        """, schoolId);
        result.put("trendCorrelationMs", System.currentTimeMillis() - t3);

        result.put("totalMs", System.currentTimeMillis() - t1);
        result.put("targetDb", "analytics");
        return ApiResponse.success(result);
    }
}
