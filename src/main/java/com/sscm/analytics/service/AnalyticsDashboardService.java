package com.sscm.analytics.service;

import com.sscm.analytics.dto.*;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sscm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 분석 DB에서 집계 데이터를 조회하는 서비스.
 *
 * analyticsJdbc(분석 DB)만 사용. 운영 DB에는 접근하지 않는다.
 * → 분석 쿼리가 운영 DB에 영향을 주지 않음 (OLAP 분리의 핵심)
 */
/**
 * 분석 DB에서 집계 데이터를 조회하는 서비스.
 *
 * analyticsJdbc(분석 DB)만 사용. 운영 DB에는 접근하지 않는다.
 * → 분석 쿼리가 운영 DB에 영향을 주지 않음 (OLAP 분리의 핵심)
 *
 * 응답 캐시(Redis) 검토 결과:
 * - 0.5 vCPU 환경에서 ObjectMapper 직렬화 오버헤드 > DB 절약분
 * - 부하 테스트에서 p95가 791ms → 1,400ms로 오히려 악화
 * - 결론: 현재 인프라 스펙에서는 응답 캐시 불필요. CPU 스펙 업 시 재검토.
 */
@Slf4j
@Service
public class AnalyticsDashboardService {

    private final JdbcTemplate analyticsJdbc;

    public AnalyticsDashboardService(
            @Qualifier("analyticsJdbc") JdbcTemplate analyticsJdbc) {
        this.analyticsJdbc = analyticsJdbc;
    }

    // ── 성적 ─────────────────────────────────────────────────

    public StudentScoreSummaryDto getScoreSummary(Long studentId, Integer year, Integer semester) {
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM student_score_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        var row = rows.get(0);
        return StudentScoreSummaryDto.builder()
                .studentId(studentId)
                .studentName((String) row.get("student_name"))
                .academicYear(year)
                .semester(semester)
                .subjectCount(toInt(row.get("subject_count")))
                .totalScore(toBigDecimal(row.get("total_score")))
                .averageScore(toBigDecimal(row.get("average_score")))
                .highestScore(toBigDecimal(row.get("highest_score")))
                .lowestScore(toBigDecimal(row.get("lowest_score")))
                .averageGrade((String) row.get("average_grade"))
                .build();
    }

    public ScoreTrendDto getScoreTrend(Long studentId) {
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM student_score_summary WHERE student_id = ? ORDER BY academic_year, semester",
                studentId);

        List<ScoreTrendDto.SemesterScore> trends = rows.stream()
                .map(row -> ScoreTrendDto.SemesterScore.builder()
                        .year(toInt(row.get("academic_year")))
                        .semester(toInt(row.get("semester")))
                        .averageScore(toBigDecimal(row.get("average_score")))
                        .averageGrade((String) row.get("average_grade"))
                        .build())
                .toList();

