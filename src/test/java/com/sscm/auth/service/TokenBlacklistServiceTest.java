package com.sscm.auth.service;

import com.sscm.auth.entity.TokenBlacklist;
import com.sscm.auth.repository.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService 단위 테스트")
class TokenBlacklistServiceTest {

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("addToBlacklist — 미존재 시 DB + Redis 저장")
    void addToBlacklist_new() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        given(tokenBlacklistRepository.existsByTokenHash("hash")).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        tokenBlacklistService.addToBlacklist("hash", expiresAt);

        verify(tokenBlacklistRepository).save(any(TokenBlacklist.class));
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("addToBlacklist — 이미 존재하면 DB 저장 건너뜀, Redis에는 저장")
    void addToBlacklist_duplicate() {
        given(tokenBlacklistRepository.existsByTokenHash("hash")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        tokenBlacklistService.addToBlacklist("hash", Instant.now().plusSeconds(3600));

        verify(tokenBlacklistRepository, never()).save(any());
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("isBlacklisted — Redis에 있으면 DB 조회 안 함")
    void isBlacklisted_redisHit() {
        given(redisTemplate.hasKey("token:blacklist:hash")).willReturn(true);

        assertThat(tokenBlacklistService.isBlacklisted("hash")).isTrue();
        verify(tokenBlacklistRepository, never()).existsByTokenHash(anyString());
    }

    @Test
    @DisplayName("isBlacklisted — Redis miss → DB 조회 → 캐시 저장")
    void isBlacklisted_redisMiss_dbHit() {
        given(redisTemplate.hasKey("token:blacklist:hash")).willReturn(false);
        given(tokenBlacklistRepository.existsByTokenHash("hash")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        assertThat(tokenBlacklistService.isBlacklisted("hash")).isTrue();
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("isBlacklisted — Redis miss + DB miss → false")
    void isBlacklisted_bothMiss() {
        given(redisTemplate.hasKey("token:blacklist:none")).willReturn(false);
        given(tokenBlacklistRepository.existsByTokenHash("none")).willReturn(false);

        assertThat(tokenBlacklistService.isBlacklisted("none")).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted — Redis 장애 시 DB fallback")
    void isBlacklisted_redisFailure_fallbackToDb() {
        given(redisTemplate.hasKey(anyString())).willThrow(new RuntimeException("Redis 연결 실패"));
        given(tokenBlacklistRepository.existsByTokenHash("hash")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        doThrow(new RuntimeException("Redis 저장 실패")).when(valueOperations).set(anyString(), anyString(), any());

        assertThat(tokenBlacklistService.isBlacklisted("hash")).isTrue();
    }
}
