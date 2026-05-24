package com.sscm.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class StudentCounselingSummaryDto {
    private Long studentId;
    private Integer academicYear;
    private Integer semester;
    private Integer totalCounselCount;
    private Integer academicCount;
    private Integer careerCount;
    private Integer behaviorCount;
    private Integer personalCount;
    private Integer otherCount;
    private LocalDate lastCounselDate;
}
