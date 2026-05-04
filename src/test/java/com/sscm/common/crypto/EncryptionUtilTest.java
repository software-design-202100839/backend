package com.sscm.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptionUtilTest {

    @Nested
    @DisplayName("SHA-256 해시")
    class Sha256 {

        @Test
        @DisplayName("동일 입력은 항상 동일 해시")
        void deterministic() {
            String hash1 = EncryptionUtil.sha256("teacher@school.ac.kr");
            String hash2 = EncryptionUtil.sha256("teacher@school.ac.kr");

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("해시 길이는 64자 (hex)")
        void hashLength() {
            String hash = EncryptionUtil.sha256("test@example.com");
            assertThat(hash).hasSize(64);
        }

        @Test
        @DisplayName("다른 입력은 다른 해시")
        void differentInputDifferentHash() {
            String hash1 = EncryptionUtil.sha256("a@b.com");
            String hash2 = EncryptionUtil.sha256("c@d.com");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("null 입력 시 null 반환")
        void nullInput() {
            assertThat(EncryptionUtil.sha256(null)).isNull();
        }
    }
}
