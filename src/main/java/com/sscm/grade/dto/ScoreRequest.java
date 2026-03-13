package com.sscm.grade.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ScoreRequest {

    @NotNull(message = "학생 ID는 필수입니다")
    private Long studentId;

    @NotNull(message = "과목 ID는 필수입니다")
    private Long subjectId;

    @NotNull(message = "학년도는 필수입니다")
    private Integer year;

    @NotNull(message = "학기는 필수입니다")
    @Min(value = 1, message = "학기는 1 또는 2입니다")
    @Max(value = 2, message = "학기는 1 또는 2입니다")
    private Integer semester;

    @NotNull(message = "점수는 필수입니다")
    @DecimalMin(value = "0.00", message = "점수는 0 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "점수는 100 이하여야 합니다")
    private BigDecimal score;
}
