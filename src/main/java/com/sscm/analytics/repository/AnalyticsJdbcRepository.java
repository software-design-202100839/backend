package com.sscm.analytics.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

/**
 * 분석 DB (analytics)에 집계 데이터를 저장하는 Repository.
 *
 * JPA가 아닌 JdbcTemplate을 사용하는 이유:
 * - 분석 DB는 집계 SQL을 직접 실행하는 게 명확하고 효율적
 * - JPA 엔티티를 별도로 만들 필요 없음
 *
 * 두 개의 JdbcTemplate을 사용:
 * - primaryJdbc: 운영 DB에서 원본 데이터를 SELECT (집계 쿼리)
 * - analyticsJdbc: 분석 DB에 집계 결과를 UPSERT (저장)
 */
@Slf4j
@Repository
public class AnalyticsJdbcRepository {

    private final JdbcTemplate primaryJdbc;      // 운영 DB (읽기 전용)
    private final JdbcTemplate analyticsJdbc;    // 분석 DB (쓰기)

    public AnalyticsJdbcRepository(
            DataSource dataSource,     // Spring Boot가 @Primary로 자동 생성한 운영 DB DataSource
            @Qualifier("analyticsJdbc") JdbcTemplate analyticsJdbc) {
        this.primaryJdbc = new JdbcTemplate(dataSource);  // 운영 DB JdbcTemplate 직접 생성
        this.analyticsJdbc = analyticsJdbc;
    }

    // ── 성적 관련 ─────────────────────────────────────────────

    /**
     * 학생의 학기별 성적 요약을 재집계.
     *
     * 운영 DB에서: 해당 학생+학기의 전 과목 성적을 집계 (COUNT, SUM, AVG, MAX, MIN)
     * 분석 DB에서: 결과를 student_score_summary 테이블에 upsert
     */
    public void upsertStudentScoreSummary(Long studentId, Integer year, Integer semester, Long schoolId) {
        // 1. 운영 DB에서 집계
        var row = primaryJdbc.queryForMap(
                """
                SELECT s2.user_id,
                       u.name AS student_name,
                       COUNT(*)        AS subject_count,
                       SUM(sc.score)   AS total_score,
                       AVG(sc.score)   AS average_score,
                       MAX(sc.score)   AS highest_score,
                       MIN(sc.score)   AS lowest_score
                FROM scores sc
                JOIN students s2 ON sc.student_id = s2.id
                JOIN users u ON s2.user_id = u.id
                WHERE sc.student_id = ? AND sc.year = ? AND sc.semester = ?
                GROUP BY s2.user_id, u.name
                """,
                studentId, year, semester);

        String gradeLetter = calculateGradeLetter(
                ((Number) row.get("average_score")).doubleValue());

        // 2. 분석 DB에 upsert
        analyticsJdbc.update(
                """
                INSERT INTO student_score_summary
                    (student_id, student_name, academic_year, semester,
                     subject_count, total_score, average_score, highest_score, lowest_score,
                     average_grade, school_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (student_id, academic_year, semester)
                DO UPDATE SET
                    student_name = EXCLUDED.student_name,
                    subject_count = EXCLUDED.subject_count,
                    total_score = EXCLUDED.total_score,
                    average_score = EXCLUDED.average_score,
                    highest_score = EXCLUDED.highest_score,
                    lowest_score = EXCLUDED.lowest_score,
                    average_grade = EXCLUDED.average_grade,
                    school_id = EXCLUDED.school_id,
                    updated_at = NOW()
                """,
                studentId, row.get("student_name"), year, semester,
                row.get("subject_count"), row.get("total_score"),
                row.get("average_score"), row.get("highest_score"),
                row.get("lowest_score"), gradeLetter, schoolId);

        log.debug("성적 요약 upsert: studentId={}, year={}, semester={}", studentId, year, semester);
    }

