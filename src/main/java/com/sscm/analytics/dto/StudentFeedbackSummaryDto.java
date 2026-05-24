package com.sscm.analytics.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentFeedbackSummaryDto {
    private Long studentId;
    private Integer academicYear;
    private Integer semester;
    private Integer totalFeedbackCount;
    private Integer academicCount;
    private Integer behaviorCount;
    private Integer attendanceCount;
    private Integer attitudeCount;
    private Integer generalCount;
}
