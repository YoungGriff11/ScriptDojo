package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.PermissionEntity;
import org.scriptdojo.backend.entity.PermissionRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PermissionEntity} persistence operations.
 * Provides query methods for the two distinct participant types in ScriptDojo —
 * unauthenticated guests (identified by display name) and authenticated users
 * (identified by database ID) — as well as file-level bulk operations.
 * All query methods are generated at runtime by Spring Data from their
 * method name — no JPQL or native SQL is required.
 */
@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    // ─ Guest permissions
    // Guests are unauthenticated and identified solely by their randomly
    // assigned display name (e.g. "Guest1234") for the duration of a session.

    /**
     * Returns the permission record for a specific guest and file, if one exists.
     * Used by {@link org.scriptdojo.backend.service.PermissionService} to check
     * and update a guest's current access level.
     */
    Optional<PermissionEntity> findByFileIdAndGuestName(Long fileId, String guestName);

    /**
     * Returns true if any permission record exists for the given guest and file.
     * Used as a lightweight existence check before creating a new permission entry.
     */
    boolean existsByFileIdAndGuestName(Long fileId, String guestName);

    /**
     * Deletes the permission record for the given guest and file.
     * Called when the host revokes a guest's access entirely, as opposed to
     * downgrading it to VIEW via {@link org.scriptdojo.backend.entity.PermissionEntity#revokeEdit()}.
     */
    void deleteByFileIdAndGuestName(Long fileId, String guestName);

    // ─ Authenticated user permissions
    // Authenticated users are identified by their database user ID.

    /**
     * Returns the permission record for a specific authenticated user and file,
     * if one exists.
     */
    Optional<PermissionEntity> findByFileIdAndUserId(Long fileId, Long userId);

    /**
     * Returns true if any permission record exists for the given user and file.
     * Used as a lightweight existence check before creating a new permission entry.
     */
    boolean existsByFileIdAndUserId(Long fileId, Long userId);

    /**
     * Deletes the permission record for the given authenticated user and file.
     */
    void deleteByFileIdAndUserId(Long fileId, Long userId);

    // ─ File-level queries

    /**
     * Returns all permission records associated with a file, covering both
     * guest and authenticated user entries.
     * Used by {@link org.scriptdojo.backend.controller.PermissionController}
     * to populate the host's permission management panel.
     */
    List<PermissionEntity> findByFileId(Long fileId);

    /**
     * Returns all permission records for a file that match a specific role.
     * Allows filtering to e.g. only EDIT-level participants without loading
     * all permissions and filtering in application code.
     */
    List<PermissionEntity> findByFileIdAndRole(Long fileId, PermissionRole role);

    /**
     * Deletes all permission records associated with a file.
     * Called during file deletion to ensure no orphaned permission records
     * remain in the database after the owning file is removed.
     */
    void deleteByFileId(Long fileId);
}