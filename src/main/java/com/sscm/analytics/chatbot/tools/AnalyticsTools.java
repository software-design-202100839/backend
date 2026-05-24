package com.sscm.analytics.chatbot.tools;

import com.sscm.analytics.dto.*;
import com.sscm.analytics.service.AnalyticsDashboardService;
import com.sscm.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;

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

    // ── Tool 입력 DTO (record로 간결하게) ──────────────────────

    public record StudentSemesterRequest(Long studentId, Integer year, Integer semester) {}
    public record StudentRequest(Long studentId) {}
    public record SubjectSemesterRequest(Integer year, Integer semester) {}

    // ── Tool 정의 ─────────────────────────────────────────────

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
}
