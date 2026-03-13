package com.sscm.grade.dto;

import com.sscm.grade.entity.Score;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ScoreResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private Long subjectId;
    private String subjectName;
    private String subjectCode;
    private Integer year;
    private Integer semester;
    private BigDecimal score;
    private String gradeLetter;
    private Integer rank;

    public static ScoreResponse from(Score score) {
        return ScoreResponse.builder()
                .id(score.getId())
                .studentId(score.getStudent().getId())
                .studentName(score.getStudent().getUser().getName())
                .subjectId(score.getSubject().getId())
                .subjectName(score.getSubject().getName())
                .subjectCode(score.getSubject().getCode())
                .year(score.getYear())
                .semester(score.getSemester())
                .score(score.getScore())
                .gradeLetter(score.getGradeLetter())
                .rank(score.getRank())
                .build();
    }
}
