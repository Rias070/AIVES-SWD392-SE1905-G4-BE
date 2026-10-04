package com.aives.dto.response;

import com.aives.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID uuid;
    private String email;
    private String userCode;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private UserStatus status;
    private RoleInfo role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private UUID uuid;
        private String name;
        private String description;
    }
}