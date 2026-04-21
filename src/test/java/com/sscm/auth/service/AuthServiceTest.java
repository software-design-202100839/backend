package com.sscm.auth.service;

import com.sscm.auth.dto.*;
import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ParentRepository parentRepository;
    @Mock
    private ParentStudentRepository parentStudentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OtpService otpService;

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private void setId(Object target, String field, Long id) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User activatedTeacher() {
        User user = User.builder()
                .name("이교사")
                .role(Role.TEACHER)
                .isActive(true)
                .isActivated(true)
                .build();
        setId(user, "id", 1L);
        return user;
    }

    private User inactiveUser() {
        User user = User.builder()
                .name("이학생")
                .role(Role.STUDENT)
                .isActive(false)
                .isActivated(true)
                .build();
        setId(user, "id", 2L);
        return user;
    }

    private User notActivatedUser() {
        return User.builder()
                .name("미활성화")
                .role(Role.STUDENT)
                .isActive(true)
                .isActivated(false)
                .build();
    }

    private OtpSendRequest otpSendRequest(String purpose) {
        try {
            OtpSendRequest req = new OtpSendRequest();
            Field phone = OtpSendRequest.class.getDeclaredField("phone");
            Field purposeField = OtpSendRequest.class.getDeclaredField("purpose");
            phone.setAccessible(true);
            purposeField.setAccessible(true);
            phone.set(req, "010-1234-5678");
            purposeField.set(req, purpose);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ActivateRequest activateRequest() {
        try {
            ActivateRequest req = new ActivateRequest();
            Field phone = ActivateRequest.class.getDeclaredField("phone");
            Field otpCode = ActivateRequest.class.getDeclaredField("otpCode");
            Field email = ActivateRequest.class.getDeclaredField("email");
            Field password = ActivateRequest.class.getDeclaredField("password");
            phone.setAccessible(true); otpCode.setAccessible(true);
            email.setAccessible(true); password.setAccessible(true);
            phone.set(req, "010-1234-5678");
            otpCode.set(req, "123456");
            email.set(req, "teacher@school.com");
            password.set(req, "Password1!");
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private LoginRequest loginRequest() {
        LoginRequest req = new LoginRequest();
        req.setEmail("teacher@school.com");
        req.setPassword("Password1!");
        return req;
    }

    private RefreshRequest refreshRequest(String token) {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken(token);
        return req;
    }

    private PasswordResetRequest passwordResetRequest() {
        try {
            PasswordResetRequest req = new PasswordResetRequest();
            Field phone = PasswordResetRequest.class.getDeclaredField("phone");
            phone.setAccessible(true);
            phone.set(req, "010-1234-5678");
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PasswordResetConfirmRequest passwordResetConfirmRequest() {
        try {
            PasswordResetConfirmRequest req = new PasswordResetConfirmRequest();
            Field phone = PasswordResetConfirmRequest.class.getDeclaredField("phone");
            Field otpCode = PasswordResetConfirmRequest.class.getDeclaredField("otpCode");
            Field newPassword = PasswordResetConfirmRequest.class.getDeclaredField("newPassword");
            phone.setAccessible(true); otpCode.setAccessible(true); newPassword.setAccessible(true);
            phone.set(req, "010-1234-5678");
            otpCode.set(req, "123456");
            newPassword.set(req, "NewPass1!");
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----------------------------------------------------------------
    // sendOtp
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("sendOtp — OTP 발송")
    class SendOtp {

        @Test
        @DisplayName("미등록 번호 → 항상 '인증번호가 발송되었습니다' 반환 (열거 공격 방지)")
        void unknownPhoneReturnsMessage() {
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.empty());

            String result = authService.sendOtp(otpSendRequest("ACTIVATE"));

            assertThat(result).isEqualTo("인증번호가 발송되었습니다");
            verify(otpService, never()).issueOtp(any(), any());
        }

        @Test
        @DisplayName("미활성화 계정에 ACTIVATE 요청 → OTP 발송 + 메시지 반환")
        void activateOtpIssuedForUnactivated() {
            User user = notActivatedUser();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            given(otpService.issueOtp(any(), eq(OtpPurpose.ACTIVATE))).willReturn("123456");

            String result = authService.sendOtp(otpSendRequest("ACTIVATE"));

            assertThat(result).isEqualTo("인증번호가 발송되었습니다");
            verify(otpService).issueOtp(any(), eq(OtpPurpose.ACTIVATE));
        }

        @Test
        @DisplayName("이미 활성화된 계정에 ACTIVATE 요청 → ACCOUNT_ALREADY_ACTIVATED 예외")
        void activatedUserThrowsAlreadyActivated() {
            User user = activatedTeacher();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.sendOtp(otpSendRequest("ACTIVATE")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_ALREADY_ACTIVATED);
        }

        @Test
        @DisplayName("미활성화 계정에 PW_RESET 요청 → ACCOUNT_NOT_ACTIVATED 예외")
        void notActivatedUserThrowsForPwReset() {
            User user = notActivatedUser();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.sendOtp(otpSendRequest("PW_RESET")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }

        @Test
        @DisplayName("활성화 계정에 PW_RESET 요청 → OTP 발송 + 메시지 반환")
        void pwResetOtpIssuedForActivated() {
            User user = activatedTeacher();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            given(otpService.issueOtp(any(), eq(OtpPurpose.PW_RESET))).willReturn("654321");

            String result = authService.sendOtp(otpSendRequest("PW_RESET"));

            assertThat(result).isEqualTo("인증번호가 발송되었습니다");
            verify(otpService).issueOtp(any(), eq(OtpPurpose.PW_RESET));
        }
    }

    // ----------------------------------------------------------------
    // activate
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("activate — 계정 활성화")
    class Activate {

        @Test
        @DisplayName("정상 활성화 → UserResponse 반환 + activate() 호출")
        void activateSuccess() {
            User user = notActivatedUser();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            doNothing().when(otpService).verifyOtp(any(), eq(OtpPurpose.ACTIVATE), eq("123456"));
            given(userRepository.existsByEmailHash(any())).willReturn(false);
            given(passwordEncoder.encode(any())).willReturn("encoded-pw");
            given(studentRepository.findByUser(user)).willReturn(Optional.empty());

            UserResponse result = authService.activate(activateRequest());

            assertThat(result.getEmail()).isEqualTo("teacher@school.com");
            assertThat(user.getIsActivated()).isTrue();
        }

        @Test
        @DisplayName("미등록 전화번호 → RESOURCE_NOT_FOUND 예외")
        void phoneNotFound() {
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.activate(activateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 활성화된 계정 → ACCOUNT_ALREADY_ACTIVATED 예외")
        void alreadyActivated() {
            User user = activatedTeacher();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.activate(activateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_ALREADY_ACTIVATED);
        }

        @Test
        @DisplayName("이메일 중복 → DUPLICATE_RESOURCE 예외")
        void duplicateEmail() {
            User user = notActivatedUser();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            doNothing().when(otpService).verifyOtp(any(), any(), any());
            given(userRepository.existsByEmailHash(any())).willReturn(true);

            assertThatThrownBy(() -> authService.activate(activateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        }

        @Test
        @DisplayName("OTP 불일치 → OTP_INVALID 예외 (otpService에서 발생)")
        void otpInvalid() {
            User user = notActivatedUser();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            doThrow(new BusinessException(ErrorCode.OTP_INVALID))
                    .when(otpService).verifyOtp(any(), any(), any());

            assertThatThrownBy(() -> authService.activate(activateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_INVALID);
        }
    }

    // ----------------------------------------------------------------
    // requestPasswordReset
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("requestPasswordReset — 비밀번호 재설정 OTP 요청")
    class RequestPasswordReset {

        @Test
        @DisplayName("미등록 번호 → 항상 '인증번호가 발송되었습니다' 반환")
        void unknownPhoneReturnsMessage() {
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.empty());

            String result = authService.requestPasswordReset(passwordResetRequest());

            assertThat(result).isEqualTo("인증번호가 발송되었습니다");
            verify(otpService, never()).issueOtp(any(), any());
        }

        @Test
        @DisplayName("미활성화 계정 → ACCOUNT_NOT_ACTIVATED 예외")
        void notActivatedAccount() {
            User user = notActivatedUser();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.requestPasswordReset(passwordResetRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }

        @Test
        @DisplayName("활성화 계정 → OTP 발송 + 메시지 반환")
        void activatedAccountSuccess() {
            User user = activatedTeacher();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            given(otpService.issueOtp(any(), eq(OtpPurpose.PW_RESET))).willReturn("123456");

            String result = authService.requestPasswordReset(passwordResetRequest());

            assertThat(result).isEqualTo("인증번호가 발송되었습니다");
            verify(otpService).issueOtp(any(), eq(OtpPurpose.PW_RESET));
        }
    }

    // ----------------------------------------------------------------
    // confirmPasswordReset
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("confirmPasswordReset — 비밀번호 재설정 확인")
    class ConfirmPasswordReset {

        @Test
        @DisplayName("정상 재설정 → resetPassword() + deleteAllByUserId() 호출")
        void confirmSuccess() {
            User user = activatedTeacher();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            doNothing().when(otpService).verifyOtp(any(), eq(OtpPurpose.PW_RESET), eq("123456"));
            given(passwordEncoder.encode(any())).willReturn("new-encoded-pw");
            doNothing().when(refreshTokenService).deleteAllByUserId(1L);

            authService.confirmPasswordReset(passwordResetConfirmRequest());

            assertThat(user.getPasswordHash()).isEqualTo("new-encoded-pw");
            verify(refreshTokenService).deleteAllByUserId(1L);
        }

        @Test
        @DisplayName("미등록 전화번호 → RESOURCE_NOT_FOUND 예외")
        void phoneNotFound() {
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.confirmPasswordReset(passwordResetConfirmRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("OTP 만료 → OTP_EXPIRED 예외")
        void otpExpired() {
            User user = activatedTeacher();
            given(userRepository.findByPhoneHash(any())).willReturn(Optional.of(user));
            doThrow(new BusinessException(ErrorCode.OTP_EXPIRED))
                    .when(otpService).verifyOtp(any(), any(), any());

            assertThatThrownBy(() -> authService.confirmPasswordReset(passwordResetConfirmRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_EXPIRED);
        }
    }

    // ----------------------------------------------------------------
    // login
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("login — 로그인")
    class Login {

        @Test
        @DisplayName("정상 로그인 → TokenResponse 반환 + recordLoginSuccess() 호출")
        void loginSuccess() {
            User user = activatedTeacher();
            given(userRepository.findByEmailHash(any())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(any(), any())).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(), any(), any())).willReturn("at-value");
            given(jwtTokenProvider.createRefreshToken(any())).willReturn("rt-value");
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604800000L);
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);
            given(teacherRepository.findByUser(user)).willReturn(Optional.empty());
            doNothing().when(refreshTokenService).save(any(), any(), any());

            TokenResponse result = authService.login(loginRequest());

            assertThat(result.getAccessToken()).isEqualTo("at-value");
            assertThat(result.getRefreshToken()).isEqualTo("rt-value");
            assertThat(user.getFailedLoginCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("존재하지 않는 이메일 → AUTH_FAILED 예외")
        void emailNotFound() {
            given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_FAILED);
        }

        @Test
        @DisplayName("미활성화 계정 → ACCOUNT_NOT_ACTIVATED 예외")
        void accountNotActivated() {
            User user = notActivatedUser();
            given(userRepository.findByEmailHash(any())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }

        @Test
        @DisplayName("isActive=false 계정 → ACCOUNT_DISABLED 예외")
        void accountDisabled() {
            User user = inactiveUser();
            given(userRepository.findByEmailHash(any())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        }

        @Test
        @DisplayName("잠긴 계정 → ACCOUNT_LOCKED 예외")
        void accountLocked() {
            User user = User.builder()
                    .name("이교사").role(Role.TEACHER)
                    .isActive(true).isActivated(true)
                    .loginLockedUntil(Instant.now().plusSeconds(600))
                    .build();
            setId(user, "id", 1L);
            given(userRepository.findByEmailHash(any())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        }

        @Test
        @DisplayName("비밀번호 불일치 → AUTH_FAILED 예외 + failedLoginCount 증가")
        void wrongPassword() {
            User user = activatedTeacher();
            given(userRepository.findByEmailHash(any())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(any(), any())).willReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_FAILED);

            assertThat(user.getFailedLoginCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("비밀번호 5회 실패 → ACCOUNT_LOCKED 잠금 + failedLoginCount=5")
        void loginFailureLock() {
            User user = activatedTeacher();
            given(userRepository.findByEmailHash(any())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(any(), any())).willReturn(false);

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> authService.login(loginRequest()))
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_FAILED);
            }

            assertThat(user.getFailedLoginCount()).isEqualTo(5);
            assertThat(user.isLocked()).isTrue();
        }
    }

    // ----------------------------------------------------------------
    // refresh
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("refresh — 토큰 갱신")
    class RefreshToken {

        @Test
        @DisplayName("유효한 Refresh Token → 새 TokenResponse 반환 + 기존 RT 삭제")
        void refreshSuccess() {
            User user = activatedTeacher();
            com.sscm.auth.entity.RefreshToken storedRt = com.sscm.auth.entity.RefreshToken.builder()
                    .userId(1L)
                    .tokenHash("hashed-rt")
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            given(jwtTokenProvider.validateToken("valid-rt")).willReturn(true);
            given(refreshTokenService.findByTokenHash(any())).willReturn(Optional.of(storedRt));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtTokenProvider.createAccessToken(any(), any(), any())).willReturn("new-at");
            given(jwtTokenProvider.createRefreshToken(any())).willReturn("new-rt");
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604800000L);
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);
            given(teacherRepository.findByUser(user)).willReturn(Optional.empty());
            doNothing().when(refreshTokenService).save(any(), any(), any());
            doNothing().when(refreshTokenService).deleteByTokenHash(any());

            TokenResponse result = authService.refresh(refreshRequest("valid-rt"));

            assertThat(result.getAccessToken()).isEqualTo("new-at");
            verify(refreshTokenService).deleteByTokenHash(any());
        }

        @Test
        @DisplayName("JWT 검증 실패 → TOKEN_EXPIRED 예외")
        void jwtValidationFails() {
            given(jwtTokenProvider.validateToken(any())).willReturn(false);

            assertThatThrownBy(() -> authService.refresh(refreshRequest("expired-rt")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("DB에 토큰 없음 → INVALID_TOKEN 예외")
        void tokenNotInDb() {
            given(jwtTokenProvider.validateToken(any())).willReturn(true);
            given(refreshTokenService.findByTokenHash(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(refreshRequest("unknown-rt")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("DB 토큰 만료(isExpired=true) → TOKEN_EXPIRED 예외")
        void storedTokenExpired() {
            com.sscm.auth.entity.RefreshToken expiredRt = com.sscm.auth.entity.RefreshToken.builder()
                    .userId(1L)
                    .tokenHash("expired-hash")
                    .expiresAt(Instant.now().minusSeconds(10))
                    .build();

            given(jwtTokenProvider.validateToken(any())).willReturn(true);
            given(refreshTokenService.findByTokenHash(any())).willReturn(Optional.of(expiredRt));

            assertThatThrownBy(() -> authService.refresh(refreshRequest("some-rt")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_EXPIRED);
        }
    }

    // ----------------------------------------------------------------
    // logout
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("logout — 로그아웃")
    class Logout {

        @Test
        @DisplayName("remainingMillis > 0 → 블랙리스트 추가 + RT 삭제")
        void logoutWithValidAt() {
            given(jwtTokenProvider.getRemainingExpiration("valid-at")).willReturn(60000L);
            doNothing().when(tokenBlacklistService).addToBlacklist(any(), any());
            doNothing().when(refreshTokenService).deleteByTokenHash(any());

            authService.logout("valid-at", "valid-rt");

            verify(tokenBlacklistService).addToBlacklist(any(), any());
            verify(refreshTokenService).deleteByTokenHash(any());
        }

        @Test
        @DisplayName("remainingMillis <= 0 → 블랙리스트 추가 안 함")
        void logoutWithExpiredAt() {
            given(jwtTokenProvider.getRemainingExpiration("expired-at")).willReturn(0L);
            doNothing().when(refreshTokenService).deleteByTokenHash(any());

            authService.logout("expired-at", "valid-rt");

            verify(tokenBlacklistService, never()).addToBlacklist(any(), any());
            verify(refreshTokenService).deleteByTokenHash(any());
        }

        @Test
        @DisplayName("refreshToken null → RT 삭제 안 함, AT만 처리")
        void logoutWithoutRefreshToken() {
            given(jwtTokenProvider.getRemainingExpiration("valid-at")).willReturn(60000L);
            doNothing().when(tokenBlacklistService).addToBlacklist(any(), any());

            authService.logout("valid-at", null);

            verify(tokenBlacklistService).addToBlacklist(any(), any());
            verify(refreshTokenService, never()).deleteByTokenHash(any());
        }
    }

    // ----------------------------------------------------------------
    // getMe
    // ----------------------------------------------------------------
    @Nested
    @DisplayName("getMe — 내 정보 조회")
    class GetMe {

        @Test
        @DisplayName("교사 userId → UserResponse 반환 (roleEntityId 포함)")
        void getMeTeacher() {
            User user = activatedTeacher();
            Teacher teacher = Teacher.builder().user(user).build();
            setId(teacher, "id", 10L);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(teacherRepository.findByUser(user)).willReturn(Optional.of(teacher));

            UserResponse result = authService.getMe(1L);

            assertThat(result.getName()).isEqualTo("이교사");
            assertThat(result.getRole()).isEqualTo("TEACHER");
            assertThat(result.getRoleEntityId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("학생 userId → roleEntityId 포함 반환")
        void getMeStudent() {
            User user = User.builder()
                    .name("이학생").role(Role.STUDENT)
                    .isActive(true).isActivated(true).build();
            setId(user, "id", 2L);

            Student student = Student.builder().user(user).build();
            setId(student, "id", 20L);

            given(userRepository.findById(2L)).willReturn(Optional.of(user));
            given(studentRepository.findByUser(user)).willReturn(Optional.of(student));

            UserResponse result = authService.getMe(2L);

            assertThat(result.getRole()).isEqualTo("STUDENT");
            assertThat(result.getRoleEntityId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("존재하지 않는 userId → RESOURCE_NOT_FOUND 예외")
        void userNotFound() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getMe(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("학부모 userId → children 포함 반환")
        void getMeParent() {
            User parentUser = User.builder()
                    .name("이학부모").role(Role.PARENT)
                    .isActive(true).isActivated(true).build();
            setId(parentUser, "id", 3L);

            Parent parent = Parent.builder().user(parentUser).build();
            setId(parent, "id", 30L);

            given(userRepository.findById(3L)).willReturn(Optional.of(parentUser));
            given(parentRepository.findByUser(parentUser)).willReturn(Optional.of(parent));
            given(parentStudentRepository.findByParent(parent)).willReturn(List.of());

            UserResponse result = authService.getMe(3L);

            assertThat(result.getRole()).isEqualTo("PARENT");
            assertThat(result.getChildren()).isNotNull().isEmpty();
        }
    }
}
