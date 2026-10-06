package com.trading.marketdataservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // Topics prefixed with /topic -> Broadcast to all the subscribers
        registry.enableSimpleBroker("/topic");
        // Application destination prefix -> Client messages to /app/** are routed to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // Websocket endpoint -> clients connect here
        // SockJS fallback for browsers that don't support WebSocket
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();

    }

}
