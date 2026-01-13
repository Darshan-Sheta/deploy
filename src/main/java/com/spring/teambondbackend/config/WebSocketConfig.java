package com.spring.teambondbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        //on /chat endpoint connection will be established
        registry.addEndpoint("/api/v1/chat")
                .setAllowedOrigins(frontendUrl, frontendUrl + "/")
                .withSockJS();
        // Also add /chat endpoint for compatibility (if frontend uses different path)
        registry.addEndpoint("/chat")
                .setAllowedOrigins(frontendUrl, frontendUrl + "/")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        //client-side mapping endpoint
        config.enableSimpleBroker("/api/v1/topic");
        //server-side mapping endpoint
        config.setApplicationDestinationPrefixes("/app");
    }
}
