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

/**
 * Service responsible for managing guest and authenticated user permissions
 * within collaborative editing rooms in ScriptDojo.
 * Provides permission checking, granting, revoking, and cleanup operations
 * used by {@link org.scriptdojo.backend.controller.PermissionController} and
 * {@link FileService}.
 * Guest permissions are keyed by display name; authenticated user permissions
 * are keyed by database user ID. Grant operations are upserts — if a permission
 * record already exists for the participant it is upgraded rather than duplicated.
 */
@Service
@RequiredArgsConstructor // Lombok: generates constructor injecting permissionRepository
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;

    // ─ Guest permission checks

    /**
     * Returns true if the given guest has EDIT-level access to the given file.
     * Returns false if no permission record exists or if the record is VIEW-only.
     * @param guestName the display name of the guest to check
     * @param fileId    the ID of the file to check against
     * @return true if the guest has edit permission, false otherwise
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
     * Returns true if the given guest has any permission record for the file
     * (VIEW or EDIT). Used to determine whether a guest has been explicitly
     * granted access, regardless of level.
     * @param guestName the display name of the guest to check
     * @param fileId    the ID of the file to check against
     * @return true if a permission record exists for this guest and file
     */
    public boolean hasGuestAccess(String guestName, Long fileId) {
        return permissionRepository.existsByFileIdAndGuestName(fileId, guestName);
    }

    // ─ Authenticated user permission checks

    /**
     * Returns true if the given authenticated user has EDIT-level access to the file.
     * Returns false if no permission record exists or if the record is VIEW-only.
     * @param userId the database ID of the user to check
     * @param fileId the ID of the file to check against
     * @return true if the user has edit permission, false otherwise
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
     * Returns true if the given authenticated user has any permission record
     * for the file (VIEW or EDIT).
     * @param userId the database ID of the user to check
     * @param fileId the ID of the file to check against
     * @return true if a permission record exists for this user and file
     */
    public boolean hasUserAccess(Long userId, Long fileId) {
        return permissionRepository.existsByFileIdAndUserId(fileId, userId);
    }

    // ─ Guest permission grants

    /**
     * Grants EDIT-level permission to a guest for the given file.
     * If a permission record already exists for the guest it is upgraded to EDIT
     * via {@link PermissionEntity#grantEdit()}; otherwise a new record is created.
     * The grantedBy field is updated in both cases to reflect the current host.
     * @param guestName the display name of the guest to grant edit access to
     * @param fileId    the ID of the file the guest is being granted access to
     * @param grantedBy the user ID of the host granting the permission
     * @return the persisted {@link PermissionEntity} with EDIT role
     */
    @Transactional
    public PermissionEntity grantGuestEdit(String guestName, Long fileId, Long grantedBy) {
        log.info("════════════════════════════════════════════════════");
        log.info("🔓 GRANTING EDIT PERMISSION TO GUEST");
        log.info("   Guest: {}", guestName);
        log.info("   File ID: {}", fileId);
        log.info("   Granted By: {}", grantedBy);

        Optional<PermissionEntity> existing = permissionRepository
                .findByFileIdAndGuestName(fileId, guestName);

        PermissionEntity permission;

        if (existing.isPresent()) {
            // Upgrade the existing record rather than creating a duplicate
            permission = existing.get();
            permission.grantEdit();
            permission.setGrantedBy(grantedBy);
            log.info("   ✅ Upgraded existing permission to EDIT");
        } else {
            // No existing record — create a fresh EDIT permission for the guest
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
     * Grants VIEW-only permission to a guest for the given file.
     * Always creates a new permission record — does not check for an existing one.
     * Intended for use when initially registering a guest's presence in a room.
     * @param guestName the display name of the guest to grant view access to
     * @param fileId    the ID of the file the guest is being granted access to
     * @param grantedBy the user ID of the host granting the permission
     * @return the persisted {@link PermissionEntity} with VIEW role
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

    // ─ Authenticated user permission grants

    /**
     * Grants EDIT-level permission to an authenticated user for the given file.
     * If a permission record already exists for the user it is upgraded to EDIT;
     * otherwise a new record is created.
     * @param userId    the database ID of the user to grant edit access to
     * @param fileId    the ID of the file the user is being granted access to
     * @param grantedBy the user ID of the host granting the permission
     * @return the persisted {@link PermissionEntity} with EDIT role
     */
    @Transactional
    public PermissionEntity grantUserEdit(Long userId, Long fileId, Long grantedBy) {
        log.info("🔓 Granting EDIT permission to user {} for file {}", userId, fileId);

        Optional<PermissionEntity> existing = permissionRepository
                .findByFileIdAndUserId(fileId, userId);

        PermissionEntity permission;

        if (existing.isPresent()) {
            // Upgrade the existing record rather than creating a duplicate
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

    // ─ Permission revocation

    /**
     * Downgrades a guest's permission from EDIT to VIEW for the given file.
     * The permission record is retained — only the role is downgraded via
     * {@link PermissionEntity#revokeEdit()}. Logs a warning if no permission
     * record exists for the guest.
     * @param guestName the display name of the guest whose edit access should be revoked
     * @param fileId    the ID of the file to revoke access on
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
     * Removes all access for a guest by deleting their permission record entirely.
     * More destructive than {@link #revokeGuestEdit} — the guest will have no
     * permission record and will need to be re-granted access to rejoin.
     * @param guestName the display name of the guest whose access should be removed
     * @param fileId    the ID of the file to remove access from
     */
    @Transactional
    public void removeGuestAccess(String guestName, Long fileId) {
        log.info("🚫 Removing all access from guest '{}' for file {}", guestName, fileId);
        permissionRepository.deleteByFileIdAndGuestName(fileId, guestName);
        log.info("   ✅ Access removed");
    }

    // ─ File-level queries

    /**
     * Returns all permission records for the given file, covering both guests
     * and authenticated users.
     * Used by {@link org.scriptdojo.backend.controller.PermissionController}
     * to populate the host's permission management panel.
     * @param fileId the ID of the file to query
     * @return a list of all {@link PermissionEntity} records for the file
     */
    public List<PermissionEntity> getFilePermissions(Long fileId) {
        return permissionRepository.findByFileId(fileId);
    }

    /**
     * Returns all permission records for the given file that have EDIT-level access.
     * @param fileId the ID of the file to query
     * @return a list of {@link PermissionEntity} records with EDIT role
     */
    public List<PermissionEntity> getEditUsers(Long fileId) {
        return permissionRepository.findByFileIdAndRole(fileId, PermissionRole.EDIT);
    }

    /**
     * Deletes all permission records associated with the given file.
     * Called by {@link FileService#deleteFile} before the file entity is removed
     * to ensure no orphaned permission records remain in the database.
     * @param fileId the ID of the file whose permissions should be deleted
     */
    @Transactional
    public void deleteFilePermissions(Long fileId) {
        log.info("🗑️ Deleting all permissions for file {}", fileId);
        permissionRepository.deleteByFileId(fileId);
    }
}