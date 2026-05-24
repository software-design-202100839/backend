package com.sscm.analytics.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentAttendanceSummaryDto {
    private Long studentId;
    private Integer academicYear;
    private Integer semester;
    private Integer attendanceCount;
    private Integer awardCount;
    private Integer volunteerCount;
    private Integer specialNoteCount;
    private Integer generalOpinionCount;
}
