package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.entity.PermissionEntity;
import org.scriptdojo.backend.service.ActiveUsersService;
import org.scriptdojo.backend.service.FileService;
import org.scriptdojo.backend.service.PermissionService;
import org.scriptdojo.backend.service.dto.PermissionDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST controller for managing guest edit permissions in ScriptDojo.
 * Hosts (authenticated file owners) use these endpoints to grant or revoke
 * edit access for guests who have joined their room. Guests are identified
 * by their display name rather than a user account, since they are unauthenticated.
 * Every permission change is immediately broadcast over WebSocket to the room's
 * permissions channel (/topic/room/{fileId}/permissions) so all connected
 * participants can update their UI state (e.g. enabling/disabling the editor)
 * in real time without polling.
 * All mutating endpoints enforce that only the file owner can grant or revoke
 * permissions, returning 403 Forbidden otherwise.
 * Base path: /api/permissions
 * Note: revokeEditPermission currently uses a placeholder user ID and should
 * be updated to resolve the authenticated user consistently with grantEditPermission.
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor // Lombok: generates constructor injecting all final fields
@Slf4j                   // Lombok: injects a static SLF4J logger
public class PermissionController {

    // Handles persistence and lookup of guest permission records
    private final PermissionService permissionService;

    // Used to verify file ownership before mutating permissions
    private final FileService fileService;

    // Used to push permission change events to all room participants over WebSocket
    private final SimpMessagingTemplate messagingTemplate;

    // Provides the current set of connected users for a given file/room
    private final ActiveUsersService activeUsersService;

    /**
     * Grants edit permission to a named guest in a collaborative room.
     * Only the authenticated owner of the file may call this endpoint.
     * On success, the permission is persisted and a WebSocket broadcast is sent
     * to /topic/room/{fileId}/permissions so the guest's editor is unlocked
     * immediately without requiring a page refresh.
     * POST /api/permissions/grant-edit
     * @param fileId    the ID of the file/room the guest is joining
     * @param guestName the display name of the guest to grant edit access to
     * @param auth      the authentication of the requesting user; must be the file owner
     * @return 200 OK with the created {@link PermissionDTO} if successful,
     *         403 Forbidden if the requester is not the file owner
     */
    @PostMapping("/grant-edit")
    public ResponseEntity<?> grantEditPermission(
            @RequestParam Long fileId,
            @RequestParam String guestName,
            Authentication auth
    ) {
        log.info("════════════════════════════════════════════════════");
        log.info("🔓 API: Grant Edit Permission Request");
        log.info("   File ID: {}", fileId);
        log.info("   Guest: {}", guestName);
        log.info("   Granted By: {}", auth.getName());

        // Cast to CustomUserDetails to retrieve the database user ID.
        // Safe because CustomUserDetailsService always produces CustomUserDetails
        // instances for authenticated sessions.
        Long currentUserId = ((org.scriptdojo.backend.security.CustomUserDetails) auth.getPrincipal()).getId();

        // Enforce ownership — only the file owner may grant edit permissions
        if (!fileService.isOwner(fileId, currentUserId)) {
            log.warn("❌ Permission denied - not file owner");
            log.info("════════════════════════════════════════════════════");
            return ResponseEntity.status(403).body(Map.of("error", "Only file owner can grant permissions"));
        }

        // Persist the grant; PermissionService records the guest name, file, and granting user
        PermissionEntity permission = permissionService.grantGuestEdit(guestName, fileId, currentUserId);

        // Broadcast the permission change to all room subscribers so the guest's
        // editor is enabled immediately without requiring a page refresh
        messagingTemplate.convertAndSend(
                "/topic/room/" + fileId + "/permissions",
                Map.of(
                        "guestName", guestName,
                        "canEdit", true,
                        "fileId", fileId
                )
        );

        log.info("✅ Edit permission granted and broadcast");
        log.info("════════════════════════════════════════════════════");

        return ResponseEntity.ok(toDTO(permission));
    }

