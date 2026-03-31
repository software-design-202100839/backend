package com.sscm.common.crypto;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter는 Spring Bean이 아니므로,
 * static 필드를 통해 EncryptionUtil에 접근할 수 있도록 브릿지 역할.
 */
@Component
@RequiredArgsConstructor
public class EncryptionContextHolder {

    private final EncryptionUtil encryptionUtil;

    private static EncryptionUtil INSTANCE;

    @PostConstruct
    void init() {
        INSTANCE = encryptionUtil;
    }

    public static EncryptionUtil get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("EncryptionUtil not initialized — Spring context not ready");
        }
        return INSTANCE;
    }
}
