package org.scriptdojo.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket and STOMP messaging configuration for ScriptDojo.
 * Responsibilities:
 * - Registers the /ws endpoint that clients connect to via SockJS
 * - Configures the in-memory message broker and application destination prefix
 * - Intercepts STOMP CONNECT frames to capture and store guest usernames,
 *   since unauthenticated guests have no Spring Security Principal
 */
@Configuration
@EnableWebSocketMessageBroker // Enables the STOMP-over-WebSocket message broker
@Slf4j                        // Lombok: injects a static 'log' field (SLF4J logger)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configures the message broker that routes messages between clients.
     * - /topic: prefix for broker-handled destinations (pub/sub fan-out).
     *   Messages sent to e.g. /topic/room/abc are broadcast to all subscribers.
     * - /app: prefix for application-handled destinations. Messages sent to
     *   e.g. /app/editor/abc are routed to a @MessageMapping method in a
     *   @Controller before optionally being forwarded to the broker.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");         // In-memory broker for broadcasting
        config.setApplicationDestinationPrefixes("/app"); // Routes to @MessageMapping handlers
    }

    /**
     * Registers the WebSocket handshake endpoint that clients connect to.
     * - /ws: the URL clients use to initiate the WebSocket connection
     * - setAllowedOriginPatterns("*"): permits connections from any origin.
     *   CORS for REST is handled separately in SecurityConfig; this covers
     *   the WebSocket upgrade request.
     * - withSockJS(): enables SockJS fallback so clients on networks that
     *   block WebSockets can fall back to HTTP long-polling or other transports.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Registers a channel interceptor on the inbound message channel.
     * The interceptor runs before every inbound STOMP frame is processed.
     * Its sole responsibility is to capture the guest username from the
     * STOMP CONNECT frame and store it in the WebSocket session attributes,
     * making it available to @MessageMapping handlers for the lifetime of
     * the connection.
     * Background: authenticated hosts have a Spring Security Principal that
     * controllers can read directly. Guests are unauthenticated (no Principal),
     * so their chosen display name is passed as a custom STOMP header on
     * CONNECT and persisted here instead.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            /**
             * Intercepts each inbound message before it is dispatched.
             * Only acts on STOMP CONNECT frames; all other frame types pass through unchanged.
             * @param message the inbound STOMP message
             * @param channel the channel the message is being sent on
             * @return the (possibly mutated) message to continue processing
             */
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {

                    // Read the custom 'username' header sent by the guest client on connect.
                    // Authenticated hosts do not send this header; their identity is resolved
                    // from the Spring Security Principal in the controller layer instead.
                    String username = accessor.getFirstNativeHeader("username");

                    if (username != null) {
                        // Store the username in the WebSocket session attribute map so it
                        // can be retrieved in any @MessageMapping handler via
                        // SimpMessageHeaderAccessor.getSessionAttributes().get("username")
                        accessor.getSessionAttributes().put("username", username);
                        log.info("🔑 Stored guest username in session: {}", username);
                    }
                }

                // Always return the message (interceptors must not return null)
                return message;
            }
        });
    }
}