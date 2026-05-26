package com.sscm.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class StudentScoreSummaryDto {
    private Long studentId;
    private String studentName;
    private Integer academicYear;
    private Integer semester;
    private Integer subjectCount;
    private BigDecimal totalScore;
    private BigDecimal averageScore;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    private String averageGrade;
}
