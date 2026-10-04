package com.aives.repository;

import com.aives.entity.User;
import com.aives.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUserCode(String userCode);
    boolean existsByEmail(String email);
    boolean existsByUserCode(String userCode);

    /**
     * Search users with optional filters: keyword (email/fullName/userCode), status, and roleName.
     * roleName = null means no role filter.
     */
    @Query("SELECT DISTINCT u FROM User u " +
            "LEFT JOIN u.userRoles ur LEFT JOIN ur.role r " +
            "WHERE (:keyword IS NULL OR :keyword = '' " +
            "       OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "       OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "       OR LOWER(u.userCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:roleName IS NULL OR :roleName = '' OR r.name = :roleName)")
    Page<User> searchUsers(@Param("keyword") String keyword,
                           @Param("status") UserStatus status,
                           @Param("roleName") String roleName,
                           Pageable pageable);

    /**
     * Count users currently holding a given role name AND with ACTIVE status.
     * Used to prevent locking/disabling the last active admin.
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u " +
            "JOIN u.userRoles ur JOIN ur.role r " +
            "WHERE r.name = :roleName AND u.status = com.aives.enums.UserStatus.ACTIVE")
    long countActiveByRoleName(@Param("roleName") String roleName);
}