        return ScoreTrendDto.builder()
                .studentId(studentId)
                .trends(trends)
                .build();
    }

    // ── 기록 ─────────────────────────────────────────────────

    public StudentAttendanceSummaryDto getAttendanceSummary(Long studentId, Integer year, Integer semester) {
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM student_attendance_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        var row = rows.get(0);
        return StudentAttendanceSummaryDto.builder()
                .studentId(studentId)
                .academicYear(year)
                .semester(semester)
                .attendanceCount(toInt(row.get("attendance_count")))
                .awardCount(toInt(row.get("award_count")))
                .volunteerCount(toInt(row.get("volunteer_count")))
                .specialNoteCount(toInt(row.get("special_note_count")))
                .generalOpinionCount(toInt(row.get("general_opinion_count")))
                .build();
    }

    // ── 피드백 ───────────────────────────────────────────────

    public StudentFeedbackSummaryDto getFeedbackSummary(Long studentId, Integer year, Integer semester) {
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM student_feedback_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        var row = rows.get(0);
        return StudentFeedbackSummaryDto.builder()
                .studentId(studentId)
                .academicYear(year)
                .semester(semester)
                .totalFeedbackCount(toInt(row.get("total_feedback_count")))
                .academicCount(toInt(row.get("academic_count")))
                .behaviorCount(toInt(row.get("behavior_count")))
                .attendanceCount(toInt(row.get("attendance_count")))
                .attitudeCount(toInt(row.get("attitude_count")))
                .generalCount(toInt(row.get("general_count")))
                .build();
    }

    // ── 상담 ─────────────────────────────────────────────────

    public StudentCounselingSummaryDto getCounselingSummary(Long studentId, Integer year, Integer semester) {
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM student_counseling_summary WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        var row = rows.get(0);
        return StudentCounselingSummaryDto.builder()
                .studentId(studentId)
                .academicYear(year)
                .semester(semester)
                .totalCounselCount(toInt(row.get("total_counsel_count")))
                .academicCount(toInt(row.get("academic_count")))
                .careerCount(toInt(row.get("career_count")))
                .behaviorCount(toInt(row.get("behavior_count")))
                .personalCount(toInt(row.get("personal_count")))
                .otherCount(toInt(row.get("other_count")))
                .lastCounselDate(toLocalDate(row.get("last_counsel_date")))
                .build();
    }

    // ── 과목 통계 ────────────────────────────────────────────

    public SubjectStatisticsDto getSubjectStatistics(Long subjectId, Integer year, Integer semester) {
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM subject_statistics WHERE subject_id = ? AND academic_year = ? AND semester = ?",
                subjectId, year, semester);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        var row = rows.get(0);
        return mapToSubjectStatistics(row);
    }

    public List<SubjectStatisticsDto> getAllSubjectStatistics(Integer year, Integer semester) {
        Long schoolId = TenantContext.requireSchoolId();
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM subject_statistics WHERE academic_year = ? AND semester = ? AND school_id = ? ORDER BY subject_name",
                year, semester, schoolId);

        return rows.stream().map(this::mapToSubjectStatistics).toList();
    }

    // ── 종합 대시보드 ────────────────────────────────────────

    public StudentDashboardDto getStudentDashboard(Long studentId, Integer year, Integer semester) {
        var rows = analyticsJdbc.queryForList(
                "SELECT * FROM student_learning_dashboard WHERE student_id = ? AND academic_year = ? AND semester = ?",
                studentId, year, semester);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        var row = rows.get(0);
        return StudentDashboardDto.builder()
                .studentId(studentId)
                .studentName((String) row.get("student_name"))
                .academicYear(year)
                .semester(semester)
                .avgScore(toBigDecimal(row.get("avg_score")))
                .scoreTrend((String) row.get("score_trend"))
                .attendanceCount(toInt(row.get("attendance_count")))
                .awardCount(toInt(row.get("award_count")))
                .totalFeedbackCount(toInt(row.get("total_feedback_count")))
                .totalCounselCount(toInt(row.get("total_counsel_count")))
                .lastCounselDate(toLocalDate(row.get("last_counsel_date")))
                .riskLevel((String) row.get("risk_level"))
                .build();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────

    private SubjectStatisticsDto mapToSubjectStatistics(Map<String, Object> row) {
        return SubjectStatisticsDto.builder()
                .subjectId(toLong(row.get("subject_id")))
                .subjectName((String) row.get("subject_name"))
                .academicYear(toInt(row.get("academic_year")))
                .semester(toInt(row.get("semester")))
                .studentCount(toInt(row.get("student_count")))
                .averageScore(toBigDecimal(row.get("average_score")))
                .maxScore(toBigDecimal(row.get("max_score")))
                .minScore(toBigDecimal(row.get("min_score")))
                .stdDeviation(toBigDecimal(row.get("std_deviation")))
                .gradeACount(toInt(row.get("grade_a_count")))
                .gradeBCount(toInt(row.get("grade_b_count")))
                .gradeCCount(toInt(row.get("grade_c_count")))
                .gradeDCount(toInt(row.get("grade_d_count")))
                .gradeFCount(toInt(row.get("grade_f_count")))
                .build();
    }

    private int toInt(Object v) { return v != null ? ((Number) v).intValue() : 0; }
    private Long toLong(Object v) { return v != null ? ((Number) v).longValue() : null; }
    private BigDecimal toBigDecimal(Object v) { return v != null ? new BigDecimal(v.toString()) : null; }
    private LocalDate toLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof Date d) return d.toLocalDate();
        return LocalDate.parse(v.toString());
    }
}
