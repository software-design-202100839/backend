package com.sscm.auth.service;

import com.sscm.auth.repository.RefreshTokenRepository;
import com.sscm.auth.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    // 매일 새벽 03:00에 만료된 토큰 일괄 삭제
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        int deletedRt = refreshTokenRepository.deleteExpired(now);
        int deletedBl = tokenBlacklistRepository.deleteExpired(now);
        log.info("토큰 정리 완료 — RefreshToken: {}건, Blacklist: {}건 삭제", deletedRt, deletedBl);
    }
}