    /**
     * Revokes edit permission from a named guest in a collaborative room.
     * Only the file owner should be able to call this endpoint. The permission
     * record is removed from the database and a WebSocket broadcast is sent to
     * /topic/room/{fileId}/permissions so the guest's editor is locked immediately.
     * POST /api/permissions/revoke-edit
     * NOTE: The owner ID is currently hardcoded to a placeholder value (1000L).
     * This means the ownership check will always fail in practice and the endpoint
     * will always return 403. This should be updated to resolve the authenticated
     * user ID from the Authentication principal, consistent with grantEditPermission.
     * @param fileId    the ID of the file/room
     * @param guestName the display name of the guest whose edit access should be removed
     * @param auth      the authentication of the requesting user; must be the file owner
     * @return 200 OK with {"success": true} if revocation succeeds,
     *         403 Forbidden if the requester is not the file owner
     */
    @PostMapping("/revoke-edit")
    public ResponseEntity<?> revokeEditPermission(
            @RequestParam Long fileId,
            @RequestParam String guestName,
            Authentication auth
    ) {
        log.info("🔒 API: Revoke Edit Permission Request");
        log.info("   File ID: {}", fileId);
        log.info("   Guest: {}", guestName);

        // The placeholder value (1000L) will cause isOwner() to return false for
        // all real users, making this endpoint always return 403.
        Long currentUserId = 1000L; // Placeholder

        if (!fileService.isOwner(fileId, currentUserId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only file owner can revoke permissions"));
        }

        permissionService.revokeGuestEdit(guestName, fileId);

        // Broadcast the revocation so the guest's editor is locked immediately
        messagingTemplate.convertAndSend(
                "/topic/room/" + fileId + "/permissions",
                Map.of(
                        "guestName", guestName,
                        "canEdit", false,
                        "fileId", fileId
                )
        );

        log.info("✅ Edit permission revoked and broadcast");

        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Returns all permission records associated with a given file.
     * Used by the host's UI to display the current permission state of all
     * guests who have been granted or had access revoked in the room.
     * GET /api/permissions/file/{fileId}
     * @param fileId the ID of the file whose permissions should be listed
     * @param auth   the authentication of the requesting user (present but not
     *               currently used for ownership enforcement on this read endpoint)
     * @return 200 OK with the list of {@link PermissionDTO} records for the file
     */
    @GetMapping("/file/{fileId}")
    public ResponseEntity<List<PermissionDTO>> getFilePermissions(
            @PathVariable Long fileId,
            Authentication auth
    ) {
        log.info("📋 API: Get File Permissions");
        log.info("   File ID: {}", fileId);

        List<PermissionEntity> permissions = permissionService.getFilePermissions(fileId);

        // Map each entity to a DTO to avoid exposing the internal entity structure
        List<PermissionDTO> dtos = permissions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Returns the set of users currently connected to a room.
     * Provides an HTTP alternative to the WebSocket active-user broadcasts
     * emitted by WebSocketEventListener. Useful for the host to query the
     * current presence state on initial page load before WebSocket events arrive.
     * GET /api/permissions/file/{fileId}/active-users
     * @param fileId the ID of the file/room to query
     * @return 200 OK with a JSON object containing the fileId, the set of
     *         usernames, and a convenience count field
     */
    @GetMapping("/file/{fileId}/active-users")
    public ResponseEntity<?> getActiveUsers(@PathVariable Long fileId) {
        Set<String> users = activeUsersService.getActiveUsers(fileId);

        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "users", users,
                "count", users.size()
        ));
    }

    /**
     * Converts a {@link PermissionEntity} to a {@link PermissionDTO} for API responses.
     * Keeps the internal entity structure decoupled from the API contract —
     * callers receive only the fields relevant to the frontend, with enum values
     * serialised as strings via {@code role.name()}.
     * @param entity the permission entity to convert
     * @return a DTO representation safe for serialisation in API responses
     */
    private PermissionDTO toDTO(PermissionEntity entity) {
        return new PermissionDTO(
                entity.getId(),
                entity.getFileId(),
                entity.getIdentifier(),  // Guest display name or user identifier
                entity.getRole().name(), // Enum serialised as string (e.g. "GUEST", "HOST")
                entity.canEdit(),
                entity.isGuest(),
                entity.getGrantedAt()
        );
    }
}