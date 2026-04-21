package com.sscm.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RegisterTeacherRequest {
    @NotBlank private String name;
    @NotBlank private String phone;
    private String department;
}
