package com.sscm.auth.dto;

import com.sscm.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private String name;
    private String role;
    private Long roleEntityId;
    private List<ChildInfo> children;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ChildInfo {
        private Long id;
        private String name;
    }

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

    public static UserResponse from(User user, Long roleEntityId, List<ChildInfo> children) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .roleEntityId(roleEntityId)
                .children(children)
                .build();
    }
}
