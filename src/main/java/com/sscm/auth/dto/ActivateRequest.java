package com.sscm.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ActivateRequest {

    @NotBlank
    @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다")
    private String phone;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자입니다")
    private String otpCode;

    @NotBlank
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 255)
    private String email;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$",
            message = "비밀번호는 8자 이상, 영문 대소문자 + 숫자 + 특수문자를 포함해야 합니다"
    )
    private String password;
}
