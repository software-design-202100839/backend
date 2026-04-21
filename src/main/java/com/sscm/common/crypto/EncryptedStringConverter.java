package com.sscm.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter: String 필드를 AES-256-GCM으로 투명하게 암/복호화.
 * 엔티티 필드에 @Convert(converter = EncryptedStringConverter.class) 선언하여 사용.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return EncryptionContextHolder.get().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return EncryptionContextHolder.get().decrypt(dbData);
    }
}
