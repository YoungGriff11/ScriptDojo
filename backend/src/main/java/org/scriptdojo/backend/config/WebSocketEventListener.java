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

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final ActiveUsersService activeUsersService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Map to track which file each session is connected to
     * sessionId -> fileId
     */
    private final Map<String, Long> sessionToFile = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Map to track username for each session
     * sessionId -> username
     */
    private final Map<String, String> sessionToUsername = new java.util.concurrent.ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("════════════════════════════════════════════════════");
        log.info("🔌 NEW WebSocket Connection");
        log.info("   Session ID: {}", sessionId);
        log.info("════════════════════════════════════════════════════");
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();

        // Get username - CRITICAL: Check session attributes FIRST (for guests)
        // then fall back to authenticated user (for hosts)
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        if (username == null && headerAccessor.getUser() != null) {
            // Fallback to authenticated user (host)
            username = headerAccessor.getUser().getName();
            log.debug("   Username from authentication: {}", username);
        } else if (username != null) {
            log.debug("   Username from session attributes: {}", username);
        } else {
            // Last resort: generate from session ID
            username = "Guest_" + sessionId.substring(0, 8);
            log.debug("   Username generated from session: {}", username);
        }

        log.info("📻 SUBSCRIPTION: {} subscribed to {}", username, destination);

        // Parse destination to extract fileId
        // Format: /topic/room/{fileId} or /topic/room/{fileId}/cursors
        if (destination != null && destination.startsWith("/topic/room/")) {
            try {
                String[] parts = destination.split("/");
                if (parts.length >= 4) {
                    Long fileId = Long.parseLong(parts[3]);

                    // Track this session
                    sessionToFile.put(sessionId, fileId);
                    sessionToUsername.put(sessionId, username);

                    // Add to active users
                    activeUsersService.addUser(fileId, username);

                    // Broadcast updated user list
                    broadcastActiveUsers(fileId);
                }
            } catch (NumberFormatException e) {
                log.warn("⚠️ Could not parse fileId from destination: {}", destination);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("════════════════════════════════════════════════════");
        log.info("❌ WebSocket Disconnected");
        log.info("   Session ID: {}", sessionId);

        // Remove from active users
        Long fileId = sessionToFile.remove(sessionId);
        String username = sessionToUsername.remove(sessionId);

        if (fileId != null && username != null) {
            activeUsersService.removeUser(fileId, username);
            log.info("   Removed {} from file {}", username, fileId);

            // Broadcast updated user list
            broadcastActiveUsers(fileId);
        }

        log.info("════════════════════════════════════════════════════");
    }

    /**
     * Broadcast the active user list to all subscribers of a file
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