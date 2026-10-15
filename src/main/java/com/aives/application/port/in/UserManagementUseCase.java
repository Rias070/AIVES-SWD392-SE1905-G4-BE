package com.aives.application.port.in;

import com.aives.dto.request.RoleAssignRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.request.UserUpdateRequest;
import com.aives.dto.response.UserResponse;
import com.aives.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserManagementUseCase {
    UserResponse createUser(UserCreateRequest request);
    UserResponse getUser(UUID uuid);
    Page<UserResponse> listUsers(String keyword, String roleName, UserStatus status, Pageable pageable);
    UserResponse updateUser(UUID uuid, UserUpdateRequest request);
    UserResponse changeRole(UUID uuid, RoleAssignRequest request);
    UserResponse changeStatus(UUID uuid, UserStatus newStatus);
    UserResponse softDelete(UUID uuid);
}
