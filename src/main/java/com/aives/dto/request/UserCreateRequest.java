package com.aives.dto.request;

import com.aives.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL")
    private String email;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, message = "INVALID_PASSWORD")
    private String password;

    @NotBlank(message = "FULLNAME_REQUIRED")
    @Size(max = 100, message = "FULLNAME_REQUIRED")
    private String fullName;

    private String phoneNumber;

    private String userCode;

    @NotBlank(message = "Role name is required (ADMIN, LECTURER, STUDENT)")
    private String roleName;

    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
}