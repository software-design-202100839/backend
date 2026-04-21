package com.sscm.auth.service;

import com.sscm.auth.entity.TokenBlacklist;
import com.sscm.auth.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Transactional
    public void addToBlacklist(String tokenHash, Instant expiresAt) {
        if (!tokenBlacklistRepository.existsByTokenHash(tokenHash)) {
            tokenBlacklistRepository.save(
                    TokenBlacklist.builder()
                            .tokenHash(tokenHash)
                            .expiresAt(expiresAt)
                            .build()
            );
        }
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String tokenHash) {
        return tokenBlacklistRepository.existsByTokenHash(tokenHash);
    }
}
