package com.aives.dto.request;

import com.aives.enums.UserStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    private String phoneNumber;

    private String avatarUrl;

    /**
     * Optional. If present, will replace the user's current role.
     * Allowed values: ADMIN, LECTURER, STUDENT.
     */
    private String roleName;

    /**
     * Optional. If present, will update the user's status.
     */
    private UserStatus status;
}