    /**
     * 과목별 통계를 재집계.
     *
     * 운영 DB에서: 해당 과목+학기의 전체 학생 성적을 집계
     * 분석 DB에서: subject_statistics 테이블에 upsert
     */
    public void upsertSubjectStatistics(Long subjectId, Integer year, Integer semester, Long schoolId) {
        var row = primaryJdbc.queryForMap(
                """
                SELECT sub.name AS subject_name,
                       COUNT(*)             AS student_count,
                       AVG(sc.score)        AS average_score,
                       MAX(sc.score)        AS max_score,
                       MIN(sc.score)        AS min_score,
                       STDDEV_POP(sc.score) AS std_deviation,
                       COUNT(CASE WHEN sc.score >= 90 THEN 1 END) AS grade_a,
                       COUNT(CASE WHEN sc.score >= 80 AND sc.score < 90 THEN 1 END) AS grade_b,
                       COUNT(CASE WHEN sc.score >= 70 AND sc.score < 80 THEN 1 END) AS grade_c,
                       COUNT(CASE WHEN sc.score >= 60 AND sc.score < 70 THEN 1 END) AS grade_d,
                       COUNT(CASE WHEN sc.score < 60 THEN 1 END) AS grade_f
                FROM scores sc
                JOIN subjects sub ON sc.subject_id = sub.id
                WHERE sc.subject_id = ? AND sc.year = ? AND sc.semester = ?
                GROUP BY sub.name
                """,
                subjectId, year, semester);

        analyticsJdbc.update(
                """
                INSERT INTO subject_statistics
                    (subject_id, subject_name, academic_year, semester,
                     student_count, average_score, max_score, min_score, std_deviation,
                     grade_a_count, grade_b_count, grade_c_count, grade_d_count, grade_f_count,
                     school_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (subject_id, academic_year, semester)
                DO UPDATE SET
                    subject_name = EXCLUDED.subject_name,
                    student_count = EXCLUDED.student_count,
                    average_score = EXCLUDED.average_score,
                    max_score = EXCLUDED.max_score,
                    min_score = EXCLUDED.min_score,
                    std_deviation = EXCLUDED.std_deviation,
                    grade_a_count = EXCLUDED.grade_a_count,
                    grade_b_count = EXCLUDED.grade_b_count,
                    grade_c_count = EXCLUDED.grade_c_count,
                    grade_d_count = EXCLUDED.grade_d_count,
                    grade_f_count = EXCLUDED.grade_f_count,
                    school_id = EXCLUDED.school_id,
                    updated_at = NOW()
                """,
                subjectId, row.get("subject_name"), year, semester,
                row.get("student_count"), row.get("average_score"),
                row.get("max_score"), row.get("min_score"), row.get("std_deviation"),
                row.get("grade_a"), row.get("grade_b"), row.get("grade_c"),
                row.get("grade_d"), row.get("grade_f"), schoolId);

        log.debug("과목 통계 upsert: subjectId={}, year={}, semester={}", subjectId, year, semester);
    }

    // ── 학생부 기록 관련 ──────────────────────────────────────

