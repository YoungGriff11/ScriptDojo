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

/**
 * STOMP message controller for real-time collaborative editing in ScriptDojo.
 *
 * Handles two categories of inbound WebSocket messages:
 *
 * 1. Text edits (/app/room/{fileId}/edit)
 *    - Resolves the editor's username (host Principal or guest body field)
 *    - Persists the latest content to the database
 *    - Runs the ANTLR parser and broadcasts any syntax errors to the error channel
 *    - Broadcasts the change to all room subscribers so their editors stay in sync
 *
 * 2. Cursor moves (/app/room/{fileId}/cursor)
 *    - Broadcasts the cursor position to all room subscribers so each participant
 *      can see where others are editing in real time
 *    - Stateless: no persistence or parsing; the payload is forwarded as-is
 *
 *  3. Username resolution (edits only):
 *   Authenticated hosts  → Spring Security Principal (injected by the framework)
 *   Unauthenticated guests → username field in the TextChange message body
 *   Neither available    → "Anonymous" fallback
 */
@Controller
@RequiredArgsConstructor // Lombok: generates constructor injecting all final fields
@Slf4j                   // Lombok: injects a static SLF4J logger
public class CollaborationController {

    // Persists file content changes to the database
    private final FileService fileService;

    // Runs the ANTLR v4 Java grammar against the editor content to detect syntax errors
    private final ParserService parserService;

    // Used to push messages to specific destinations outside of a @SendTo return value
    // (specifically for broadcasting parser errors to the /errors channel)
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handles real-time text edits sent by any participant in a collaborative room.
     *
     * Inbound:  /app/room/{fileId}/edit       (TextChange payload)
     * Outbound: /topic/room/{fileId}           (TextChange broadcast to all subscribers)
     *           /topic/room/{fileId}/errors    (ParserErrorBroadcast, sent manually)
     *
     * Processing steps:
     *   1. Resolve the editor's display name
     *   2. Persist the updated content to the database
     *   3. Parse the content with ANTLR and broadcast syntax errors (or an empty
     *      error list if the content is blank, to clear existing markers in the editor)
     *   4. Return the TextChange so @SendTo broadcasts it to all room subscribers
     *
     * Parser errors are non-critical — a parse failure is caught and logged but does
     * not prevent the edit from being saved or broadcast.
     *
     * @param fileId    the ID of the file being edited, extracted from the destination path
     * @param change    the incoming edit payload containing the full current content
     *                  and optionally the sender's username (required for guests)
     * @param principal the Spring Security principal of the sender, or null for guests
     * @return the TextChange to broadcast to /topic/room/{fileId}, with the resolved username
     */
    @MessageMapping("/room/{fileId}/edit")
    @SendTo("/topic/room/{fileId}")
    public TextChange handleEdit(
            @DestinationVariable Long fileId,
            @Payload TextChange change,
            Principal principal) {

        // ─ Username resolution
        // Guests are unauthenticated, so principal is null for them. Their chosen
        // display name is included in the TextChange body instead (set by the frontend).
        // See WebSocketConfig for how guest names are captured on CONNECT.
        String username;
        if (principal != null) {
            username = principal.getName();                              // Authenticated host
        } else if (change.username() != null && !change.username().trim().isEmpty()) {
            username = change.username();                                // Guest via message body
        } else {
            username = "Anonymous";                                      // Fallback
        }

        log.info("═════════════════════════");
        log.info("📝 EDIT RECEIVED");
        log.info("   File ID: {}", fileId);
        log.info("   User: {}", username);
        log.info("   Content Length: {} characters",
                change.content() != null ? change.content().length() : 0);

        if (change.content() != null && change.content().length() > 0) {
            log.info("   First 100 chars: {}",
                    change.content().substring(0, Math.min(100, change.content().length())));
        }

        // ─ Persistence
        // Save the full current content of the editor to the database on every edit.
        // Failures are logged but do not interrupt the broadcast — other participants
        // should still receive the change even if persistence fails transiently.
        try {
            fileService.updateFileContent(fileId, change.content());
            log.info("✅ Database update successful");
        } catch (Exception e) {
            log.error("❌ Database update FAILED: {}", e.getMessage(), e);
        }

        // ─ Syntax parsing and error broadcast
        // Run the ANTLR parser on the current content and broadcast the result to
        // /topic/room/{fileId}/errors. All subscribers (including the sender) update
        // their Monaco Editor error markers based on this broadcast.
        //
        // An empty error list is broadcast for blank content to ensure any existing
        // markers are cleared in the editor rather than left stale.
        if (change.content() != null && !change.content().trim().isEmpty()) {
            try {
                log.info("🔍 Parsing code for syntax errors...");
                ParseResult parseResult = parserService.parseJavaCode(change.content());

                if (parseResult.getErrors() != null && !parseResult.getErrors().isEmpty()) {
                    log.warn("⚠️ Found {} syntax error(s)", parseResult.getErrors().size());
                } else {
                    log.info("✅ No syntax errors found");
                }

                // Wrap the parse result in a broadcast DTO that also carries the
                // username and a server-side timestamp for ordering on the client
                ParserErrorBroadcast errorBroadcast = new ParserErrorBroadcast(
                        username,
                        parseResult.getErrors(),
                        System.currentTimeMillis()
                );

                messagingTemplate.convertAndSend(
                        "/topic/room/" + fileId + "/errors",
                        errorBroadcast
                );
                log.info("📡 Broadcast errors to /topic/room/{}/errors", fileId);

            } catch (Exception e) {
                // Parser failures are non-critical: the edit is already saved and will
                // be broadcast. Error markers simply won't update for this edit cycle.
                log.error("❌ Parser error (non-critical):", e);
            }
        } else {
            // Content is blank — broadcast an empty error list to clear any syntax
            // markers that were previously displayed in the editor
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

        // Return the TextChange with the resolved username so @SendTo can broadcast
        // it to all /topic/room/{fileId} subscribers (including the original sender)
        return new TextChange(change.content(), username);
    }

    /**
     * Handles real-time cursor position updates sent by any participant.
     *
     * Inbound:  /app/room/{fileId}/cursor      (CursorMove payload)
     * Outbound: /topic/room/{fileId}/cursors   (CursorMove broadcast to all subscribers)
     *
     * Stateless and lightweight: the payload is validated by the framework and
     * returned as-is for broadcasting. No persistence or parsing is performed.
     * The session ID is logged at DEBUG level for traceability during development.
     *
     * @param fileId         the ID of the file, extracted from the destination path
     * @param move           the cursor position payload containing username, line, and column
     * @param headerAccessor provides access to STOMP session metadata (session ID for logging)
     * @return the CursorMove to broadcast to /topic/room/{fileId}/cursors
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

        // Forward the cursor position unchanged — all subscribers render it as a
        // remote cursor overlay in their Monaco Editor instance
        return move;
    }
}