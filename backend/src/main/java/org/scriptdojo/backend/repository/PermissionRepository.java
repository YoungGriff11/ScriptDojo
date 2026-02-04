package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.PermissionEntity;
import org.scriptdojo.backend.entity.PermissionRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    // ============================================
    // GUEST PERMISSIONS
    // ============================================

    /**
     * Find permission for a specific guest and file
     */
    Optional<PermissionEntity> findByFileIdAndGuestName(Long fileId, String guestName);

    /**
     * Check if guest has any permission for file
     */
    boolean existsByFileIdAndGuestName(Long fileId, String guestName);

    /**
     * Delete guest permission
     */
    void deleteByFileIdAndGuestName(Long fileId, String guestName);

    // ============================================
    // AUTHENTICATED USER PERMISSIONS
    // ============================================

    /**
     * Find permission for authenticated user
     */
    Optional<PermissionEntity> findByFileIdAndUserId(Long fileId, Long userId);

    /**
     * Check if user has permission for file
     */
    boolean existsByFileIdAndUserId(Long fileId, Long userId);

    /**
     * Delete user permission
     */
    void deleteByFileIdAndUserId(Long fileId, Long userId);

    // ============================================
    // FILE-LEVEL QUERIES
    // ============================================

    /**
     * Get all permissions for a file (users + guests)
     */
    List<PermissionEntity> findByFileId(Long fileId);

    /**
     * Get all permissions with specific role for a file
     */
    List<PermissionEntity> findByFileIdAndRole(Long fileId, PermissionRole role);

    /**
     * Delete all permissions for a file (cleanup when file deleted)
     */
    void deleteByFileId(Long fileId);
}