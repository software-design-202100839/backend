package com.sscm.auth.controller;

import com.sscm.auth.dto.*;
import com.sscm.auth.service.AuthService;
import com.sscm.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "OTP 발송 — 계정 활성화 또는 비밀번호 찾기")
    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        String message = authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @Operation(summary = "계정 활성화 — OTP 검증 후 이메일+비밀번호 설정")
    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<UserResponse>> activate(@Valid @RequestBody ActivateRequest request) {
        UserResponse response = authService.activate(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "비밀번호 찾기 — OTP 발송")
    @PostMapping("/password/reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        String message = authService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @Operation(summary = "비밀번호 찾기 — 새 비밀번호 설정")
    @PostMapping("/password/reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다. 다시 로그인해주세요"));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String refreshToken,
            @AuthenticationPrincipal Long userId) {
        String accessToken = authHeader.substring(7);
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다"));
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Long userId) {
        UserResponse response = authService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
