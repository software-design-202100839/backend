package com.sscm.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilTest {

    // "sscm-dev-encryption-key-32-bytes" 의 Base64
    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("sscm-dev-encryption-key-32-bytes".getBytes());

    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil(TEST_KEY);
    }

    @Nested
    @DisplayName("AES-256-GCM 암호화/복호화")
    class AesGcm {

        @Test
        @DisplayName("암호화 후 복호화하면 원본과 동일하다")
        void encryptDecryptRoundTrip() {
            String plaintext = "teacher@school.ac.kr";
            String encrypted = encryptionUtil.encrypt(plaintext);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("같은 평문을 두 번 암호화하면 다른 암호문이 나온다 (랜덤 IV)")
        void differentCiphertextForSamePlaintext() {
            String plaintext = "student@school.ac.kr";
            String encrypted1 = encryptionUtil.encrypt(plaintext);
            String encrypted2 = encryptionUtil.encrypt(plaintext);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }

        @Test
        @DisplayName("null 입력 시 null 반환")
        void nullInput() {
            assertThat(encryptionUtil.encrypt(null)).isNull();
            assertThat(encryptionUtil.decrypt(null)).isNull();
        }

        @Test
        @DisplayName("빈 문자열도 정상 암/복호화")
        void emptyString() {
            String encrypted = encryptionUtil.encrypt("");
            assertThat(encryptionUtil.decrypt(encrypted)).isEmpty();
        }

        @Test
        @DisplayName("한글 텍스트 암/복호화")
        void koreanText() {
            String plaintext = "학생 상담 내용: 진로 고민이 있습니다.";
            String encrypted = encryptionUtil.encrypt(plaintext);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("긴 텍스트 (상담 내용) 암/복호화")
        void longText() {
            String plaintext = "가".repeat(5000);
            String encrypted = encryptionUtil.encrypt(plaintext);
            String decrypted = encryptionUtil.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("암호문이 원본과 다르다 (실제로 암호화됨)")
        void ciphertextDiffersFromPlaintext() {
            String plaintext = "010-1234-5678";
            String encrypted = encryptionUtil.encrypt(plaintext);

            assertThat(encrypted).isNotEqualTo(plaintext);
            assertThat(encrypted).isBase64();
        }
    }

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

    @Nested
    @DisplayName("키 검증")
    class KeyValidation {

        @Test
        @DisplayName("32바이트가 아닌 키는 예외 발생")
        void invalidKeyLength() {
            String shortKey = Base64.getEncoder().encodeToString("short-key".getBytes());
            assertThatThrownBy(() -> new EncryptionUtil(shortKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }
    }
}
