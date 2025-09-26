package org.scriptdojo.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

@Controller
public class WebSocketController {

    @Autowired
    private WebSocketMessageHandler webSocketMessageHandler;

    @Bean
    public WebSocketHttpRequestHandler webSocketHttpRequestHandler() {
        return new WebSocketHttpRequestHandler(webSocketMessageHandler);
    }

}
