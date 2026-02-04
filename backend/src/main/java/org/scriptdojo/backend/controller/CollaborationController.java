package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.service.dto.TextChange;
import org.scriptdojo.backend.service.dto.CursorMove;
import org.scriptdojo.backend.service.FileService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CollaborationController {

    private final FileService fileService;

    /**
     * Handle real-time text edits
     * Route: /app/room/{fileId}/edit
     * Broadcast to: /topic/room/{fileId}
     */
    @MessageMapping("/room/{fileId}/edit")
    @SendTo("/topic/room/{fileId}")
    public TextChange handleEdit(
            @DestinationVariable Long fileId,
            @Payload TextChange change,
            Principal principal) {

        // Get username (authenticated user or anonymous)
        String username = (principal != null) ? principal.getName() : "Anonymous";

        log.info("════════════════════════════════════════════════════");
        log.info("📝 EDIT RECEIVED");
        log.info("   File ID: {}", fileId);
        log.info("   User: {}", username);
        log.info("   Content Length: {} characters", change.content().length());
        log.info("   First 100 chars: {}",
                change.content().substring(0, Math.min(100, change.content().length())));

        // Save to database
        try {
            fileService.updateFileContent(fileId, change.content());
            log.info("✅ Database update successful");
        } catch (Exception e) {
            log.error("❌ Database update FAILED: {}", e.getMessage(), e);
            // Still broadcast the change even if DB save fails (eventual consistency)
        }

        log.info("📡 Broadcasting to /topic/room/{}", fileId);
        log.info("════════════════════════════════════════════════════");

        // Return the same change to broadcast to all subscribers
        return change;
    }

    /**
     * Handle cursor position updates
     * Route: /app/room/{fileId}/cursor
     * Broadcast to: /topic/room/{fileId}/cursors
     */
    @MessageMapping("/room/{fileId}/cursor")
    @SendTo("/topic/room/{fileId}/cursors")
    public CursorMove handleCursor(
            @DestinationVariable Long fileId,
            @Payload CursorMove move,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();

        log.debug("👆 Cursor Update - User: '{}', Position: {}:{}, Session: {}",
                move.username(), move.line(), move.column(), sessionId);

        return move;
    }
}