    /**
     * 학생의 학기별 기록(출결/수상/봉사/세특/종합의견) 건수를 재집계.
     */
    public void upsertStudentAttendanceSummary(Long studentId, Integer year, Integer semester, Long schoolId) {
        var row = primaryJdbc.queryForMap(
                """
                SELECT COUNT(CASE WHEN category = 'ATTENDANCE' THEN 1 END)      AS attendance_count,
                       COUNT(CASE WHEN category = 'AWARD' THEN 1 END)           AS award_count,
                       COUNT(CASE WHEN category = 'VOLUNTEER' THEN 1 END)       AS volunteer_count,
                       COUNT(CASE WHEN category = 'SPECIAL_NOTE' THEN 1 END)    AS special_note_count,
                       COUNT(CASE WHEN category = 'GENERAL_OPINION' THEN 1 END) AS general_opinion_count
                FROM student_records
                WHERE student_id = ? AND year = ? AND semester = ?
                """,
                studentId, year, semester);

        analyticsJdbc.update(
                """
                INSERT INTO student_attendance_summary
                    (student_id, academic_year, semester,
                     attendance_count, award_count, volunteer_count,
                     special_note_count, general_opinion_count, school_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (student_id, academic_year, semester)
                DO UPDATE SET
                    attendance_count = EXCLUDED.attendance_count,
                    award_count = EXCLUDED.award_count,
                    volunteer_count = EXCLUDED.volunteer_count,
                    special_note_count = EXCLUDED.special_note_count,
                    general_opinion_count = EXCLUDED.general_opinion_count,
                    school_id = EXCLUDED.school_id,
                    updated_at = NOW()
                """,
                studentId, year, semester,
                row.get("attendance_count"), row.get("award_count"),
                row.get("volunteer_count"), row.get("special_note_count"),
                row.get("general_opinion_count"), schoolId);

        log.debug("기록 요약 upsert: studentId={}, year={}, semester={}", studentId, year, semester);
    }

    // ── 피드백 관련 ───────────────────────────────────────────

    /**
     * 학생의 학기별 피드백 카테고리별 건수를 재집계.
     */
    public void upsertStudentFeedbackSummary(Long studentId, Integer year, Integer semester, Long schoolId) {
        var row = primaryJdbc.queryForMap(
                """
                SELECT COUNT(*)                                                AS total_count,
                       COUNT(CASE WHEN category = 'ACADEMIC' THEN 1 END)       AS academic_count,
                       COUNT(CASE WHEN category = 'BEHAVIOR' THEN 1 END)       AS behavior_count,
                       COUNT(CASE WHEN category = 'ATTENDANCE' THEN 1 END)     AS attendance_count,
                       COUNT(CASE WHEN category = 'ATTITUDE' THEN 1 END)       AS attitude_count,
                       COUNT(CASE WHEN category = 'GENERAL' THEN 1 END)        AS general_count
                FROM feedbacks
                WHERE student_id = ? AND year = ? AND semester = ?
                """,
                studentId, year, semester);

        analyticsJdbc.update(
                """
                INSERT INTO student_feedback_summary
                    (student_id, academic_year, semester,
                     total_feedback_count, academic_count, behavior_count,
                     attendance_count, attitude_count, general_count, school_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (student_id, academic_year, semester)
                DO UPDATE SET
                    total_feedback_count = EXCLUDED.total_feedback_count,
                    academic_count = EXCLUDED.academic_count,
                    behavior_count = EXCLUDED.behavior_count,
                    attendance_count = EXCLUDED.attendance_count,
                    attitude_count = EXCLUDED.attitude_count,
                    general_count = EXCLUDED.general_count,
                    school_id = EXCLUDED.school_id,
                    updated_at = NOW()
                """,
                studentId, year, semester,
                row.get("total_count"), row.get("academic_count"),
                row.get("behavior_count"), row.get("attendance_count"),
                row.get("attitude_count"), row.get("general_count"), schoolId);

        log.debug("피드백 요약 upsert: studentId={}, year={}, semester={}", studentId, year, semester);
    }

    // ── 상담 관련 ─────────────────────────────────────────────

