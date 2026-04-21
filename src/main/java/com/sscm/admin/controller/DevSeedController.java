package com.sscm.admin.controller;

import com.sscm.auth.entity.Role;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.crypto.EncryptionUtil;
import com.sscm.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * dev 환경 전용 — 초기 ADMIN 계정 생성.
 * POST /api/v1/dev/seed/admin → admin@sscm.dev
 * 비밀번호는 DEV_SEED_ADMIN_PASSWORD 환경변수로 주입.
 * 프로덕션에서는 활성화되지 않음 (@Profile("dev")).
 */
@Tag(name = "Dev", description = "개발 환경 전용 (프로덕션 비활성화)")
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@Profile("dev")
public class DevSeedController {

    private static final String ADMIN_EMAIL = "admin@sscm.dev";
    private static final String ADMIN_PHONE = "010-0000-0000";
    private static final String ADMIN_NAME  = "관리자";

    @Value("${dev.seed.admin.password}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "[DEV] ADMIN 계정 생성 — admin@sscm.dev")
    @PostMapping("/seed/admin")
    public ResponseEntity<ApiResponse<SeedResult>> seedAdmin() {
        String phoneHash = EncryptionUtil.sha256(ADMIN_PHONE);
        String emailHash = EncryptionUtil.sha256(ADMIN_EMAIL);

        if (userRepository.existsByEmailHash(emailHash)) {
            return ResponseEntity.ok(ApiResponse.success(new SeedResult("이미 ADMIN 계정이 존재합니다", ADMIN_EMAIL)));
        }

        User admin = User.builder()
                .name(ADMIN_NAME)
                .email(ADMIN_EMAIL)
                .emailHash(emailHash)
                .phone(ADMIN_PHONE)
                .phoneHash(phoneHash)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .isActive(true)
                .isActivated(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(admin);

        log.info("[DEV SEED] ADMIN 계정 생성 완료: {}", ADMIN_EMAIL);
        return ResponseEntity.ok(ApiResponse.success(new SeedResult("ADMIN 생성 완료", ADMIN_EMAIL)));
    }

    public record SeedResult(String message, String credential) {}
}
