package com.virtualhuman.config;

import com.virtualhuman.controller.BehaviorWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BehaviorWebSocketHandler behaviorWebSocketHandler;

    public WebSocketConfig(BehaviorWebSocketHandler behaviorWebSocketHandler) {
        this.behaviorWebSocketHandler = behaviorWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Expose a raw WebSocket endpoint at ws://localhost:8080/ws/behavior
        // setAllowedOrigins("*") is critical for Unity WebGL
        registry.addHandler(behaviorWebSocketHandler, "/ws/behavior")
                .setAllowedOrigins("*");
    }
}
