package org.scriptdojo.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
public class WebSocketEventListener {

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
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("════════════════════════════════════════════════════");
        log.info("❌ WebSocket Disconnected");
        log.info("   Session ID: {}", sessionId);
        log.info("════════════════════════════════════════════════════");
    }
}