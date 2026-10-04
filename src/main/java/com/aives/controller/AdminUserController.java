package com.aives.controller;

import com.aives.dto.request.RoleAssignRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.request.UserStatusUpdateRequest;
import com.aives.dto.request.UserUpdateRequest;
import com.aives.dto.response.UserResponse;
import com.aives.enums.UserStatus;
import com.aives.response.ApiResponse;
import com.aives.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - User Management", description = "CRUD endpoints for admin to manage user accounts and assign roles")
public class AdminUserController {

    private final UserManagementService userManagementService;

    @PostMapping
    @Operation(summary = "Create a new user account",
            description = "Admin creates a new user with a single role (ADMIN, LECTURER, or STUDENT).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or duplicate email/userCode")
    })
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("POST /api/v1/admin/users - createUser: email={}", request.getEmail());
        UserResponse created = userManagementService.createUser(request);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .code(1000)
                .message("User created successfully")
                .result(created)
                .build());
    }

    @GetMapping
    @Operation(summary = "List users with filters",
            description = "Paginated list of users. Supports keyword (email/fullName/userCode), roleName and status filters.")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @Parameter(description = "Search keyword (matches email, fullName, userCode)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Filter by role name (ADMIN / LECTURER / STUDENT)")
            @RequestParam(required = false) String roleName,
            @Parameter(description = "Filter by user status (ACTIVE / LOCKED / INACTIVE)")
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        log.info("GET /api/v1/admin/users - keyword={}, roleName={}, status={}", keyword, roleName, status);
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Page<UserResponse> result = userManagementService.listUsers(keyword, roleName, status, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<UserResponse>>builder()
                .code(1000)
                .message("Users fetched successfully")
                .result(result)
                .build());
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get user detail by uuid")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID uuid) {
        log.info("GET /api/v1/admin/users/{}", uuid);
        UserResponse user = userManagementService.getUser(uuid);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .code(1000)
                .message("User fetched successfully")
                .result(user)
                .build());
    }

    @PutMapping("/{uuid}")
    @Operation(summary = "Update user information and optionally replace role",
            description = "Update fullName, phoneNumber, avatarUrl. Optionally replace role by providing roleName.")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID uuid,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("PUT /api/v1/admin/users/{}", uuid);
        UserResponse updated = userManagementService.updateUser(uuid, request);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .code(1000)
                .message("User updated successfully")
                .result(updated)
                .build());
    }

    @PutMapping("/{uuid}/role")
    @Operation(summary = "Replace user role (quick endpoint)",
            description = "Convenience endpoint to assign a new role to the user (1 role only).")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            @PathVariable UUID uuid,
            @Valid @RequestBody RoleAssignRequest request) {
        log.info("PUT /api/v1/admin/users/{}/role -> {}", uuid, request.getRoleName());
        UserResponse updated = userManagementService.changeRole(uuid, request);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .code(1000)
                .message("Role assigned successfully")
                .result(updated)
                .build());
    }

    @PutMapping("/{uuid}/status")
    @Operation(summary = "Change user account status",
            description = "Lock, unlock, activate, or deactivate a user account. Will refuse if it would lock the last active admin.")
    public ResponseEntity<ApiResponse<UserResponse>> changeStatus(
            @PathVariable UUID uuid,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        log.info("PUT /api/v1/admin/users/{}/status -> {}", uuid, request.getStatus());
        UserResponse updated = userManagementService.changeStatus(uuid, request.getStatus());
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .code(1000)
                .message("User status updated successfully")
                .result(updated)
                .build());
    }

    @DeleteMapping("/{uuid}")
    @Operation(summary = "Soft-delete user (set status = INACTIVE)",
            description = "Marks the user as INACTIVE. Does not remove from DB to preserve payment / exam history.")
    public ResponseEntity<ApiResponse<UserResponse>> softDelete(@PathVariable UUID uuid) {
        log.info("DELETE /api/v1/admin/users/{}", uuid);
        UserResponse updated = userManagementService.softDelete(uuid);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .code(1000)
                .message("User deactivated successfully")
                .result(updated)
                .build());
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction dir = (parts.length > 1 && parts[1].equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }
}