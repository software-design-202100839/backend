package com.sscm.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final RedisTemplate<String, String> redisTemplate;

    public void blacklistAccessToken(String token, long remainingMillis) {
        redisTemplate.opsForValue()
                .set(BLACKLIST_PREFIX + token, "true", remainingMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    public void saveRefreshToken(Long userId, String refreshToken, long expirationMillis) {
        redisTemplate.opsForValue()
                .set(REFRESH_PREFIX + userId, refreshToken, expirationMillis, TimeUnit.MILLISECONDS);
    }

    public String getRefreshToken(Long userId) {
        return redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }
}
