package com.sscm.auth.service;

import com.sscm.auth.repository.RefreshTokenRepository;
import com.sscm.auth.repository.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenCleanupService 단위 테스트")
class TokenCleanupServiceTest {

    @InjectMocks
    private TokenCleanupService tokenCleanupService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    @DisplayName("cleanupExpiredTokens — 만료 토큰 삭제 양쪽 리포지터리에 위임")
    void cleanupExpiredTokens() {
        given(refreshTokenRepository.deleteExpired(any(Instant.class))).willReturn(3);
        given(tokenBlacklistRepository.deleteExpired(any(Instant.class))).willReturn(5);

        tokenCleanupService.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteExpired(any(Instant.class));
        verify(tokenBlacklistRepository).deleteExpired(any(Instant.class));
    }
}
