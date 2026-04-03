package com.sscm.auth.dto;

import com.sscm.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private String name;
    private String role;
    private Long roleEntityId;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    public static UserResponse from(User user, Long roleEntityId) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .roleEntityId(roleEntityId)
                .build();
    }
}
