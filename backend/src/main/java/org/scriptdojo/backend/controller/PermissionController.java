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

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Slf4j
public class PermissionController {

    private final PermissionService permissionService;
    private final FileService fileService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActiveUsersService activeUsersService;

    /**
     * Grant edit permission to a guest
     * POST /api/permissions/grant-edit
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

        // Verify requester is the file owner
        // TODO: Get current user ID from auth - implement based on your CustomUserDetails
        Long currentUserId = 1000L; // Placeholder

        if (!fileService.isOwner(fileId, currentUserId)) {
            log.warn("❌ Permission denied - not file owner");
            log.info("════════════════════════════════════════════════════");
            return ResponseEntity.status(403).body(Map.of("error", "Only file owner can grant permissions"));
        }

        // Grant edit permission
        PermissionEntity permission = permissionService.grantGuestEdit(guestName, fileId, currentUserId);

        // Broadcast permission update via WebSocket
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
     * Revoke edit permission from a guest
     * POST /api/permissions/revoke-edit
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

        // Verify requester is the file owner
        Long currentUserId = 1000L; // Placeholder

        if (!fileService.isOwner(fileId, currentUserId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only file owner can revoke permissions"));
        }

        permissionService.revokeGuestEdit(guestName, fileId);

        // Broadcast permission update via WebSocket
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
     * Get all permissions for a file
     * GET /api/permissions/file/{fileId}
     */
    @GetMapping("/file/{fileId}")
    public ResponseEntity<List<PermissionDTO>> getFilePermissions(
            @PathVariable Long fileId,
            Authentication auth
    ) {
        log.info("📋 API: Get File Permissions");
        log.info("   File ID: {}", fileId);

        List<PermissionEntity> permissions = permissionService.getFilePermissions(fileId);

        List<PermissionDTO> dtos = permissions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get active users for a file
     * GET /api/permissions/file/{fileId}/active-users
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
     * Convert entity to DTO
     */
    private PermissionDTO toDTO(PermissionEntity entity) {
        return new PermissionDTO(
                entity.getId(),
                entity.getFileId(),
                entity.getIdentifier(),
                entity.getRole().name(),
                entity.canEdit(),
                entity.isGuest(),
                entity.getGrantedAt()
        );
    }
}