    /**
     * 학생의 학기별 상담 카테고리별 건수와 마지막 상담일을 재집계.
     *
     * 상담은 year/semester 컬럼이 없고 counsel_date만 있으므로,
     * 날짜 기준으로 학기를 판별한다 (1학기: 3~8월, 2학기: 9~2월).
     */
    public void upsertStudentCounselingSummary(Long studentId, Integer year, Integer semester, Long schoolId) {
        String startDate;
        String endDate;
        if (semester == 1) {
            startDate = year + "-03-01";
            endDate = year + "-08-31";
        } else {
            startDate = year + "-09-01";
            endDate = (year + 1) + "-02-28";
        }

        var row = primaryJdbc.queryForMap(
                """
                SELECT COUNT(*)                                              AS total_count,
                       COUNT(CASE WHEN category = 'ACADEMIC' THEN 1 END)     AS academic_count,
                       COUNT(CASE WHEN category = 'CAREER' THEN 1 END)       AS career_count,
                       COUNT(CASE WHEN category = 'BEHAVIOR' THEN 1 END)     AS behavior_count,
                       COUNT(CASE WHEN category = 'PERSONAL' THEN 1 END)     AS personal_count,
                       COUNT(CASE WHEN category = 'OTHER' THEN 1 END)        AS other_count,
                       MAX(counsel_date)                                      AS last_counsel_date
                FROM counselings
                WHERE student_id = ? AND counsel_date BETWEEN ?::date AND ?::date
                """,
                studentId, startDate, endDate);

        analyticsJdbc.update(
                """
                INSERT INTO student_counseling_summary
                    (student_id, academic_year, semester,
                     total_counsel_count, academic_count, career_count,
                     behavior_count, personal_count, other_count,
                     last_counsel_date, school_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (student_id, academic_year, semester)
                DO UPDATE SET
                    total_counsel_count = EXCLUDED.total_counsel_count,
                    academic_count = EXCLUDED.academic_count,
                    career_count = EXCLUDED.career_count,
                    behavior_count = EXCLUDED.behavior_count,
                    personal_count = EXCLUDED.personal_count,
                    other_count = EXCLUDED.other_count,
                    last_counsel_date = EXCLUDED.last_counsel_date,
                    school_id = EXCLUDED.school_id,
                    updated_at = NOW()
                """,
                studentId, year, semester,
                row.get("total_count"), row.get("academic_count"),
                row.get("career_count"), row.get("behavior_count"),
                row.get("personal_count"), row.get("other_count"),
                row.get("last_counsel_date"), schoolId);

        log.debug("상담 요약 upsert: studentId={}, year={}, semester={}", studentId, year, semester);
    }

    // ── 학습 대시보드 (종합) ──────────────────────────────────

