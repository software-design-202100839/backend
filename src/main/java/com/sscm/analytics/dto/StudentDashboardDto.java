package com.sscm.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class StudentDashboardDto {
    private Long studentId;
    private String studentName;
    private Integer academicYear;
    private Integer semester;
    // 성적
    private BigDecimal avgScore;
    private String scoreTrend;          // UP, DOWN, STABLE
    // 학생부 기록
    private Integer attendanceCount;
    private Integer awardCount;
    // 피드백
    private Integer totalFeedbackCount;
    // 상담
    private Integer totalCounselCount;
    private LocalDate lastCounselDate;
    // 위험도
    private String riskLevel;           // LOW, MEDIUM, HIGH
}
