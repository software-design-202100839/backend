package com.sscm.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ScoreTrendDto {
    private Long studentId;
    private List<SemesterScore> trends;

    @Getter
    @Builder
    public static class SemesterScore {
        private Integer year;
        private Integer semester;
        private BigDecimal averageScore;
        private String averageGrade;
    }
}
