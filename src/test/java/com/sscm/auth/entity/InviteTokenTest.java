package com.sscm.auth.entity;

import com.sscm.auth.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InviteToken & RefreshToken 엔티티 단위 테스트")
class InviteTokenTest {

    private InviteToken buildToken(Instant expiresAt, Instant usedAt, int attemptCount) {
        return InviteToken.builder()
                .phoneHash("hash123")
                .otpCode("123456")
                .purpose(OtpPurpose.ACTIVATE)
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .attemptCount(attemptCount)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isExpired
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("만료 시각 이전 → isExpired false")
    void isExpired_false() {
        InviteToken token = buildToken(Instant.now().plusSeconds(300), null, 0);
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("만료 시각 이후 → isExpired true")
    void isExpired_true() {
        InviteToken token = buildToken(Instant.now().minusSeconds(1), null, 0);
        assertThat(token.isExpired()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isUsed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("사용 전 → isUsed false")
    void isUsed_false() {
        InviteToken token = buildToken(Instant.now().plusSeconds(300), null, 0);
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    @DisplayName("사용 후 → isUsed true")
    void isUsed_true() {
        InviteToken token = buildToken(Instant.now().plusSeconds(300), Instant.now(), 0);
        assertThat(token.isUsed()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isExhausted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시도 4회 → isExhausted false")
    void isExhausted_false() {
        InviteToken token = buildToken(Instant.now().plusSeconds(300), null, 4);
        assertThat(token.isExhausted()).isFalse();
    }

    @Test
    @DisplayName("시도 5회 → isExhausted true")
    void isExhausted_true() {
        InviteToken token = buildToken(Instant.now().plusSeconds(300), null, 5);
        assertThat(token.isExhausted()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // incrementAttempt / markUsed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("incrementAttempt → 시도 횟수 증가")
    void incrementAttempt() {
        InviteToken token = buildToken(Instant.now().plusSeconds(300), null, 2);
        token.incrementAttempt();
        assertThat(token.getAttemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("markUsed → usedAt 설정됨")
    void markUsed() {
        InviteToken token = buildToken(Instant.now().plusSeconds(300), null, 0);
        assertThat(token.isUsed()).isFalse();

        token.markUsed();

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RefreshToken.isExpired
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RefreshToken — 만료 전 → isExpired false")
    void refreshToken_notExpired() {
        RefreshToken token = RefreshToken.builder()
                .userId(1L)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        assertThat(token.isExpired()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User 엔티티 메서드
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("User.setPhoneHash — 전화번호 해시 갱신")
    void user_setPhoneHash() {
        User user = User.builder()
                .name("테스트").role(Role.STUDENT).isActive(true).isActivated(false)
                .phoneHash("old_hash").build();

        user.setPhoneHash("new_hash");

        assertThat(user.getPhoneHash()).isEqualTo("new_hash");
    }

    @Test
    @DisplayName("RefreshToken — 만료 후 → isExpired true")
    void refreshToken_expired() {
        RefreshToken token = RefreshToken.builder()
                .userId(1L)
                .tokenHash("hash")
                .expiresAt(Instant.now().minusSeconds(1))
                .build();
        assertThat(token.isExpired()).isTrue();
    }
}
