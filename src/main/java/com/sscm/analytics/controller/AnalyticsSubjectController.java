package com.sscm.analytics.controller;

import com.sscm.analytics.dto.SubjectStatisticsDto;
import com.sscm.analytics.service.AnalyticsDashboardService;
import com.sscm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 과목 통계 API.
 * 교사와 관리자만 접근 가능.
 */
@RestController
@RequestMapping("/api/v1/analytics/subjects")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class AnalyticsSubjectController {

    private final AnalyticsDashboardService dashboardService;

    @GetMapping("/statistics")
    public ApiResponse<List<SubjectStatisticsDto>> getAllStatistics(
            @RequestParam Integer year,
            @RequestParam Integer semester) {
        return ApiResponse.success(dashboardService.getAllSubjectStatistics(year, semester));
    }

    @GetMapping("/{subjectId}/statistics")
    public ApiResponse<SubjectStatisticsDto> getStatistics(
            @PathVariable Long subjectId,
            @RequestParam Integer year,
            @RequestParam Integer semester) {
        return ApiResponse.success(dashboardService.getSubjectStatistics(subjectId, year, semester));
    }
}
