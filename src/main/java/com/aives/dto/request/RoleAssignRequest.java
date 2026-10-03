package com.aives.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignRequest {

    @NotBlank(message = "Role name is required (ADMIN, LECTURER, STUDENT)")
    private String roleName;
}