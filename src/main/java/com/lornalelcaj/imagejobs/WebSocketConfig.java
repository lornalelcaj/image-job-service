package com.lornalelcaj.imagejobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomSocketHandler roomSocketHandler;

    public WebSocketConfig(RoomSocketHandler roomSocketHandler) {
        this.roomSocketHandler = roomSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Only pages from the same origin may connect (Spring's default).
        registry.addHandler(roomSocketHandler, "/ws/rooms");
    }
}
