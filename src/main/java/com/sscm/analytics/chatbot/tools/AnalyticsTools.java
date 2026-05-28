package com.sscm.analytics.chatbot.tools;

import com.sscm.analytics.chatbot.service.EmbeddingService;
import com.sscm.analytics.chatbot.service.ReportGenerationService;
import com.sscm.analytics.chatbot.service.ReportGenerationService.ReportResult;
import com.sscm.analytics.dto.*;
import com.sscm.analytics.service.AnalyticsDashboardService;
import com.sscm.auth.entity.Student;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.StudentEnrollment;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.repository.ClassRoomRepository;
import com.sscm.common.repository.StudentEnrollmentRepository;
import com.sscm.common.tenant.TenantContext;
import com.sscm.counsel.entity.Counseling;
import com.sscm.counsel.repository.CounselingRepository;
import com.sscm.feedback.entity.Feedback;
import com.sscm.feedback.repository.FeedbackRepository;
import com.sscm.grade.entity.Score;
import com.sscm.grade.repository.ScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * AI 챗봇이 호출할 수 있는 Tool(함수) 정의.
 *
 * Spring AI의 Function Calling 방식:
 * 1. 각 @Bean Function을 Claude에게 "이런 함수가 있어"라고 알려줌
 * 2. 사용자가 질문하면 Claude가 적절한 함수를 선택
 * 3. Spring AI가 함수를 실행하고 결과를 Claude에게 반환
 * 4. Claude가 결과를 자연어로 변환하여 응답
 *
 * @Description 어노테이션이 중요: Claude가 이 설명을 보고 어떤 함수를 호출할지 판단한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AnalyticsTools {

    private final AnalyticsDashboardService dashboardService;
    private final EmbeddingService embeddingService;
    private final ReportGenerationService reportGenerationService;
    private final StudentRepository studentRepository;
    private final FeedbackRepository feedbackRepository;
    private final CounselingRepository counselingRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ScoreRepository scoreRepository;

    // ── Tool 입력 DTO (record로 간결하게) ──────────────────────

    public record StudentSemesterRequest(Long studentId, Integer year, Integer semester) {}
    public record StudentRequest(Long studentId) {}
    public record SubjectSemesterRequest(Integer year, Integer semester) {}
    public record StudentNameRequest(String name) {}
    public record ClassListRequest(Integer year, Integer grade, Integer classNum) {}
    public record AtRiskRequest(Integer year, Integer semester) {}
    public record FeedbackDetailRequest(Long studentId) {}
    public record CounselingDetailRequest(Long studentId) {}
    public record SubjectRankingRequest(Long subjectId, Integer year, Integer semester) {}
    public record CompareClassesRequest(Integer year, Integer grade, Integer classNum1, Integer classNum2) {}
    public record SemanticSearchRequest(String query, Integer year, Integer semester) {}
    public record SemanticSearchResult(Long studentId, String preview, String category, double similarity) {}
    public record ReportRequest(Long studentId, Integer year, Integer semester) {}

    // ── Tool 출력 DTO ─────────────────────────────────────────

    public record StudentSearchResult(Long studentId, String name) {}
    public record FeedbackDetailResult(String date, String category, String content, String teacherName) {}
    public record CounselingDetailResult(String date, String category, String content, String nextPlan, String teacherName) {}
    public record RankingResult(Integer rank, Long studentId, String studentName, Double score, String grade) {}
    public record ClassComparisonResult(ClassStats class1, ClassStats class2) {}
    public record ClassStats(String className, int studentCount, double averageScore) {}

    // ── 기존 Tool 정의 ──────────────────────────────────────────

    @Bean
    @Description("학생의 해당 학기 종합 학습 현황을 조회합니다. 평균 점수, 위험도, 출결, 피드백, 상담 정보를 포함합니다.")
    public Function<StudentSemesterRequest, StudentDashboardDto> getStudentDashboard() {
        return request -> {
            log.info("[AI Tool] getStudentDashboard 호출: studentId={}, year={}, semester={}",
                    request.studentId(), request.year(), request.semester());
            try {
                return dashboardService.getStudentDashboard(
                        request.studentId(), request.year(), request.semester());
            } catch (BusinessException e) {
                log.warn("[AI Tool] 데이터 없음: {}", e.getMessage());
                return null;
            }
        };
    }

    @Bean
    @Description("학생의 해당 학기 성적 요약을 조회합니다. 수강 과목 수, 총점, 평균, 최고점, 최저점, 평균 등급을 포함합니다.")
    public Function<StudentSemesterRequest, StudentScoreSummaryDto> getStudentScoreSummary() {
        return request -> {
            log.info("[AI Tool] getStudentScoreSummary 호출: studentId={}", request.studentId());
            try {
                return dashboardService.getScoreSummary(
                        request.studentId(), request.year(), request.semester());
            } catch (BusinessException e) {
                return null;
            }
        };
    }

    @Bean
    @Description("학생의 전체 학기별 성적 추이를 조회합니다. 각 학기의 평균 점수와 등급 변화를 볼 수 있습니다.")
    public Function<StudentRequest, ScoreTrendDto> getStudentScoreTrend() {
        return request -> {
            log.info("[AI Tool] getStudentScoreTrend 호출: studentId={}", request.studentId());
            return dashboardService.getScoreTrend(request.studentId());
        };
    }

    @Bean
    @Description("학생의 해당 학기 피드백 요약을 조회합니다. 학업, 행동, 출결, 태도, 일반 카테고리별 건수를 포함합니다.")
    public Function<StudentSemesterRequest, StudentFeedbackSummaryDto> getStudentFeedbackSummary() {
        return request -> {
            log.info("[AI Tool] getStudentFeedbackSummary 호출: studentId={}", request.studentId());
            try {
                return dashboardService.getFeedbackSummary(
                        request.studentId(), request.year(), request.semester());
            } catch (BusinessException e) {
                return null;
            }
        };
    }

    @Bean
    @Description("학생의 해당 학기 상담 요약을 조회합니다. 학업, 진로, 행동, 개인, 기타 카테고리별 건수와 마지막 상담일을 포함합니다.")
    public Function<StudentSemesterRequest, StudentCounselingSummaryDto> getStudentCounselingSummary() {
        return request -> {
            log.info("[AI Tool] getStudentCounselingSummary 호출: studentId={}", request.studentId());
            try {
                return dashboardService.getCounselingSummary(
                        request.studentId(), request.year(), request.semester());
            } catch (BusinessException e) {
                return null;
            }
        };
    }

    @Bean
    @Description("해당 학기의 전체 과목별 통계를 조회합니다. 과목별 수강 학생 수, 평균, 최고, 최저, 표준편차, 등급 분포를 포함합니다.")
    public Function<SubjectSemesterRequest, List<SubjectStatisticsDto>> getAllSubjectStatistics() {
        return request -> {
            log.info("[AI Tool] getAllSubjectStatistics 호출: year={}, semester={}", request.year(), request.semester());
            return dashboardService.getAllSubjectStatistics(request.year(), request.semester());
        };
    }

    // ── 신규 Tool 정의 ──────────────────────────────────────────

    @Bean
    @Description("학생 이름으로 검색합니다. 이름의 일부만 입력해도 검색됩니다.")
    public Function<StudentNameRequest, List<StudentSearchResult>> searchStudentByName() {
        return request -> {
            log.info("[AI Tool] searchStudentByName 호출: name={}", request.name());
            Long schoolId = TenantContext.requireSchoolId();
            List<Student> students = studentRepository.findBySchoolIdAndNameContaining(schoolId, request.name());
            return students.stream()
                    .map(s -> new StudentSearchResult(s.getId(), s.getUser().getName()))
                    .toList();
        };
    }

    @Bean
    @Description("반의 학생 목록을 조회합니다. 학년도와 학년, 반 번호로 검색합니다.")
    public Function<ClassListRequest, List<StudentSearchResult>> getClassStudentList() {
        return request -> {
            log.info("[AI Tool] getClassStudentList 호출: year={}, grade={}, classNum={}",
                    request.year(), request.grade(), request.classNum());
            Optional<ClassRoom> classRoom = classRoomRepository.findByAcademicYearAndGradeAndClassNum(
                    request.year(), request.grade(), request.classNum());
            if (classRoom.isEmpty()) {
                return List.of();
            }
            List<StudentEnrollment> enrollments = enrollmentRepository.findByClassRoomWithStudent(classRoom.get());
            return enrollments.stream()
                    .map(e -> new StudentSearchResult(e.getStudent().getId(), e.getStudent().getUser().getName()))
                    .toList();
        };
    }

    @Bean
    @Description("위험도가 높은 학생 목록을 조회합니다. 학업 성취도가 낮거나 관심이 필요한 학생을 찾습니다.")
    public Function<AtRiskRequest, List<Map<String, Object>>> getAtRiskStudents() {
        return request -> {
            log.info("[AI Tool] getAtRiskStudents 호출: year={}, semester={}", request.year(), request.semester());
            Long schoolId = TenantContext.requireSchoolId();
            return dashboardService.getAtRiskStudents(schoolId, request.year(), request.semester());
        };
    }

    @Bean
    @Description("학생이 받은 피드백의 실제 내용을 조회합니다.")
    public Function<FeedbackDetailRequest, List<FeedbackDetailResult>> getStudentFeedbackDetails() {
        return request -> {
            log.info("[AI Tool] getStudentFeedbackDetails 호출: studentId={}", request.studentId());
            List<Feedback> feedbacks = feedbackRepository.findByStudentIdWithDetails(request.studentId());
            return feedbacks.stream()
                    .map(f -> new FeedbackDetailResult(
                            f.getCreatedAt().toLocalDate().toString(),
                            f.getCategory().name(),
                            f.getContent(),
                            f.getTeacher().getUser().getName()))
                    .toList();
        };
    }

    @Bean
    @Description("학생의 상담 내역 실제 내용을 조회합니다.")
    public Function<CounselingDetailRequest, List<CounselingDetailResult>> getStudentCounselingDetails() {
        return request -> {
            log.info("[AI Tool] getStudentCounselingDetails 호출: studentId={}", request.studentId());
            List<Counseling> counselings = counselingRepository.findByStudentIdWithDetails(request.studentId());
            return counselings.stream()
                    .map(c -> new CounselingDetailResult(
                            c.getCounselDate().toString(),
                            c.getCategory().name(),
                            c.getContent(),
                            c.getNextPlan(),
                            c.getTeacher().getUser().getName()))
                    .toList();
        };
    }

    @Bean
    @Description("과목별 학생 순위를 조회합니다.")
    public Function<SubjectRankingRequest, List<RankingResult>> getSubjectRanking() {
        return request -> {
            log.info("[AI Tool] getSubjectRanking 호출: subjectId={}, year={}, semester={}",
                    request.subjectId(), request.year(), request.semester());
            List<Score> scores = scoreRepository.findBySubjectAndSemesterWithStudentOrderByScoreDesc(
                    request.subjectId(), request.year(), request.semester());
            return IntStream.range(0, scores.size())
                    .mapToObj(i -> {
                        Score s = scores.get(i);
                        return new RankingResult(
                                i + 1,
                                s.getStudent().getId(),
                                s.getStudent().getUser().getName(),
                                s.getScore().doubleValue(),
                                s.getGradeLetter());
                    })
                    .toList();
        };
    }

    @Bean
    @Description("두 반의 성적을 비교합니다.")
    public Function<CompareClassesRequest, ClassComparisonResult> compareClasses() {
        return request -> {
            log.info("[AI Tool] compareClasses 호출: year={}, grade={}, class1={}, class2={}",
                    request.year(), request.grade(), request.classNum1(), request.classNum2());

            ClassStats stats1 = getClassStats(request.year(), request.grade(), request.classNum1());
            ClassStats stats2 = getClassStats(request.year(), request.grade(), request.classNum2());
            return new ClassComparisonResult(stats1, stats2);
        };
    }

    // ── RAG 시맨틱 검색 Tool ──────────────────────────────────

    @Bean
    @Description("피드백 텍스트를 의미 기반으로 검색합니다. '수업 태도 문제', '학습 의욕 저하' 같은 자연어 질문으로 관련 피드백을 찾습니다.")
    public Function<SemanticSearchRequest, List<SemanticSearchResult>> semanticSearchFeedback() {
        return request -> {
            log.info("[AI Tool] semanticSearchFeedback 호출: query={}, year={}, semester={}",
                    request.query(), request.year(), request.semester());
            Long schoolId = TenantContext.requireSchoolId();
            return embeddingService.searchFeedback(
                    request.query(), schoolId, null,
                    request.year(), request.semester(), 10
            ).stream().map(row -> new SemanticSearchResult(
                    toLong(row.get("student_id")),
                    (String) row.get("content_preview"),
                    (String) row.get("category"),
                    toDouble(row.get("similarity"))
            )).toList();
        };
    }

    @Bean
    @Description("상담 내역을 의미 기반으로 검색합니다. '진로 고민', '교우 관계 갈등' 같은 자연어 질문으로 관련 상담을 찾습니다.")
    public Function<SemanticSearchRequest, List<SemanticSearchResult>> semanticSearchCounseling() {
        return request -> {
            log.info("[AI Tool] semanticSearchCounseling 호출: query={}, year={}, semester={}",
                    request.query(), request.year(), request.semester());
            Long schoolId = TenantContext.requireSchoolId();
            return embeddingService.searchCounseling(
                    request.query(), schoolId, null,
                    request.year(), request.semester(), 10
            ).stream().map(row -> new SemanticSearchResult(
                    toLong(row.get("student_id")),
                    (String) row.get("content_preview"),
                    (String) row.get("category"),
                    toDouble(row.get("similarity"))
            )).toList();
        };
    }

    // ── 보고서 생성 Tool ──────────────────────────────────────

    @Bean
    @Description("학생의 학기말 종합 의견 초안을 생성합니다. 성적, 피드백, 상담 기록을 종합하여 근거 기반 보고서를 작성합니다.")
    public Function<ReportRequest, ReportResult> generateStudentReport() {
        return request -> {
            // AI가 학년도/학기를 생략할 수 있으므로 기본값 적용 (최신 학기: 2026년 1학기)
            int year = request.year() != null ? request.year() : 2026;
            int semester = request.semester() != null ? request.semester() : 1;
            log.info("[AI Tool] generateStudentReport 호출: studentId={}, year={}, semester={}",
                    request.studentId(), year, semester);
            Long schoolId = TenantContext.requireSchoolId();
            return reportGenerationService.generateReport(
                    request.studentId(), year, semester, schoolId, 0L);
        };
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return null;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private ClassStats getClassStats(int year, int grade, int classNum) {
        String className = grade + "학년 " + classNum + "반";
        Optional<ClassRoom> classRoom = classRoomRepository.findByAcademicYearAndGradeAndClassNum(year, grade, classNum);
        if (classRoom.isEmpty()) {
            return new ClassStats(className, 0, 0.0);
        }

        List<StudentEnrollment> enrollments = enrollmentRepository.findByClassRoomWithStudent(classRoom.get());
        if (enrollments.isEmpty()) {
            return new ClassStats(className, 0, 0.0);
        }

        double totalScore = 0;
        int count = 0;
        for (StudentEnrollment enrollment : enrollments) {
            List<Score> scores = scoreRepository.findByStudentIdAndYearAndSemester(
                    enrollment.getStudent().getId(), year, 1);
            if (!scores.isEmpty()) {
                double avg = scores.stream()
                        .mapToDouble(s -> s.getScore().doubleValue())
                        .average().orElse(0);
                totalScore += avg;
                count++;
            }
        }

        double avgScore = count > 0 ? totalScore / count : 0.0;
        return new ClassStats(className, enrollments.size(), Math.round(avgScore * 100.0) / 100.0);
    }
}
