package org.scriptdojo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.entity.PermissionEntity;
import org.scriptdojo.backend.entity.PermissionRole;
import org.scriptdojo.backend.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;

    // ============================================
    // PERMISSION CHECKING (GUEST)
    // ============================================

    /**
     * Check if a guest can edit a file
     */
    public boolean canGuestEdit(String guestName, Long fileId) {
        log.debug("🔍 Checking guest edit permission: guest='{}', file={}", guestName, fileId);

        Optional<PermissionEntity> permission = permissionRepository
                .findByFileIdAndGuestName(fileId, guestName);

        boolean hasEdit = permission.isPresent() && permission.get().canEdit();

        log.debug("   Result: {}", hasEdit ? "✅ CAN EDIT" : "❌ VIEW ONLY");

        return hasEdit;
    }

    /**
     * Check if a guest has any access (view or edit)
     */
    public boolean hasGuestAccess(String guestName, Long fileId) {
        return permissionRepository.existsByFileIdAndGuestName(fileId, guestName);
    }

    // ============================================
    // PERMISSION CHECKING (AUTHENTICATED USER)
    // ============================================

    /**
     * Check if authenticated user can edit a file
     */
    public boolean canUserEdit(Long userId, Long fileId) {
        log.debug("🔍 Checking user edit permission: userId={}, file={}", userId, fileId);

        Optional<PermissionEntity> permission = permissionRepository
                .findByFileIdAndUserId(fileId, userId);

        boolean hasEdit = permission.isPresent() && permission.get().canEdit();

        log.debug("   Result: {}", hasEdit ? "✅ CAN EDIT" : "❌ VIEW ONLY");

        return hasEdit;
    }

    /**
     * Check if user has any access
     */
    public boolean hasUserAccess(Long userId, Long fileId) {
        return permissionRepository.existsByFileIdAndUserId(fileId, userId);
    }

    // ============================================
    // GRANT PERMISSIONS (GUEST)
    // ============================================

    /**
     * Grant edit permission to a guest
     */
    @Transactional
    public PermissionEntity grantGuestEdit(String guestName, Long fileId, Long grantedBy) {
        log.info("════════════════════════════════════════════════════");
        log.info("🔓 GRANTING EDIT PERMISSION TO GUEST");
        log.info("   Guest: {}", guestName);
        log.info("   File ID: {}", fileId);
        log.info("   Granted By: {}", grantedBy);

        // Check if permission already exists
        Optional<PermissionEntity> existing = permissionRepository
                .findByFileIdAndGuestName(fileId, guestName);

        PermissionEntity permission;

        if (existing.isPresent()) {
            // Upgrade existing permission
            permission = existing.get();
            permission.grantEdit();
            permission.setGrantedBy(grantedBy);
            log.info("   ✅ Upgraded existing permission to EDIT");
        } else {
            // Create new permission
            permission = PermissionEntity.builder()
                    .fileId(fileId)
                    .guestName(guestName)
                    .userId(null)
                    .role(PermissionRole.EDIT)
                    .grantedBy(grantedBy)
                    .build();
            log.info("   ✅ Created new EDIT permission");
        }

        PermissionEntity saved = permissionRepository.save(permission);

        log.info("   Permission ID: {}", saved.getId());
        log.info("════════════════════════════════════════════════════");

        return saved;
    }

    /**
     * Grant view-only permission to a guest
     */
    @Transactional
    public PermissionEntity grantGuestView(String guestName, Long fileId, Long grantedBy) {
        log.info("👁️ Granting VIEW permission to guest '{}' for file {}", guestName, fileId);

        PermissionEntity permission = PermissionEntity.builder()
                .fileId(fileId)
                .guestName(guestName)
                .userId(null)
                .role(PermissionRole.VIEW)
                .grantedBy(grantedBy)
                .build();

        return permissionRepository.save(permission);
    }

    // ============================================
    // GRANT PERMISSIONS (AUTHENTICATED USER)
    // ============================================

    /**
     * Grant edit permission to authenticated user
     */
    @Transactional
    public PermissionEntity grantUserEdit(Long userId, Long fileId, Long grantedBy) {
        log.info("🔓 Granting EDIT permission to user {} for file {}", userId, fileId);

        Optional<PermissionEntity> existing = permissionRepository
                .findByFileIdAndUserId(fileId, userId);

        PermissionEntity permission;

        if (existing.isPresent()) {
            permission = existing.get();
            permission.grantEdit();
            permission.setGrantedBy(grantedBy);
        } else {
            permission = PermissionEntity.builder()
                    .fileId(fileId)
                    .userId(userId)
                    .guestName(null)
                    .role(PermissionRole.EDIT)
                    .grantedBy(grantedBy)
                    .build();
        }

        return permissionRepository.save(permission);
    }

    // ============================================
    // REVOKE PERMISSIONS
    // ============================================

    /**
     * Revoke edit permission from guest (downgrade to view)
     */
    @Transactional
    public void revokeGuestEdit(String guestName, Long fileId) {
        log.info("🔒 Revoking edit permission from guest '{}' for file {}", guestName, fileId);

        Optional<PermissionEntity> permission = permissionRepository
                .findByFileIdAndGuestName(fileId, guestName);

        if (permission.isPresent()) {
            PermissionEntity p = permission.get();
            p.revokeEdit();
            permissionRepository.save(p);
            log.info("   ✅ Edit permission revoked (now view-only)");
        } else {
            log.warn("   ⚠️ No permission found to revoke");
        }
    }

    /**
     * Remove all access from guest
     */
    @Transactional
    public void removeGuestAccess(String guestName, Long fileId) {
        log.info("🚫 Removing all access from guest '{}' for file {}", guestName, fileId);
        permissionRepository.deleteByFileIdAndGuestName(fileId, guestName);
        log.info("   ✅ Access removed");
    }

    // ============================================
    // FILE QUERIES
    // ============================================

    /**
     * Get all permissions for a file
     */
    public List<PermissionEntity> getFilePermissions(Long fileId) {
        return permissionRepository.findByFileId(fileId);
    }

    /**
     * Get all users/guests with edit permission
     */
    public List<PermissionEntity> getEditUsers(Long fileId) {
        return permissionRepository.findByFileIdAndRole(fileId, PermissionRole.EDIT);
    }

    /**
     * Cleanup all permissions when file is deleted
     */
    @Transactional
    public void deleteFilePermissions(Long fileId) {
        log.info("🗑️ Deleting all permissions for file {}", fileId);
        permissionRepository.deleteByFileId(fileId);
    }
}