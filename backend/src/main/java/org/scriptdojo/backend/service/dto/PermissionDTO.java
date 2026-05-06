package org.scriptdojo.backend.service.dto;

import java.time.LocalDateTime;

/**
 * Immutable record used to transfer permission data in API responses without
 * exposing the internal {@link org.scriptdojo.backend.entity.PermissionEntity}
 * structure or its enum types.
 * and returned by the grant, revoke, and list permission endpoints so the
 * frontend can update the host's permission management panel.
 */
public record PermissionDTO(
        Long id,  //the unique database identifier of the permission record
        Long fileId, //the ID of the file this permission applies to
        String identifier,  //the display name of the permission holder — either a guest name (e.g. "Guest1234") or a prefixed user ID (e.g. "User_42")
        String role,  //the access level as a string — either "VIEW" or "EDIT"
        boolean canEdit,  //convenience flag derived from role; true if role is "EDIT"
        boolean isGuest,  //true if this permission belongs to an unauthenticated guest, false if it belongs to an authenticated user
        LocalDateTime grantedAt  //the timestamp at which this permission was most recently granted or modified
) {}