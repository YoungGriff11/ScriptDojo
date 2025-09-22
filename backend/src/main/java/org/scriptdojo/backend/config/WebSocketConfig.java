package org.scriptdojo.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new CustomWebSocketHandler(), "/ws").setAllowedOrigins("*");
    }
}

// Placeholder handler (to be implemented next)
class CustomWebSocketHandler implements org.springframework.web.socket.WebSocketHandler {
    @Override public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) { }
    @Override public void handleMessage(org.springframework.web.socket.WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> message) { }
    @Override public void handleTransportError(org.springframework.web.socket.WebSocketSession session, Throwable exception) { }
    @Override public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session, org.springframework.web.socket.CloseStatus closeStatus) { }
    @Override public boolean supportsPartialMessages() { return false; }
}
