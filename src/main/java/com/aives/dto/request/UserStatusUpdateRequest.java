package com.aives.dto.request;

import com.aives.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusUpdateRequest {

    @NotNull(message = "Status is required (ACTIVE, LOCKED, INACTIVE)")
    private UserStatus status;
}