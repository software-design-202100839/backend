package com.sscm.auth.service;

import com.sscm.auth.entity.TokenBlacklist;
import com.sscm.auth.repository.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService 단위 테스트")
class TokenBlacklistServiceTest {

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    @DisplayName("addToBlacklist — 미존재 시 저장")
    void addToBlacklist_new() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        given(tokenBlacklistRepository.existsByTokenHash("hash")).willReturn(false);

        tokenBlacklistService.addToBlacklist("hash", expiresAt);

        verify(tokenBlacklistRepository).save(any(TokenBlacklist.class));
    }

    @Test
    @DisplayName("addToBlacklist — 이미 존재하면 저장 건너뜀")
    void addToBlacklist_duplicate() {
        given(tokenBlacklistRepository.existsByTokenHash("hash")).willReturn(true);

        tokenBlacklistService.addToBlacklist("hash", Instant.now().plusSeconds(3600));

        verify(tokenBlacklistRepository, never()).save(any());
    }

    @Test
    @DisplayName("isBlacklisted — true 반환")
    void isBlacklisted_true() {
        given(tokenBlacklistRepository.existsByTokenHash("hash")).willReturn(true);

        assertThat(tokenBlacklistService.isBlacklisted("hash")).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted — false 반환")
    void isBlacklisted_false() {
        given(tokenBlacklistRepository.existsByTokenHash("none")).willReturn(false);

        assertThat(tokenBlacklistService.isBlacklisted("none")).isFalse();
    }
}
