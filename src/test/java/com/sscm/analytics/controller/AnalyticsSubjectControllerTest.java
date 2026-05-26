package com.sscm.analytics.controller;

import com.sscm.analytics.dto.SubjectStatisticsDto;
import com.sscm.analytics.service.AnalyticsDashboardService;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsSubjectController.class)
@Import(SecurityConfig.class)
@DisplayName("AnalyticsSubjectController 테스트")
class AnalyticsSubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsDashboardService dashboardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private static RequestPostProcessor teacher() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    private static RequestPostProcessor student() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "10", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @Test
    @DisplayName("교사가 전체 과목 통계 조회 성공")
    void teacher_getAllStatistics() throws Exception {
        SubjectStatisticsDto dto = SubjectStatisticsDto.builder()
                .subjectId(1L).subjectName("수학")
                .academicYear(2026).semester(1)
                .studentCount(30).averageScore(BigDecimal.valueOf(78.2))
                .build();

        given(dashboardService.getAllSubjectStatistics(2026, 1)).willReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/analytics/subjects/statistics")
                        .param("year", "2026")
                        .param("semester", "1")
                        .with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subjectName").value("수학"));
    }

    @Test
    @DisplayName("학생이 과목 통계 조회 시 403")
    void student_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/subjects/statistics")
                        .param("year", "2026")
                        .param("semester", "1")
                        .with(student()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("교사가 특정 과목 통계 조회 성공")
    void teacher_getSubjectStatistics() throws Exception {
        SubjectStatisticsDto dto = SubjectStatisticsDto.builder()
                .subjectId(1L).subjectName("수학")
                .academicYear(2026).semester(1)
                .studentCount(30).averageScore(BigDecimal.valueOf(78.2))
                .build();

        given(dashboardService.getSubjectStatistics(1L, 2026, 1)).willReturn(dto);

        mockMvc.perform(get("/api/v1/analytics/subjects/1/statistics")
                        .param("year", "2026")
                        .param("semester", "1")
                        .with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(30));
    }
}
