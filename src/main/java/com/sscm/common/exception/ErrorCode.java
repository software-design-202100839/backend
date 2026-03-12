package com.sscm.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Auth
    AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "인증이 만료되었습니다. 다시 로그인해주세요"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_004", "접근 권한이 없습니다"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "AUTH_005", "비활성화된 계정입니다. 관리자에게 문의하세요"),

    // Common
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON_001", "유효성 검증에 실패했습니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_002", "리소스를 찾을 수 없습니다"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON_003", "이미 등록된 이메일입니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
