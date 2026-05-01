package org.scriptdojo.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a permission record that controls what a participant
 * can do within a collaborative editing session in ScriptDojo.
 *
 * A permission record is created for each participant (host-granted guest or
 * authenticated user) and tracks whether they have VIEW or EDIT access to a
 * specific file. Permissions are scoped to a file, not a room, so they persist
 * across sessions as long as the file exists.
 *
 * Identity model — exactly one of the following must be set per record:
 *   - userId    → the participant is an authenticated user (identified by DB ID)
 *   - guestName → the participant is an unauthenticated guest (identified by
 *                 their randomly assigned display name, e.g. "Guest1234")
 *
 * This mutual exclusivity is enforced at the database level by the
 * {@link #validatePermission()} JPA lifecycle callback, which fires on both
 * INSERT and UPDATE and throws {@link IllegalStateException} if the constraint
 * is violated.
 *
 * Permission levels (defined in {@link PermissionRole}):
 *   - VIEW — read-only access; the editor is displayed but not editable
 *   - EDIT — full read/write access; the guest can type in the editor
 *
 * Lombok annotations used:
 * - @Getter / @Setter  — generates accessors for all fields
 * - @NoArgsConstructor — required by JPA
 * - @AllArgsConstructor / @Builder — support programmatic construction in service code
 *
 * Maps to the "permission" table in the database.
 */
@Entity
@Table(name = "permission")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermissionEntity {

    /**
     * Primary key, auto-incremented by the database.
     * Never set manually — assigned by the persistence provider on first save.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The ID of the file this permission applies to.
     * Stored as a plain foreign key column rather than a @ManyToOne relationship
     * to keep the entity lightweight — the file is never navigated to from here.
     * Must not be null.
     */
    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /**
     * The database ID of the authenticated user this permission belongs to.
     * Null for guest permissions — exactly one of userId or guestName must be set.
     * See {@link #validatePermission()} for enforcement.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * The randomly assigned display name of the unauthenticated guest this
     * permission belongs to (e.g. "Guest1234", "Guest7382").
     * Null for authenticated user permissions — exactly one of userId or
     * guestName must be set. Length capped at 100 characters.
     * See {@link #validatePermission()} for enforcement.
     */
    @Column(name = "guest_name", length = 100)
    private String guestName;

    /**
     * The access level granted to this participant.
     * Persisted as a string (e.g. "VIEW", "EDIT") rather than an ordinal so
     * that reordering the enum values never silently corrupts existing records.
     * Defaults to VIEW — all participants start with read-only access until
     * the host explicitly grants edit permission.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PermissionRole role = PermissionRole.VIEW;

    /**
     * The timestamp at which this permission was most recently granted or modified.
     * Initialised to the current time on construction; refreshed by
     * {@link #grantEdit()} and {@link #revokeEdit()} when the role changes.
     * Not managed by a @PreUpdate callback — updated only on explicit role changes.
     */
    @Column(name = "granted_at")
    private LocalDateTime grantedAt = LocalDateTime.now();

    /**
     * The user ID of the host who granted this permission.
     * Used for auditing — allows the system to identify which host authorised
     * a particular guest's access to a file.
     */
    @Column(name = "granted_by")
    private Long grantedBy;

    // ── Derived identity helpers ──────────────────────────────────────────────

    /**
     * Returns a human-readable identifier for the permission holder.
     *
     * Resolution order:
     *   1. guestName if set (e.g. "Guest1234")
     *   2. "User_{userId}" if userId is set (e.g. "User_42")
     *   3. "Unknown" if neither is set (should not occur after validation)
     *
     * Used by {@link org.scriptdojo.backend.controller.PermissionController}
     * when mapping entities to DTOs for API responses.
     *
     * @return the display identifier for this permission holder
     */
    public String getIdentifier() {
        if (guestName != null) {
            return guestName;
        }
        return userId != null ? "User_" + userId : "Unknown";
    }

    // ── Role predicate helpers ────────────────────────────────────────────────

    /**
     * Returns true if this is a guest permission (guestName set, userId null).
     */
    public boolean isGuest() {
        return guestName != null && userId == null;
    }

    /**
     * Returns true if this is an authenticated user permission (userId set, guestName null).
     */
    public boolean isAuthenticatedUser() {
        return userId != null && guestName == null;
    }

    /**
     * Returns true if this permission grants edit access.
     * Convenience wrapper over the role enum to avoid callers comparing against
     * PermissionRole.EDIT directly.
     */
    public boolean canEdit() {
        return role == PermissionRole.EDIT;
    }

    /**
     * Returns true if this permission is read-only (VIEW level).
     */
    public boolean isViewOnly() {
        return role == PermissionRole.VIEW;
    }

    // ── Role mutation helpers ─────────────────────────────────────────────────

    /**
     * Upgrades this permission to EDIT level and records the current time as
     * the grant timestamp. Called by {@link org.scriptdojo.backend.service.PermissionService}
     * when the host grants edit access to a guest via the permissions panel.
     */
    public void grantEdit() {
        this.role = PermissionRole.EDIT;
        this.grantedAt = LocalDateTime.now(); // Record when the upgrade occurred
    }

    /**
     * Downgrades this permission to VIEW level and records the current time.
     * Called by {@link org.scriptdojo.backend.service.PermissionService}
     * when the host revokes edit access from a guest.
     */
    public void revokeEdit() {
        this.role = PermissionRole.VIEW;
        this.grantedAt = LocalDateTime.now(); // Record when the downgrade occurred
    }

    // ── JPA lifecycle validation ──────────────────────────────────────────────

    /**
     * Enforces the mutual exclusivity invariant between userId and guestName
     * before every INSERT and UPDATE operation.
     *
     * Valid states:
     *   - userId set,    guestName null  → authenticated user permission
     *   - userId null,   guestName set   → guest permission
     *
     * Invalid states (both throw {@link IllegalStateException}):
     *   - Both set   → ambiguous identity; cannot determine permission holder
     *   - Neither set → orphaned record with no identifiable holder
     *
     * Fires on both @PrePersist and @PreUpdate so the constraint is checked
     * on initial creation and on every subsequent modification.
     */
    @PrePersist
    @PreUpdate
    private void validatePermission() {
        boolean hasUserId = userId != null;
        boolean hasGuestName = guestName != null && !guestName.isBlank();

        if (hasUserId && hasGuestName) {
            throw new IllegalStateException("Permission cannot have both userId and guestName");
        }
        if (!hasUserId && !hasGuestName) {
            throw new IllegalStateException("Permission must have either userId or guestName");
        }
    }
}