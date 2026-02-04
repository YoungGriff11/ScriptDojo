package org.scriptdojo.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "permission")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /**
     * For authenticated users (null if guest)
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * For anonymous guests (null if authenticated user)
     * Example: "Guest1234", "Guest7382"
     */
    @Column(name = "guest_name", length = 100)
    private String guestName;

    /**
     * Permission role: VIEW or EDIT
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PermissionRole role = PermissionRole.VIEW;

    /**
     * When permission was granted
     */
    @Column(name = "granted_at")
    private LocalDateTime grantedAt = LocalDateTime.now();

    /**
     * User ID of the person who granted this permission (host)
     */
    @Column(name = "granted_by")
    private Long grantedBy;

    /**
     * Get the identifier (either username or guest name)
     */
    public String getIdentifier() {
        if (guestName != null) {
            return guestName;
        }
        return userId != null ? "User_" + userId : "Unknown";
    }

    /**
     * Check if this is a guest permission
     */
    public boolean isGuest() {
        return guestName != null && userId == null;
    }

    /**
     * Check if this is an authenticated user permission
     */
    public boolean isAuthenticatedUser() {
        return userId != null && guestName == null;
    }

    /**
     * Check if this permission allows editing
     */
    public boolean canEdit() {
        return role == PermissionRole.EDIT;
    }

    /**
     * Check if this permission is view-only
     */
    public boolean isViewOnly() {
        return role == PermissionRole.VIEW;
    }

    /**
     * Upgrade to edit permission
     */
    public void grantEdit() {
        this.role = PermissionRole.EDIT;
        this.grantedAt = LocalDateTime.now();
    }

    /**
     * Downgrade to view-only
     */
    public void revokeEdit() {
        this.role = PermissionRole.VIEW;
        this.grantedAt = LocalDateTime.now();
    }

    /**
     * Validate that either userId OR guestName is set (not both, not neither)
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