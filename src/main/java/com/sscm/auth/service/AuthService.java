package com.sscm.auth.service;

import com.sscm.auth.dto.*;
import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.crypto.EncryptionUtil;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmailHash(EncryptionUtil.sha256(request.getEmail()))) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Role role = Role.valueOf(request.getRole());
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .role(role)
                .build();
        userRepository.save(user);

        createRoleDetail(user, role, request.getRoleDetail());

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailHash(EncryptionUtil.sha256(request.getEmail()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        tokenBlacklistService.saveRefreshToken(
                user.getId(),
                refreshToken,
                jwtTokenProvider.getRemainingExpiration(refreshToken)
        );

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(UserResponse.from(user))
                .build();
    }

    public TokenResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String storedToken = tokenBlacklistService.getRefreshToken(userId);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        tokenBlacklistService.saveRefreshToken(
                user.getId(),
                newRefreshToken,
                jwtTokenProvider.getRemainingExpiration(newRefreshToken)
        );

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .build();
    }

    public void logout(String accessToken, Long userId) {
        long remaining = jwtTokenProvider.getRemainingExpiration(accessToken);
        if (remaining > 0) {
            tokenBlacklistService.blacklistAccessToken(accessToken, remaining);
        }
        tokenBlacklistService.deleteRefreshToken(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return UserResponse.from(user);
    }

    private void createRoleDetail(User user, Role role, Map<String, Object> detail) {
        if (detail == null) {
            detail = Map.of();
        }

        switch (role) {
            case TEACHER -> {
                Teacher teacher = Teacher.builder()
                        .user(user)
                        .department((String) detail.get("department"))
                        .homeroomGrade(toInteger(detail.get("homeroomGrade")))
                        .homeroomClass(toInteger(detail.get("homeroomClass")))
                        .build();
                teacherRepository.save(teacher);
            }
            case STUDENT -> {
                Student student = Student.builder()
                        .user(user)
                        .grade((Integer) detail.get("grade"))
                        .classNum((Integer) detail.get("classNum"))
                        .studentNum((Integer) detail.get("studentNum"))
                        .admissionYear(detail.get("admissionYear") != null
                                ? (Integer) detail.get("admissionYear")
                                : java.time.Year.now().getValue())
                        .build();
                studentRepository.save(student);
            }
            case PARENT -> {
                Parent parent = Parent.builder()
                        .user(user)
                        .build();
                parentRepository.save(parent);
            }
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        return Integer.parseInt(value.toString());
    }
}
