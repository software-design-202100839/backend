package com.sscm.analytics.service;

import com.sscm.analytics.dto.ScoreTrendDto;
import com.sscm.analytics.dto.StudentScoreSummaryDto;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsDashboardService 단위 테스트")
class AnalyticsDashboardServiceTest {

    private AnalyticsDashboardService service;

    @Mock
    private JdbcTemplate analyticsJdbc;

    @BeforeEach
    void setUp() {
        service = new AnalyticsDashboardService(analyticsJdbc);
    }

    @Nested
    @DisplayName("getScoreSummary")
    class GetScoreSummary {

        @Test
        @DisplayName("데이터 있으면 DTO 반환")
        void returnsDto_whenDataExists() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("student_name", "김철수");
            row.put("subject_count", 5);
            row.put("total_score", new BigDecimal("425.00"));
            row.put("average_score", new BigDecimal("85.00"));
            row.put("highest_score", new BigDecimal("95.00"));
            row.put("lowest_score", new BigDecimal("70.00"));
            row.put("average_grade", "B+");

            given(analyticsJdbc.queryForList(anyString(), any(Object[].class)))
                    .willReturn(List.of(row));

            StudentScoreSummaryDto result = service.getScoreSummary(5L, 2026, 1);

            assertThat(result.getStudentName()).isEqualTo("김철수");
            assertThat(result.getAverageScore()).isEqualByComparingTo("85.00");
            assertThat(result.getAverageGrade()).isEqualTo("B+");
            assertThat(result.getSubjectCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("데이터 없으면 RESOURCE_NOT_FOUND")
        void throwsException_whenNoData() {
            given(analyticsJdbc.queryForList(anyString(), any(Object[].class)))
                    .willReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getScoreSummary(5L, 2026, 1))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getScoreTrend")
    class GetScoreTrend {

        @Test
        @DisplayName("학기별 추이 리스트 반환")
        void returnsTrendList() {
            Map<String, Object> row1 = Map.of(
                    "academic_year", 2025, "semester", 2,
                    "average_score", new BigDecimal("80.00"), "average_grade", "B");
            Map<String, Object> row2 = Map.of(
                    "academic_year", 2026, "semester", 1,
                    "average_score", new BigDecimal("85.00"), "average_grade", "B+");

            given(analyticsJdbc.queryForList(anyString(), any(Object[].class)))
                    .willReturn(List.of(row1, row2));

            ScoreTrendDto result = service.getScoreTrend(5L);

            assertThat(result.getTrends()).hasSize(2);
            assertThat(result.getTrends().get(1).getAverageScore()).isEqualByComparingTo("85.00");
        }

        @Test
        @DisplayName("데이터 없으면 빈 리스트")
        void returnsEmptyList_whenNoData() {
            given(analyticsJdbc.queryForList(anyString(), any(Object[].class)))
                    .willReturn(Collections.emptyList());

            ScoreTrendDto result = service.getScoreTrend(5L);

            assertThat(result.getTrends()).isEmpty();
        }
    }
}
