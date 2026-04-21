package com.sscm.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class EnrollStudentRequest {
    @NotNull private Long studentId;
    @NotNull @Min(1) private Integer studentNum;
}
