package com.sscm.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CreateClassRequest {
    @NotNull private Integer academicYear;
    @NotNull @Min(1) @Max(3) private Integer grade;
    @NotNull @Min(1) private Integer classNum;
}
