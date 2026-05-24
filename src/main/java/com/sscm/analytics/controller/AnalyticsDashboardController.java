package com.sscm.analytics.controller;

import com.sscm.analytics.dto.*;
import com.sscm.analytics.service.AnalyticsAccessChecker;
import com.sscm.analytics.service.AnalyticsDashboardService;
import com.sscm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 학생별 분석 대시보드 API.
 *
 * 모든 엔드포인트에서 접근 권한을 검증한다:
 * - TEACHER, ADMIN: 모든 학생 조회 가능
 * - STUDENT: 본인만
 * - PARENT: 자녀만
 */
@RestController
@RequestMapping("/api/v1/analytics/students/{studentId}")
@RequiredArgsConstructor
public class AnalyticsDashboardController {

    private final AnalyticsDashboardService dashboardService;
    private final AnalyticsAccessChecker accessChecker;

    @GetMapping("/score-summary")
    public ApiResponse<StudentScoreSummaryDto> getScoreSummary(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer semester,
            Authentication authentication) {
        accessChecker.checkAccess(studentId, authentication);
        return ApiResponse.success(dashboardService.getScoreSummary(studentId, year, semester));
    }

    @GetMapping("/score-trend")
    public ApiResponse<ScoreTrendDto> getScoreTrend(
            @PathVariable Long studentId,
            Authentication authentication) {
        accessChecker.checkAccess(studentId, authentication);
        return ApiResponse.success(dashboardService.getScoreTrend(studentId));
    }

    @GetMapping("/attendance-summary")
    public ApiResponse<StudentAttendanceSummaryDto> getAttendanceSummary(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer semester,
            Authentication authentication) {
        accessChecker.checkAccess(studentId, authentication);
        return ApiResponse.success(dashboardService.getAttendanceSummary(studentId, year, semester));
    }

    @GetMapping("/feedback-summary")
    public ApiResponse<StudentFeedbackSummaryDto> getFeedbackSummary(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer semester,
            Authentication authentication) {
        accessChecker.checkAccess(studentId, authentication);
        return ApiResponse.success(dashboardService.getFeedbackSummary(studentId, year, semester));
    }

    @GetMapping("/counseling-summary")
    public ApiResponse<StudentCounselingSummaryDto> getCounselingSummary(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer semester,
            Authentication authentication) {
        accessChecker.checkAccess(studentId, authentication);
        return ApiResponse.success(dashboardService.getCounselingSummary(studentId, year, semester));
    }

    @GetMapping("/dashboard")
    public ApiResponse<StudentDashboardDto> getDashboard(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer semester,
            Authentication authentication) {
        accessChecker.checkAccess(studentId, authentication);
        return ApiResponse.success(dashboardService.getStudentDashboard(studentId, year, semester));
    }
}
