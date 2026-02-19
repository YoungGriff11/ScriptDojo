package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.parser.ParserService;
import org.scriptdojo.backend.service.dto.ParseResult;
import org.scriptdojo.backend.service.dto.ParserErrorBroadcast;
import org.scriptdojo.backend.service.dto.TextChange;
import org.scriptdojo.backend.service.dto.CursorMove;
import org.scriptdojo.backend.service.FileService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Collections;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CollaborationController {

    private final FileService fileService;
    private final ParserService parserService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle real-time text edits
     * Route: /app/room/{fileId}/edit
     * Broadcast to: /topic/room/{fileId}
     * Broadcast errors to: /topic/room/{fileId}/errors
     */
    @MessageMapping("/room/{fileId}/edit")
    @SendTo("/topic/room/{fileId}")
    public TextChange handleEdit(
            @DestinationVariable Long fileId,
            @Payload TextChange change,
            Principal principal) {

        // ✅ FIX: Use username from the message body if principal is null (guest)
        String username;
        if (principal != null) {
            username = principal.getName();  // Authenticated user
        } else if (change.username() != null && !change.username().trim().isEmpty()) {
            username = change.username();  // Guest sent their name in the body
        } else {
            username = "Anonymous";  // Fallback
        }

        log.info("════════════════════════════════════════════════════");
        log.info("📝 EDIT RECEIVED");
        log.info("   File ID: {}", fileId);
        log.info("   User: {}", username);
        log.info("   Content Length: {} characters", change.content() != null ? change.content().length() : 0);

        if (change.content() != null && change.content().length() > 0) {
            log.info("   First 100 chars: {}",
                    change.content().substring(0, Math.min(100, change.content().length())));
        }

        // Save to database
        try {
            fileService.updateFileContent(fileId, change.content());
            log.info("✅ Database update successful");
        } catch (Exception e) {
            log.error("❌ Database update FAILED: {}", e.getMessage(), e);
        }

        // Parse code and broadcast syntax errors
        if (change.content() != null && !change.content().trim().isEmpty()) {
            try {
                log.info("🔍 Parsing code for syntax errors...");
                ParseResult parseResult = parserService.parseJavaCode(change.content());

                if (parseResult.getErrors() != null && !parseResult.getErrors().isEmpty()) {
                    log.warn("⚠️ Found {} syntax error(s)", parseResult.getErrors().size());
                } else {
                    log.info("✅ No syntax errors found");
                }

                ParserErrorBroadcast errorBroadcast = new ParserErrorBroadcast(
                        username,  // Use the resolved username
                        parseResult.getErrors(),
                        System.currentTimeMillis()
                );

                messagingTemplate.convertAndSend(
                        "/topic/room/" + fileId + "/errors",
                        errorBroadcast
                );
                log.info("📡 Broadcast errors to /topic/room/{}/errors", fileId);

            } catch (Exception e) {
                log.error("❌ Parser error (non-critical):", e);
            }
        } else {
            log.info("⚪ Empty content - skipping parser, clearing errors");

            ParserErrorBroadcast errorBroadcast = new ParserErrorBroadcast(
                    username,
                    Collections.emptyList(),
                    System.currentTimeMillis()
            );

            messagingTemplate.convertAndSend(
                    "/topic/room/" + fileId + "/errors",
                    errorBroadcast
            );
        }

        log.info("📡 Broadcasting to /topic/room/{}", fileId);
        log.info("════════════════════════════════════════════════════");


        return new TextChange(change.content(), username);
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