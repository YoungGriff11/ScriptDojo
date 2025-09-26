package org.scriptdojo.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;


@Configuration
@EnableWebSocket
public class WebSocketConfig {
    // Endpoint registration moved to WebSocketController
}
