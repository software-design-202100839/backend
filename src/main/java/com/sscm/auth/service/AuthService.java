package com.sscm.auth.service;

import com.sscm.auth.dto.*;
import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.crypto.EncryptionUtil;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String OTP_SENT_MESSAGE = "인증번호가 발송되었습니다";

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;

    /**
     * OTP 발송 — ACTIVATE(최초 활성화) 또는 PW_RESET(비밀번호 찾기)
     * 미등록 번호도 동일 메시지 반환 (전화번호 열거 공격 방지)
     */
    @Transactional
    public String sendOtp(OtpSendRequest request) {
        OtpPurpose purpose = OtpPurpose.valueOf(request.getPurpose());
        String phoneHash = EncryptionUtil.sha256(request.getPhone());

        userRepository.findByPhoneHash(phoneHash).ifPresent(user -> {
            if (purpose == OtpPurpose.ACTIVATE && user.getIsActivated()) {
                throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_ACTIVATED);
            }
            if (purpose == OtpPurpose.PW_RESET && !user.getIsActivated()) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
            }

            String otpCode = otpService.issueOtp(phoneHash, purpose);
            // SMS 발송 (Phase 6에서 Solapi 연동 예정)
            log.info("[OTP] phone={}, purpose={}, code={}", request.getPhone(), purpose, otpCode);
        });

        return OTP_SENT_MESSAGE;
    }

    /**
     * 계정 활성화 — OTP 검증 후 이메일+비밀번호 설정
     */
    @Transactional
    public UserResponse activate(ActivateRequest request) {
        String phoneHash = EncryptionUtil.sha256(request.getPhone());

        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (user.getIsActivated()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_ACTIVATED);
        }

        otpService.verifyOtp(phoneHash, OtpPurpose.ACTIVATE, request.getOtpCode());

        if (userRepository.existsByEmailHash(EncryptionUtil.sha256(request.getEmail()))) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        user.activate(request.getEmail(), passwordEncoder.encode(request.getPassword()));

        return UserResponse.from(user, resolveRoleEntityId(user));
    }

    /**
     * 비밀번호 찾기 요청 — OTP 발송
     */
    @Transactional
    public String requestPasswordReset(PasswordResetRequest request) {
        String phoneHash = EncryptionUtil.sha256(request.getPhone());

        userRepository.findByPhoneHash(phoneHash).ifPresent(user -> {
            if (!user.getIsActivated()) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
            }
            String otpCode = otpService.issueOtp(phoneHash, OtpPurpose.PW_RESET);
            log.info("[OTP] phone={}, purpose=PW_RESET, code={}", request.getPhone(), otpCode);
        });

        return OTP_SENT_MESSAGE;
    }

    /**
     * 비밀번호 찾기 확인 — OTP 검증 후 새 비밀번호 설정
     */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String phoneHash = EncryptionUtil.sha256(request.getPhone());

        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        otpService.verifyOtp(phoneHash, OtpPurpose.PW_RESET, request.getOtpCode());

        user.resetPassword(passwordEncoder.encode(request.getNewPassword()));

        // 기존 세션 모두 강제 만료
        refreshTokenService.deleteAllByUserId(user.getId());
    }

    /**
     * 로그인 — 로그인 잠금 + 실패 카운트 포함
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailHash(EncryptionUtil.sha256(request.getEmail()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED));

        if (!user.getIsActivated()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        if (user.isLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.recordLoginFailure();
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        user.recordLoginSuccess();
        return issueTokens(user);
    }

    /**
     * 토큰 갱신 — Refresh Token Rotation
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String rawRt = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(rawRt)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        String tokenHash = EncryptionUtil.sha256(rawRt);
        RefreshToken stored = refreshTokenService.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (stored.isExpired()) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        refreshTokenService.deleteByTokenHash(tokenHash);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return issueTokens(user);
    }

    /**
     * 로그아웃 — AT 블랙리스트 + RT 삭제
     */
    @Transactional
    public void logout(String rawAccessToken, String rawRefreshToken) {
        long remainingMillis = jwtTokenProvider.getRemainingExpiration(rawAccessToken);
        if (remainingMillis > 0) {
            Instant expiresAt = Instant.now().plusMillis(remainingMillis);
            tokenBlacklistService.addToBlacklist(EncryptionUtil.sha256(rawAccessToken), expiresAt);
        }

        if (rawRefreshToken != null) {
            refreshTokenService.deleteByTokenHash(EncryptionUtil.sha256(rawRefreshToken));
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return UserResponse.from(user, resolveRoleEntityId(user));
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        Instant rtExpiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpiration());
        refreshTokenService.save(user.getId(), EncryptionUtil.sha256(refreshToken), rtExpiresAt);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(UserResponse.from(user, resolveRoleEntityId(user)))
                .build();
    }

    private Long resolveRoleEntityId(User user) {
        return switch (user.getRole()) {
            case TEACHER -> teacherRepository.findByUser(user).map(Teacher::getId).orElse(null);
            case STUDENT -> studentRepository.findByUser(user).map(Student::getId).orElse(null);
            case PARENT, ADMIN -> null;
        };
    }
}
