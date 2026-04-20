package com.sscm.auth.service;

import com.sscm.auth.dto.*;
import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.crypto.EncryptionUtil;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailHash(EncryptionUtil.sha256(request.getEmail()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        return issueTokens(user);
    }

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

        // Rotation: 기존 RT 즉시 삭제
        refreshTokenService.deleteByTokenHash(tokenHash);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return issueTokens(user);
    }

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
