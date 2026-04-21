package com.sscm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OtpSendRequest {

    @NotBlank
    @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다 (010-XXXX-XXXX)")
    private String phone;

    @NotBlank
    @Pattern(regexp = "ACTIVATE|PW_RESET", message = "purpose는 ACTIVATE 또는 PW_RESET이어야 합니다")
    private String purpose;
}