    /**
     * 학생의 학기별 종합 대시보드를 갱신.
     *
     * 분석 DB의 다른 요약 테이블에서 데이터를 읽어서
     * student_learning_dashboard 테이블에 통합 저장.
     */
    public void upsertStudentDashboard(Long studentId, Integer year, Integer semester, Long schoolId) {
        // 분석 DB에서 각 요약 테이블 조회
        var scoreRow = queryAnalyticsOrDefault(
                "SELECT average_score, student_name FROM student_score_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);
        var attendRow = queryAnalyticsOrDefault(
                "SELECT attendance_count, award_count FROM student_attendance_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);
        var feedbackRow = queryAnalyticsOrDefault(
                "SELECT total_feedback_count FROM student_feedback_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);
        var counselRow = queryAnalyticsOrDefault(
                "SELECT total_counsel_count, last_counsel_date FROM student_counseling_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);

        // 학생 이름 (분석 DB에 없으면 운영 DB에서 조회)
        String studentName = scoreRow.get("student_name") != null
                ? (String) scoreRow.get("student_name")
                : getStudentName(studentId);

        // 성적 추이 계산 (이전 학기 평균과 비교)
        Double avgScore = scoreRow.get("average_score") != null
                ? ((Number) scoreRow.get("average_score")).doubleValue() : null;
        String scoreTrend = calculateScoreTrend(studentId, year, semester, avgScore);

        // 위험도 판정
        String riskLevel = calculateRiskLevel(avgScore,
                toInt(feedbackRow.get("total_feedback_count")),
                toInt(counselRow.get("total_counsel_count")));

        analyticsJdbc.update(
                """
                INSERT INTO student_learning_dashboard
                    (student_id, student_name, academic_year, semester,
                     avg_score, score_trend, attendance_count, award_count,
                     total_feedback_count, total_counsel_count, last_counsel_date,
                     risk_level, school_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (student_id, academic_year, semester)
                DO UPDATE SET
                    student_name = EXCLUDED.student_name,
                    avg_score = EXCLUDED.avg_score,
                    score_trend = EXCLUDED.score_trend,
                    attendance_count = EXCLUDED.attendance_count,
                    award_count = EXCLUDED.award_count,
                    total_feedback_count = EXCLUDED.total_feedback_count,
                    total_counsel_count = EXCLUDED.total_counsel_count,
                    last_counsel_date = EXCLUDED.last_counsel_date,
                    risk_level = EXCLUDED.risk_level,
                    school_id = EXCLUDED.school_id,
                    updated_at = NOW()
                """,
                studentId, studentName, year, semester,
                avgScore, scoreTrend,
                toInt(attendRow.get("attendance_count")),
                toInt(attendRow.get("award_count")),
                toInt(feedbackRow.get("total_feedback_count")),
                toInt(counselRow.get("total_counsel_count")),
                counselRow.get("last_counsel_date"),
                riskLevel, schoolId);

        log.debug("대시보드 upsert: studentId={}, year={}, semester={}, risk={}",
                studentId, year, semester, riskLevel);
    }

    // ── 헬퍼 메서드 ──────────────────────────────────────────

    /** 분석 DB에서 조회, 결과 없으면 빈 Map 반환 */
    private java.util.Map<String, Object> queryAnalyticsOrDefault(String sql, Object... args) {
        var results = analyticsJdbc.queryForList(sql, args);
        if (results.isEmpty()) {
            return new java.util.HashMap<>();
        }
        return results.get(0);
    }

    /** 운영 DB에서 학생 이름 조회 */
    private String getStudentName(Long studentId) {
        try {
            return primaryJdbc.queryForObject(
                    "SELECT u.name FROM students s JOIN users u ON s.user_id = u.id WHERE s.id = ?",
                    String.class, studentId);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 성적 추이 계산.
     * 이전 학기 평균과 비교하여 UP/DOWN/STABLE 반환.
     */
    private String calculateScoreTrend(Long studentId, Integer year, Integer semester, Double currentAvg) {
        if (currentAvg == null) return null;

        // 이전 학기 계산 (1학기면 → 작년 2학기, 2학기면 → 올해 1학기)
        int prevYear = semester == 1 ? year - 1 : year;
        int prevSemester = semester == 1 ? 2 : 1;

        var prevRows = analyticsJdbc.queryForList(
                "SELECT average_score FROM student_score_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, prevYear, prevSemester);

        if (prevRows.isEmpty() || prevRows.get(0).get("average_score") == null) {
            return null;  // 이전 학기 데이터 없음
        }

        double prevAvg = ((Number) prevRows.get(0).get("average_score")).doubleValue();
        double diff = currentAvg - prevAvg;

        if (diff > 2.0) return "UP";
        if (diff < -2.0) return "DOWN";
        return "STABLE";
    }

    /**
     * 위험도 판정.
     * - HIGH: 평균 60 미만 또는 행동 피드백 5건 이상
     * - MEDIUM: 평균 70 미만
     * - LOW: 그 외
     */
    private String calculateRiskLevel(Double avgScore, int feedbackCount, int counselCount) {
        if (avgScore != null && avgScore < 60) return "HIGH";
        if (feedbackCount >= 5) return "HIGH";
        if (avgScore != null && avgScore < 70) return "MEDIUM";
        return "LOW";
    }

    /** 등급 계산 (Score 엔티티와 동일한 기준) */
    private String calculateGradeLetter(double score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 85) return "B+";
        if (score >= 80) return "B";
        if (score >= 75) return "C+";
        if (score >= 70) return "C";
        if (score >= 65) return "D+";
        if (score >= 60) return "D";
        return "F";
    }

    private int toInt(Object value) {
        return value != null ? ((Number) value).intValue() : 0;
    }
}
