package com.sscm.auth.service;

import com.sscm.auth.entity.InviteToken;
import com.sscm.auth.entity.OtpPurpose;
import com.sscm.auth.repository.InviteTokenRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService 단위 테스트")
class OtpServiceTest {

    @InjectMocks
    private OtpService otpService;

    @Mock
    private InviteTokenRepository inviteTokenRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // issueOtp
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OTP 발급")
    class IssueOtp {

        @Test
        @DisplayName("쿨다운 이내 요청 → OTP_RATE_LIMIT")
        void rateLimitExceeded() {
            Instant recent = Instant.now().minusSeconds(30); // 30초 전 (60초 쿨다운 미달)
            given(inviteTokenRepository.findLatestCreatedAt("hash123", OtpPurpose.ACTIVATE))
                    .willReturn(Optional.of(recent));

            assertThatThrownBy(() -> otpService.issueOtp("hash123", OtpPurpose.ACTIVATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.OTP_RATE_LIMIT);

            verify(inviteTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("쿨다운 초과 후 정상 발급")
        void issueSuccess() {
            Instant old = Instant.now().minusSeconds(61); // 61초 전 (쿨다운 초과)
            given(inviteTokenRepository.findLatestCreatedAt("hash123", OtpPurpose.ACTIVATE))
                    .willReturn(Optional.of(old));

            ArgumentCaptor<InviteToken> captor = ArgumentCaptor.forClass(InviteToken.class);
            given(inviteTokenRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            String otp = otpService.issueOtp("hash123", OtpPurpose.ACTIVATE);

            assertThat(otp).matches("\\d{6}");
            verify(inviteTokenRepository).invalidateAll(eq("hash123"), eq(OtpPurpose.ACTIVATE), any(Instant.class));
            verify(inviteTokenRepository).save(any(InviteToken.class));
        }

        @Test
        @DisplayName("발급 이력 없음 → 정상 발급")
        void issueSuccessNoHistory() {
            given(inviteTokenRepository.findLatestCreatedAt("hash456", OtpPurpose.ACTIVATE))
                    .willReturn(Optional.empty());
            given(inviteTokenRepository.save(any(InviteToken.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            String otp = otpService.issueOtp("hash456", OtpPurpose.ACTIVATE);

            assertThat(otp).matches("\\d{6}");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verifyOtp
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OTP 검증")
    class VerifyOtp {

        private InviteToken buildToken(boolean exhausted, String code) {
            InviteToken token = InviteToken.builder()
                    .phoneHash("hash123")
                    .otpCode(code)
                    .purpose(OtpPurpose.ACTIVATE)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .attemptCount(exhausted ? 5 : 0)
                    .build();
            return token;
        }

        @Test
        @DisplayName("활성 토큰 없음 → OTP_EXPIRED")
        void noActiveToken() {
            given(inviteTokenRepository.findActiveToken(eq("hash123"), eq(OtpPurpose.ACTIVATE), any(Instant.class)))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> otpService.verifyOtp("hash123", OtpPurpose.ACTIVATE, "123456"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.OTP_EXPIRED);
        }

        @Test
        @DisplayName("시도 횟수 초과 → OTP_EXHAUSTED")
        void exhausted() {
            InviteToken token = buildToken(true, "123456");
            given(inviteTokenRepository.findActiveToken(eq("hash123"), eq(OtpPurpose.ACTIVATE), any(Instant.class)))
                    .willReturn(Optional.of(token));

            assertThatThrownBy(() -> otpService.verifyOtp("hash123", OtpPurpose.ACTIVATE, "123456"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.OTP_EXHAUSTED);
        }

        @Test
        @DisplayName("코드 불일치 → OTP_INVALID + incrementAttempt")
        void invalidCode() {
            InviteToken token = buildToken(false, "999999");
            given(inviteTokenRepository.findActiveToken(eq("hash123"), eq(OtpPurpose.ACTIVATE), any(Instant.class)))
                    .willReturn(Optional.of(token));

            assertThatThrownBy(() -> otpService.verifyOtp("hash123", OtpPurpose.ACTIVATE, "000000"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.OTP_INVALID);

            assertThat(token.getAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("코드 일치 → 성공, markUsed 호출")
        void success() {
            InviteToken token = buildToken(false, "123456");
            given(inviteTokenRepository.findActiveToken(eq("hash123"), eq(OtpPurpose.ACTIVATE), any(Instant.class)))
                    .willReturn(Optional.of(token));

            otpService.verifyOtp("hash123", OtpPurpose.ACTIVATE, "123456");

            assertThat(token.isUsed()).isTrue();
        }
    }
}
