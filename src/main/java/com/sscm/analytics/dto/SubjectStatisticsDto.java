package com.sscm.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SubjectStatisticsDto {
    private Long subjectId;
    private String subjectName;
    private Integer academicYear;
    private Integer semester;
    private Integer studentCount;
    private BigDecimal averageScore;
    private BigDecimal maxScore;
    private BigDecimal minScore;
    private BigDecimal stdDeviation;
    private Integer gradeACount;
    private Integer gradeBCount;
    private Integer gradeCCount;
    private Integer gradeDCount;
    private Integer gradeFCount;
}
