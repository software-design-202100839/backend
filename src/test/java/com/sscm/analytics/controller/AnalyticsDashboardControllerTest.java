package com.sscm.analytics.controller;

import com.sscm.analytics.dto.StudentScoreSummaryDto;
import com.sscm.analytics.service.AnalyticsAccessChecker;
import com.sscm.analytics.service.AnalyticsDashboardService;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.common.config.SecurityConfig;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsDashboardController.class)
@Import(SecurityConfig.class)
@DisplayName("AnalyticsDashboardController 테스트")
class AnalyticsDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsDashboardService dashboardService;

    @MockitoBean
    private AnalyticsAccessChecker accessChecker;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private static RequestPostProcessor teacher() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    private static RequestPostProcessor student(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/students/{id}/score-summary")
    class GetScoreSummary {

        @Test
        @DisplayName("교사가 학생 성적 요약 조회 성공")
        void teacher_success() throws Exception {
            StudentScoreSummaryDto dto = StudentScoreSummaryDto.builder()
                    .studentId(5L).studentName("김철수")
                    .academicYear(2026).semester(1)
                    .subjectCount(5).averageScore(BigDecimal.valueOf(85.5))
                    .averageGrade("B+").build();

            doNothing().when(accessChecker).checkAccess(eq(5L), any());
            given(dashboardService.getScoreSummary(5L, 2026, 1)).willReturn(dto);

            mockMvc.perform(get("/api/v1/analytics/students/5/score-summary")
                            .param("year", "2026")
                            .param("semester", "1")
                            .with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.studentName").value("김철수"))
                    .andExpect(jsonPath("$.data.averageGrade").value("B+"));
        }

        @Test
        @DisplayName("접근 권한 없으면 403")
        void accessDenied_returns403() throws Exception {
            doThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                    .when(accessChecker).checkAccess(eq(99L), any());

            mockMvc.perform(get("/api/v1/analytics/students/99/score-summary")
                            .param("year", "2026")
                            .param("semester", "1")
                            .with(student(10L)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없으면 401")
        void noAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/students/5/score-summary")
                            .param("year", "2026")
                            .param("semester", "1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/analytics/students/{id}/dashboard")
    class GetDashboard {

        @Test
        @DisplayName("교사가 종합 대시보드 조회 성공")
        void teacher_dashboardSuccess() throws Exception {
            doNothing().when(accessChecker).checkAccess(eq(5L), any());
            given(dashboardService.getStudentDashboard(5L, 2026, 1)).willReturn(null);

            mockMvc.perform(get("/api/v1/analytics/students/5/dashboard")
                            .param("year", "2026")
                            .param("semester", "1")
                            .with(teacher()))
                    .andExpect(status().isOk());
        }
    }
}
