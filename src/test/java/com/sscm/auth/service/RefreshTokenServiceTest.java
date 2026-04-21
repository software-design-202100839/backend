package com.sscm.auth.service;

import com.sscm.auth.entity.RefreshToken;
import com.sscm.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService 단위 테스트")
class RefreshTokenServiceTest {

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("save — RefreshToken 엔티티 생성 후 저장")
    void save() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        given(refreshTokenRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        refreshTokenService.save(1L, "tokenHash", expiresAt);

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getTokenHash()).isEqualTo("tokenHash");
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("findByTokenHash — 리포지터리에 위임")
    void findByTokenHash() {
        RefreshToken token = RefreshToken.builder()
                .userId(1L).tokenHash("hash").expiresAt(Instant.now().plusSeconds(3600)).build();
        given(refreshTokenRepository.findByTokenHash("hash")).willReturn(Optional.of(token));

        Optional<RefreshToken> result = refreshTokenService.findByTokenHash("hash");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByTokenHash — 없으면 empty")
    void findByTokenHash_empty() {
        given(refreshTokenRepository.findByTokenHash("none")).willReturn(Optional.empty());

        Optional<RefreshToken> result = refreshTokenService.findByTokenHash("none");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteByTokenHash — 리포지터리에 위임")
    void deleteByTokenHash() {
        refreshTokenService.deleteByTokenHash("hash");

        verify(refreshTokenRepository).deleteByTokenHash("hash");
    }

    @Test
    @DisplayName("deleteAllByUserId — 리포지터리에 위임")
    void deleteAllByUserId() {
        refreshTokenService.deleteAllByUserId(1L);

        verify(refreshTokenRepository).deleteAllByUserId(1L);
    }
}
