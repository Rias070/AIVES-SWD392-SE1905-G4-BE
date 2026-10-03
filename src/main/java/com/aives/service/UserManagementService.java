package com.aives.service;

import com.aives.dto.request.RoleAssignRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.request.UserUpdateRequest;
import com.aives.dto.response.UserResponse;
import com.aives.entity.Role;
import com.aives.entity.User;
import com.aives.entity.UserRole;
import com.aives.enums.ErrorCode;
import com.aives.enums.RoleEnum;
import com.aives.enums.UserStatus;
import com.aives.exception.AppException;
import com.aives.repository.RoleRepository;
import com.aives.repository.UserRepository;
import com.aives.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Admin creating new user: email={}, roleName={}", request.getEmail(), request.getRoleName());

        // Validate role name is a real enum value
        RoleEnum roleEnum = validateRoleName(request.getRoleName());

        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED, "Email already in use: " + request.getEmail());
        }

        // Check userCode uniqueness (if provided)
        if (request.getUserCode() != null && !request.getUserCode().isBlank()
                && userRepository.existsByUserCode(request.getUserCode())) {
            throw new AppException(ErrorCode.USER_EXISTED, "User code already in use: " + request.getUserCode());
        }

        // Resolve role entity
        Role role = roleRepository.findByName(roleEnum.name())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND,
                        "Role not found in DB: " + roleEnum.name()));

        // Build user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .userCode(request.getUserCode())
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();

        // Attach single role
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .build();
        user.getUserRoles().add(userRole);

        User saved = userRepository.save(user);
        log.info("User created successfully: uuid={}, email={}", saved.getUuid(), saved.getEmail());
        return mapToUserResponse(saved);
    }

    public UserResponse getUser(UUID uuid) {
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "User not found with uuid: " + uuid));
        return mapToUserResponse(user);
    }

    public Page<UserResponse> listUsers(String keyword, String roleName, UserStatus status, Pageable pageable) {
        // Validate roleName if provided
        if (roleName != null && !roleName.isBlank()) {
            try {
                RoleEnum.valueOf(roleName);
            } catch (IllegalArgumentException ex) {
                throw new AppException(ErrorCode.ROLE_NOT_FOUND,
                        "Invalid role name: " + roleName + ". Allowed: " +
                                Arrays.stream(RoleEnum.values()).map(Enum::name).collect(Collectors.joining(", ")));
            }
        }

        Page<User> page = userRepository.searchUsers(
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                status,
                (roleName == null || roleName.isBlank()) ? null : roleName.trim(),
                pageable);

        return page.map(this::mapToUserResponse);
    }

    @Transactional
    public UserResponse updateUser(UUID uuid, UserUpdateRequest request) {
        log.info("Admin updating user uuid={}", uuid);
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "User not found with uuid: " + uuid));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        // Role replacement (only 1 role per user)
        if (request.getRoleName() != null && !request.getRoleName().isBlank()) {
            RoleEnum roleEnum = validateRoleName(request.getRoleName());
            replaceUserRole(user, roleEnum);
        }

        // Status update with last-admin protection
        if (request.getStatus() != null && request.getStatus() != user.getStatus()) {
            ensureNotLockingLastActiveAdmin(user, request.getStatus());
            user.setStatus(request.getStatus());
        }

        User saved = userRepository.save(user);
        log.info("User updated successfully: uuid={}", saved.getUuid());
        return mapToUserResponse(saved);
    }

    @Transactional
    public UserResponse changeRole(UUID uuid, RoleAssignRequest request) {
        log.info("Admin changing role for user uuid={} to {}", uuid, request.getRoleName());
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "User not found with uuid: " + uuid));
        RoleEnum roleEnum = validateRoleName(request.getRoleName());
        replaceUserRole(user, roleEnum);
        User saved = userRepository.save(user);
        return mapToUserResponse(saved);
    }

    @Transactional
    public UserResponse changeStatus(UUID uuid, UserStatus newStatus) {
        log.info("Admin changing status for user uuid={} to {}", uuid, newStatus);
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "User not found with uuid: " + uuid));
        if (newStatus == user.getStatus()) {
            return mapToUserResponse(user);
        }
        ensureNotLockingLastActiveAdmin(user, newStatus);
        user.setStatus(newStatus);
        User saved = userRepository.save(user);
        return mapToUserResponse(saved);
    }

    @Transactional
    public UserResponse softDelete(UUID uuid) {
        log.info("Admin soft-deleting user uuid={}", uuid);
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "User not found with uuid: " + uuid));
        ensureNotLockingLastActiveAdmin(user, UserStatus.INACTIVE);
        user.setStatus(UserStatus.INACTIVE);
        User saved = userRepository.save(user);
        return mapToUserResponse(saved);
    }

    // ================== Helpers ==================

    private RoleEnum validateRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND, "Role name must not be blank");
        }
        try {
            return RoleEnum.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND,
                    "Invalid role name: " + roleName + ". Allowed: " +
                            Arrays.stream(RoleEnum.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    private void replaceUserRole(User user, RoleEnum roleEnum) {
        // If user is currently the only active admin and we're moving them off ADMIN,
        // block it to protect the system.
        boolean isCurrentlyAdmin = user.getUserRoles().stream()
                .anyMatch(ur -> RoleEnum.ADMIN.name().equals(ur.getRole().getName()));
        if (isCurrentlyAdmin && roleEnum != RoleEnum.ADMIN) {
            long activeAdminCount = userRepository.countActiveByRoleName(RoleEnum.ADMIN.name());
            if (activeAdminCount <= 1 && user.getStatus() == UserStatus.ACTIVE) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Cannot remove the last active ADMIN's role. Promote another admin first.");
            }
        }

        Role role = roleRepository.findByName(roleEnum.name())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND,
                        "Role not found in DB: " + roleEnum.name()));

        // Delete existing UserRole rows
        if (!user.getUserRoles().isEmpty()) {
            userRoleRepository.deleteAll(user.getUserRoles());
            user.getUserRoles().clear();
        }

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .build();
        user.getUserRoles().add(userRole);
    }

    private void ensureNotLockingLastActiveAdmin(User user, UserStatus newStatus) {
        if (newStatus == UserStatus.ACTIVE) {
            return;
        }
        boolean isCurrentlyAdmin = user.getUserRoles().stream()
                .anyMatch(ur -> RoleEnum.ADMIN.name().equals(ur.getRole().getName()));
        if (!isCurrentlyAdmin) {
            return;
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            return; // already not active, no risk
        }
        long activeAdminCount = userRepository.countActiveByRoleName(RoleEnum.ADMIN.name());
        if (activeAdminCount <= 1) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Cannot lock or deactivate the last active ADMIN account.");
        }
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse.RoleInfo roleInfo = null;
        if (user.getUserRoles() != null && !user.getUserRoles().isEmpty()) {
            // Get the first role (user has at most 1 in this design)
            UserRole ur = user.getUserRoles().iterator().next();
            Role r = ur.getRole();
            roleInfo = UserResponse.RoleInfo.builder()
                    .uuid(r.getUuid())
                    .name(r.getName())
                    .description(r.getDescription())
                    .build();
        }
        return UserResponse.builder()
                .uuid(user.getUuid())
                .email(user.getEmail())
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .role(roleInfo)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}