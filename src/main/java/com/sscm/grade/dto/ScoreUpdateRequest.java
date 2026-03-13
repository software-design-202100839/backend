package com.sscm.grade.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ScoreUpdateRequest {

    @NotNull(message = "점수는 필수입니다")
    @DecimalMin(value = "0.00", message = "점수는 0 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "점수는 100 이하여야 합니다")
    private BigDecimal score;
}
