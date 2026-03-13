package com.sscm.grade.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class StudentScoreSummary {

    private Long studentId;
    private String studentName;
    private Integer year;
    private Integer semester;
    private List<ScoreResponse> scores;
    private BigDecimal totalScore;
    private BigDecimal averageScore;
    private String averageGradeLetter;
}
