package com.sscm.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class RegisterStudentRequest {
    @NotBlank private String name;
    @NotBlank private String phone;
    @NotNull  private Integer admissionYear;
}
