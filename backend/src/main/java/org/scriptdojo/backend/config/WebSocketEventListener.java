package org.scriptdojo.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.service.ActiveUsersService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;

/**
 * Listens for WebSocket lifecycle events (connect, subscribe, disconnect) and
 * maintains the active users list for each collaborative editing session.
 * Responsibilities:
 * - Tracks which file (room) each WebSocket session is currently viewing
 * - Resolves the display name for both authenticated hosts and unauthenticated guests
 * - Adds/removes users from ActiveUsersService as they join or leave
 * - Broadcasts the updated user list to all room subscribers after every change
 * Username resolution priority (handled in handleSubscribeEvent):
 *   1. Session attributes  — set by WebSocketConfig interceptor for guests
 *   2. Spring Security Principal — available for authenticated hosts
 *   3. Generated fallback — "Guest_" + first 8 chars of session ID (last resort)
 */
@Component
@RequiredArgsConstructor // Lombok: generates constructor injecting activeUsersService and messagingTemplate
@Slf4j                   // Lombok: injects a static SLF4J logger
public class WebSocketEventListener {

    // Service that maintains the in-memory set of active users per file
    private final ActiveUsersService activeUsersService;

    // Used to push the updated active-user list to all room subscribers
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Maps WebSocket session ID → file ID.
     * Populated on subscribe, consumed and cleared on disconnect.
     * ConcurrentHashMap used because WebSocket events can arrive on different threads.
     */
    private final Map<String, Long> sessionToFile = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Maps WebSocket session ID → resolved display name.
     * Mirrors sessionToFile so that both pieces of state are always cleaned up together on disconnect.
     */
    private final Map<String, String> sessionToUsername = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Fired when a new WebSocket connection is established (STOMP CONNECTED frame received).
     * At this point the client has not yet subscribed to any destination, so no room
     * state is updated here — this handler exists purely for connection-level logging.
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("════════════════════════════════════════════════════");
        log.info("🔌 NEW WebSocket Connection");
        log.info("   Session ID: {}", sessionId);
        log.info("════════════════════════════════════════════════════");
    }

    /**
     * Used when a client sends a STOMP SUBSCRIBE frame.
     * This is the primary entry point for room presence tracking. When a client
     * subscribes to a /topic/room/{fileId} destination, they are considered to have
     * joined that room and are added to the active users list.
     * Username resolution follows a three-step priority chain to handle both
     * authenticated hosts (who have a Spring Security Principal) and unauthenticated
     * guests (whose name was stored in session attributes by the STOMP CONNECT interceptor).
     * Destinations that do not match /topic/room/{fileId} (e.g. cursor channels) are
     * parsed but only the numeric fileId segment triggers presence tracking — non-numeric
     * segments (e.g. "cursors") are silently skipped via the NumberFormatException catch.
     */
    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();

        // ─ Username resolution
        // Guests have no Spring Security Principal; their name was captured from the
        // STOMP CONNECT frame and stored in session attributes by WebSocketConfig.
        // Hosts are authenticated and their name comes from the Principal instead.
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        if (username == null && headerAccessor.getUser() != null) {
            // Host path: no session attribute set, but a Principal is present
            username = headerAccessor.getUser().getName();
            log.debug("   Username from authentication: {}", username);
        } else if (username != null) {
            // Guest path: session attribute was populated by the CONNECT interceptor
            log.debug("   Username from session attributes: {}", username);
        } else {
            // Fallback: neither a Principal nor a session attribute is available.
            // Generates a deterministic name so the user still appears in the active list.
            username = "Guest_" + sessionId.substring(0, 8);
            log.debug("   Username generated from session: {}", username);
        }

        log.info("📻 SUBSCRIPTION: {} subscribed to {}", username, destination);

        // ─ Room presence tracking
        // Only subscriptions to /topic/room/{fileId} trigger presence updates.
        // Companion channels like /topic/room/{fileId}/cursors share the same
        // fileId segment but are not treated as separate join events.
        // Destination format: /topic/room/{fileId} or /topic/room/{fileId}/cursors
        if (destination != null && destination.startsWith("/topic/room/")) {
            try {
                String[] parts = destination.split("/");
                // parts[0]="", parts[1]="topic", parts[2]="room", parts[3]=fileId
                if (parts.length >= 4) {
                    Long fileId = Long.parseLong(parts[3]); // throws if segment is not numeric

                    // Record the session → file and session → username mappings
                    // so they can be looked up efficiently on disconnect
                    sessionToFile.put(sessionId, fileId);
                    sessionToUsername.put(sessionId, username);

                    // Register the user in the shared in-memory presence store
                    activeUsersService.addUser(fileId, username);

                    // Notify all room subscribers of the updated user list
                    broadcastActiveUsers(fileId);
                }
            } catch (NumberFormatException e) {
                // Destination segment is not a fileId (e.g. "cursors") — not a room subscription
                log.warn("⚠️ Could not parse fileId from destination: {}", destination);
            }
        }
    }

    /**
     * Fired when a WebSocket session is closed (client disconnected or network dropped).
     * Cleans up both session tracking maps and removes the user from the active users
     * store, then broadcasts the updated list so all remaining room participants see
     * the departure immediately without needing to poll.
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("════════════════════════════════════════════════════");
        log.info("❌ WebSocket Disconnected");
        log.info("   Session ID: {}", sessionId);

        // Atomically remove and retrieve both tracking entries.
        // If either is null the session never completed a room subscription
        // (e.g. the client connected but never subscribed), so no presence
        // cleanup is needed.
        Long fileId = sessionToFile.remove(sessionId);
        String username = sessionToUsername.remove(sessionId);

        if (fileId != null && username != null) {
            activeUsersService.removeUser(fileId, username);
            log.info("   Removed {} from file {}", username, fileId);

            // Push the updated user list to remaining room subscribers
            broadcastActiveUsers(fileId);
        }

        log.info("════════════════════════════════════════════════════");
    }

    /**
     * Publishes the current active user list for a given file to all subscribers
     * of the /topic/room/{fileId}/users destination.
     * The payload includes the fileId, the list of usernames, and a convenience
     * count field so the frontend does not need to compute the size itself.
     * Called after every join and leave event to keep all clients in sync.
     * @param fileId the ID of the file/room whose user list should be broadcast
     */
    private void broadcastActiveUsers(Long fileId) {
        var activeUsers = activeUsersService.getActiveUsers(fileId);

        messagingTemplate.convertAndSend(
                "/topic/room/" + fileId + "/users",
                Map.of(
                        "fileId", fileId,
                        "users", activeUsers,
                        "count", activeUsers.size()
                )
        );

        log.info("📡 Broadcast active users for file {}: {}", fileId, activeUsers);
    }
}