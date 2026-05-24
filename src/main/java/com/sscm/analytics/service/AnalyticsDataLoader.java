package com.sscm.analytics.service;

import com.sscm.analytics.repository.AnalyticsJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 기존 운영 데이터를 분석 DB에 일괄 적재하는 서비스 (Backfill).
 *
 * 언제 사용?
 * - OLAP 시스템을 처음 도입했을 때 (이전 데이터가 분석 DB에 없으니까)
 * - 분석 DB를 초기화한 후 다시 채울 때
 *
 * 동작 방식:
 * 1. 운영 DB에서 모든 학생+학기 조합을 조회
 * 2. 각 조합에 대해 집계 쿼리를 실행하여 분석 DB에 upsert
 */
@Slf4j
@Service
public class AnalyticsDataLoader {

    private final JdbcTemplate primaryJdbc;   // 운영 DB
    private final AnalyticsJdbcRepository analyticsRepo;

    public AnalyticsDataLoader(DataSource dataSource, AnalyticsJdbcRepository analyticsRepo) {
        this.primaryJdbc = new JdbcTemplate(dataSource);
        this.analyticsRepo = analyticsRepo;
    }

    /**
     * 전체 backfill 실행.
     * 모든 학생의 모든 학기에 대해 성적, 기록, 피드백, 상담, 과목통계, 대시보드를 재집계.
     */
    public void backfillAll() {
        log.info("=== Analytics Backfill 시작 ===");

        backfillScores();
        backfillAttendance();
        backfillFeedbacks();
        backfillCounselings();
        backfillDashboards();

        log.info("=== Analytics Backfill 완료 ===");
    }

    private void backfillScores() {
        // 운영 DB에서 성적이 있는 학생+학기+과목 조합 조회
        List<Map<String, Object>> studentSemesters = primaryJdbc.queryForList(
                "SELECT DISTINCT student_id, year, semester FROM scores");

        for (var row : studentSemesters) {
            Long studentId = ((Number) row.get("student_id")).longValue();
            Integer year = (Integer) row.get("year");
            Integer semester = (Integer) row.get("semester");
            analyticsRepo.upsertStudentScoreSummary(studentId, year, semester);
        }

        // 과목별 통계
        List<Map<String, Object>> subjectSemesters = primaryJdbc.queryForList(
                "SELECT DISTINCT subject_id, year, semester FROM scores");

        for (var row : subjectSemesters) {
            Long subjectId = ((Number) row.get("subject_id")).longValue();
            Integer year = (Integer) row.get("year");
            Integer semester = (Integer) row.get("semester");
            analyticsRepo.upsertSubjectStatistics(subjectId, year, semester);
        }

        log.info("성적 backfill 완료: {} 건", studentSemesters.size());
    }

    private void backfillAttendance() {
        List<Map<String, Object>> rows = primaryJdbc.queryForList(
                "SELECT DISTINCT student_id, year, semester FROM student_records");

        for (var row : rows) {
            Long studentId = ((Number) row.get("student_id")).longValue();
            Integer year = (Integer) row.get("year");
            Integer semester = (Integer) row.get("semester");
            analyticsRepo.upsertStudentAttendanceSummary(studentId, year, semester);
        }

        log.info("기록 backfill 완료: {} 건", rows.size());
    }

    private void backfillFeedbacks() {
        List<Map<String, Object>> rows = primaryJdbc.queryForList(
                "SELECT DISTINCT student_id, year, semester FROM feedbacks");

        for (var row : rows) {
            Long studentId = ((Number) row.get("student_id")).longValue();
            Integer year = (Integer) row.get("year");
            Integer semester = (Integer) row.get("semester");
            analyticsRepo.upsertStudentFeedbackSummary(studentId, year, semester);
        }

        log.info("피드백 backfill 완료: {} 건", rows.size());
    }

    private void backfillCounselings() {
        // 상담은 year/semester가 없으므로 counsel_date에서 추출
        List<Map<String, Object>> rows = primaryJdbc.queryForList(
                """
                SELECT DISTINCT student_id,
                       EXTRACT(YEAR FROM counsel_date)::int AS year,
                       CASE WHEN EXTRACT(MONTH FROM counsel_date) <= 8 THEN 1 ELSE 2 END AS semester
                FROM counselings
                """);

        for (var row : rows) {
            Long studentId = ((Number) row.get("student_id")).longValue();
            Integer year = ((Number) row.get("year")).intValue();
            Integer semester = ((Number) row.get("semester")).intValue();
            analyticsRepo.upsertStudentCounselingSummary(studentId, year, semester);
        }

        log.info("상담 backfill 완료: {} 건", rows.size());
    }

    private void backfillDashboards() {
        // 모든 학생+학기 조합 (성적 기준)
        List<Map<String, Object>> rows = primaryJdbc.queryForList(
                """
                SELECT DISTINCT student_id, year, semester FROM scores
                UNION
                SELECT DISTINCT student_id, year, semester FROM student_records
                UNION
                SELECT DISTINCT student_id, year, semester FROM feedbacks
                """);

        for (var row : rows) {
            Long studentId = ((Number) row.get("student_id")).longValue();
            Integer year = (Integer) row.get("year");
            Integer semester = (Integer) row.get("semester");
            analyticsRepo.upsertStudentDashboard(studentId, year, semester);
        }

        log.info("대시보드 backfill 완료: {} 건", rows.size());
    }
}
