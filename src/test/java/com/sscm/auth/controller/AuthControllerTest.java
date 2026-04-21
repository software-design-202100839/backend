package com.sscm.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.auth.dto.*;
import com.sscm.auth.service.AuthService;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.common.config.SecurityConfig;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController 통합 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private static RequestPostProcessor teacher() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    private static RequestPostProcessor student() {
        return authentication(new UsernamePasswordAuthenticationToken(
                2L, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    private UserResponse sampleUserResponse() {
        return UserResponse.builder()
                .id(1L)
                .email("teacher@school.com")
                .name("이교사")
                .role("TEACHER")
                .roleEntityId(10L)
                .build();
    }

    private TokenResponse sampleTokenResponse() {
        return TokenResponse.builder()
                .accessToken("access-token-value")
                .refreshToken("refresh-token-value")
                .expiresIn(1800L)
                .user(sampleUserResponse())
                .build();
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/otp/send
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/otp/send — OTP 발송")
    class SendOtp {

        @Test
        @DisplayName("유효한 전화번호 + ACTIVATE → 200 성공")
        void activateOtpSuccess() throws Exception {
            given(authService.sendOtp(any())).willReturn("인증번호가 발송되었습니다");

            mockMvc.perform(post("/api/v1/auth/otp/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678",
                                    "purpose", "ACTIVATE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("인증번호가 발송되었습니다"));
        }

        @Test
        @DisplayName("유효한 전화번호 + PW_RESET → 200 성공")
        void pwResetOtpSuccess() throws Exception {
            given(authService.sendOtp(any())).willReturn("인증번호가 발송되었습니다");

            mockMvc.perform(post("/api/v1/auth/otp/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678",
                                    "purpose", "PW_RESET"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("이미 활성화된 계정에 ACTIVATE OTP 요청 → 409")
        void alreadyActivated() throws Exception {
            given(authService.sendOtp(any()))
                    .willThrow(new BusinessException(ErrorCode.ACCOUNT_ALREADY_ACTIVATED));

            mockMvc.perform(post("/api/v1/auth/otp/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678",
                                    "purpose", "ACTIVATE"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("미활성화 계정에 PW_RESET OTP 요청 → 409")
        void notActivatedPwReset() throws Exception {
            given(authService.sendOtp(any()))
                    .willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED));

            mockMvc.perform(post("/api/v1/auth/otp/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678",
                                    "purpose", "PW_RESET"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("전화번호 형식 오류 → 400")
        void invalidPhoneFormat() throws Exception {
            mockMvc.perform(post("/api/v1/auth/otp/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "01012345678",
                                    "purpose", "ACTIVATE"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("purpose 값 오류 → 400")
        void invalidPurpose() throws Exception {
            mockMvc.perform(post("/api/v1/auth/otp/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678",
                                    "purpose", "UNKNOWN"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("필수 필드 누락 → 400")
        void missingField() throws Exception {
            mockMvc.perform(post("/api/v1/auth/otp/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("phone", "010-1234-5678"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/activate
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/activate — 계정 활성화")
    class Activate {

        private String activateRequestJson() throws Exception {
            return objectMapper.writeValueAsString(Map.of(
                    "phone", "010-1234-5678",
                    "otpCode", "123456",
                    "email", "teacher@school.com",
                    "password", "Password1!"));
        }

        @Test
        @DisplayName("정상 활성화 → 200 + UserResponse 반환")
        void activateSuccess() throws Exception {
            given(authService.activate(any())).willReturn(sampleUserResponse());

            mockMvc.perform(post("/api/v1/auth/activate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activateRequestJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.email").value("teacher@school.com"))
                    .andExpect(jsonPath("$.data.role").value("TEACHER"));
        }

        @Test
        @DisplayName("미등록 전화번호 → 404")
        void phoneNotFound() throws Exception {
            given(authService.activate(any()))
                    .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(post("/api/v1/auth/activate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activateRequestJson()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("이미 활성화된 계정 → 409")
        void alreadyActivated() throws Exception {
            given(authService.activate(any()))
                    .willThrow(new BusinessException(ErrorCode.ACCOUNT_ALREADY_ACTIVATED));

            mockMvc.perform(post("/api/v1/auth/activate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activateRequestJson()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("이메일 중복 → 409")
        void duplicateEmail() throws Exception {
            given(authService.activate(any()))
                    .willThrow(new BusinessException(ErrorCode.DUPLICATE_RESOURCE));

            mockMvc.perform(post("/api/v1/auth/activate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activateRequestJson()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("OTP 불일치 → 400")
        void otpInvalid() throws Exception {
            given(authService.activate(any()))
                    .willThrow(new BusinessException(ErrorCode.OTP_INVALID));

            mockMvc.perform(post("/api/v1/auth/activate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activateRequestJson()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호 정책 불충족 (약한 비밀번호) → 400")
        void weakPassword() throws Exception {
            mockMvc.perform(post("/api/v1/auth/activate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678",
                                    "otpCode", "123456",
                                    "email", "teacher@school.com",
                                    "password", "weakpass"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/login
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/login — 로그인")
    class Login {

        private String loginRequestJson() throws Exception {
            return objectMapper.writeValueAsString(Map.of(
                    "email", "teacher@school.com",
                    "password", "Password1!"));
        }

        @Test
        @DisplayName("정상 로그인 → 200 + TokenResponse 반환")
        void loginSuccess() throws Exception {
            given(authService.login(any())).willReturn(sampleTokenResponse());

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.user.role").value("TEACHER"));
        }

        @Test
        @DisplayName("이메일/비밀번호 불일치 → 401")
        void authFailed() throws Exception {
            given(authService.login(any()))
                    .willThrow(new BusinessException(ErrorCode.AUTH_FAILED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("미활성화 계정 로그인 → 409")
        void accountNotActivated() throws Exception {
            given(authService.login(any()))
                    .willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("비활성화(isActive=false) 계정 → 403")
        void accountDisabled() throws Exception {
            given(authService.login(any()))
                    .willThrow(new BusinessException(ErrorCode.ACCOUNT_DISABLED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("잠긴 계정 로그인 → 423")
        void accountLocked() throws Exception {
            given(authService.login(any()))
                    .willThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson()))
                    .andExpect(status().is(423));
        }

        @Test
        @DisplayName("이메일 형식 오류 → 400")
        void invalidEmailFormat() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", "not-an-email",
                                    "password", "Password1!"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("필수 필드 누락 → 400")
        void missingField() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "teacher@school.com"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/refresh — 토큰 갱신")
    class Refresh {

        @Test
        @DisplayName("유효한 Refresh Token → 200 + 새 TokenResponse")
        void refreshSuccess() throws Exception {
            given(authService.refresh(any())).willReturn(sampleTokenResponse());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "refreshToken", "valid-refresh-token"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-value"));
        }

        @Test
        @DisplayName("만료된 Refresh Token → 401")
        void tokenExpired() throws Exception {
            given(authService.refresh(any()))
                    .willThrow(new BusinessException(ErrorCode.TOKEN_EXPIRED));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "refreshToken", "expired-refresh-token"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DB에 없는 Refresh Token (탈취/재사용) → 401")
        void invalidToken() throws Exception {
            given(authService.refresh(any()))
                    .willThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "refreshToken", "unknown-refresh-token"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("refreshToken 필드 누락 → 400")
        void missingRefreshToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/logout
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/logout — 로그아웃")
    class Logout {

        @Test
        @DisplayName("인증된 사용자 로그아웃 성공 → 200")
        void logoutSuccess() throws Exception {
            doNothing().when(authService).logout(any(), any());

            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(teacher()).with(csrf())
                            .header("Authorization", "Bearer valid-access-token")
                            .param("refreshToken", "valid-refresh-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("로그아웃되었습니다"));
        }

        @Test
        @DisplayName("refreshToken 없이 로그아웃 (AT만 블랙리스트) → 200")
        void logoutWithoutRefreshToken() throws Exception {
            doNothing().when(authService).logout(any(), isNull());

            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(teacher()).with(csrf())
                            .header("Authorization", "Bearer valid-access-token"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("미인증 사용자 로그아웃 시도 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(csrf())
                            .header("Authorization", "Bearer some-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ----------------------------------------------------------------
    // GET /api/v1/auth/me
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/auth/me — 내 정보 조회")
    class GetMe {

        @Test
        @DisplayName("인증된 교사 → 200 + UserResponse 반환")
        void getMeAsTeacher() throws Exception {
            given(authService.getMe(1L)).willReturn(sampleUserResponse());

            mockMvc.perform(get("/api/v1/auth/me").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.name").value("이교사"))
                    .andExpect(jsonPath("$.data.role").value("TEACHER"))
                    .andExpect(jsonPath("$.data.roleEntityId").value(10));
        }

        @Test
        @DisplayName("인증된 학생 → 200 + UserResponse 반환")
        void getMeAsStudent() throws Exception {
            UserResponse studentResponse = UserResponse.builder()
                    .id(2L).email("student@school.com").name("이학생")
                    .role("STUDENT").roleEntityId(20L).build();
            given(authService.getMe(2L)).willReturn(studentResponse);

            mockMvc.perform(get("/api/v1/auth/me").with(student()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("STUDENT"));
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 userId (토큰은 유효) → 404")
        void userNotFound() throws Exception {
            given(authService.getMe(1L))
                    .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get("/api/v1/auth/me").with(teacher()))
                    .andExpect(status().isNotFound());
        }
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/password/reset/request
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/password/reset/request — 비밀번호 재설정 OTP 요청")
    class PasswordResetRequest {

        @Test
        @DisplayName("등록된 전화번호 → 200")
        void requestSuccess() throws Exception {
            given(authService.requestPasswordReset(any())).willReturn("인증번호가 발송되었습니다");

            mockMvc.perform(post("/api/v1/auth/password/reset/request")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("인증번호가 발송되었습니다"));
        }

        @Test
        @DisplayName("미활성화 계정 → 409")
        void notActivated() throws Exception {
            given(authService.requestPasswordReset(any()))
                    .willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED));

            mockMvc.perform(post("/api/v1/auth/password/reset/request")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("전화번호 형식 오류 → 400")
        void invalidPhone() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/reset/request")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("phone", "01012345678"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/password/reset/confirm
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/password/reset/confirm — 비밀번호 재설정 확인")
    class PasswordResetConfirm {

        private String confirmRequestJson() throws Exception {
            return objectMapper.writeValueAsString(Map.of(
                    "phone", "010-1234-5678",
                    "otpCode", "123456",
                    "newPassword", "NewPass1!"));
        }

        @Test
        @DisplayName("정상 비밀번호 재설정 → 200")
        void confirmSuccess() throws Exception {
            doNothing().when(authService).confirmPasswordReset(any());

            mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmRequestJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다. 다시 로그인해주세요"));
        }

        @Test
        @DisplayName("미등록 전화번호 → 404")
        void phoneNotFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(authService).confirmPasswordReset(any());

            mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmRequestJson()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("OTP 불일치 → 400")
        void otpInvalid() throws Exception {
            doThrow(new BusinessException(ErrorCode.OTP_INVALID))
                    .when(authService).confirmPasswordReset(any());

            mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmRequestJson()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("OTP 만료 → 400")
        void otpExpired() throws Exception {
            doThrow(new BusinessException(ErrorCode.OTP_EXPIRED))
                    .when(authService).confirmPasswordReset(any());

            mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmRequestJson()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호 정책 불충족 → 400")
        void weakNewPassword() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-1234-5678",
                                    "otpCode", "123456",
                                    "newPassword", "weak"))))
                    .andExpect(status().isBadRequest());
        }
    }
}
