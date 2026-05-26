package com.sscm.auth.service;

import com.sscm.auth.entity.TokenBlacklist;
import com.sscm.auth.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT 블랙리스트 관리 서비스.
 *
 * Redis를 1차 캐시로 사용하고, DB를 영구 저장소로 사용.
 * - isBlacklisted(): Redis 먼저 조회 → 없으면 DB 조회 (캐시 miss 시)
 * - addToBlacklist(): Redis + DB 동시 저장
 *
 * 왜 Redis?
 * - 매 API 요청마다 블랙리스트 체크 발생 (JwtAuthenticationFilter)
 * - DB SELECT: ~2-5ms + 커넥션 점유
 * - Redis GET: <1ms, 커넥션 풀 부담 없음
 * - 200 동시 사용자 기준 DB 커넥션 고갈 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final StringRedisTemplate redisTemplate;
    private final Counter redisCacheHitCounter;
    private final Counter redisCacheMissCounter;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

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

        // Redis에도 저장 (TTL = 토큰 만료 시간까지)
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative()) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + tokenHash, "1", ttl);
        }
    }

    /**
     * Redis 1차 → DB 2차 조회.
     * Redis 장애 시 DB fallback으로 서비스 중단 없음.
     */
    public boolean isBlacklisted(String tokenHash) {
        // 1차: Redis 조회 (<1ms)
        try {
            Boolean exists = redisTemplate.hasKey(BLACKLIST_PREFIX + tokenHash);
            if (Boolean.TRUE.equals(exists)) {
                redisCacheHitCounter.increment();
                return true;
            }
            redisCacheMissCounter.increment();
        } catch (Exception e) {
            redisCacheMissCounter.increment();
            // Redis 장애 시 DB fallback (느려질 뿐, 서비스 중단 없음)
            log.warn("Redis 조회 실패, DB fallback: {}", e.getMessage());
        }

        // 2차: DB 조회 (캐시 miss 또는 Redis 장애)
        boolean blacklisted = tokenBlacklistRepository.existsByTokenHash(tokenHash);

        // 캐시 miss인 경우 Redis에 저장 (다음 요청부터 캐시 hit)
        if (blacklisted) {
            try {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + tokenHash, "1", Duration.ofHours(1));
            } catch (Exception e) {
                log.warn("Redis 캐시 저장 실패: {}", e.getMessage());
            }
        }

        return blacklisted;
    }
}
