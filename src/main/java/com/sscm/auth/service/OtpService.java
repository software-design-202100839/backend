package com.sscm.auth.service;

import com.sscm.auth.entity.InviteToken;
import com.sscm.auth.entity.OtpPurpose;
import com.sscm.auth.repository.InviteTokenRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RATE_LIMIT_SECONDS = 60;
    private final SecureRandom secureRandom = new SecureRandom();

    private final InviteTokenRepository inviteTokenRepository;

    @Transactional
    public String issueOtp(String phoneHash, OtpPurpose purpose) {
        // 1분 쿨다운 확인
        inviteTokenRepository.findLatestCreatedAt(phoneHash, purpose).ifPresent(lastCreated -> {
            if (lastCreated.plusSeconds(RATE_LIMIT_SECONDS).isAfter(Instant.now())) {
                throw new BusinessException(ErrorCode.OTP_RATE_LIMIT);
            }
        });

        // 기존 미사용 OTP 폐기
        inviteTokenRepository.invalidateAll(phoneHash, purpose, Instant.now());

        // 신규 OTP 생성 (6자리)
        String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        inviteTokenRepository.save(
                InviteToken.builder()
                        .phoneHash(phoneHash)
                        .otpCode(otpCode)
                        .purpose(purpose)
                        .expiresAt(expiresAt)
                        .build()
        );

        return otpCode;
    }

    @Transactional
    public void verifyOtp(String phoneHash, OtpPurpose purpose, String inputCode) {
        Instant now = Instant.now();
        InviteToken token = inviteTokenRepository.findActiveToken(phoneHash, purpose, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_EXPIRED));

        if (token.isExhausted()) {
            throw new BusinessException(ErrorCode.OTP_EXHAUSTED);
        }

        if (!token.getOtpCode().equals(inputCode)) {
            token.incrementAttempt();
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }

        token.markUsed();
    }
}
