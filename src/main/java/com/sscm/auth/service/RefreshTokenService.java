package com.sscm.auth.service;

import com.sscm.auth.entity.RefreshToken;
import com.sscm.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void save(Long userId, String tokenHash, Instant expiresAt) {
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .userId(userId)
                        .tokenHash(tokenHash)
                        .expiresAt(expiresAt)
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    @Transactional
    public void deleteByTokenHash(String tokenHash) {
        refreshTokenRepository.deleteByTokenHash(tokenHash);
    }

    @Transactional
    public void deleteAllByUserId(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }
}
