package com.sscm.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptionConfig {

    @Bean
    public EncryptionUtil encryptionUtil(
            @Value("${encryption.key}") String encryptionKey) {
        return new EncryptionUtil(encryptionKey);
    }
}
