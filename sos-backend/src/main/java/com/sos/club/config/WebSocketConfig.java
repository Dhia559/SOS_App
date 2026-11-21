package com.sos.club.config;

import com.sos.club.security.AuthUser;
import com.sos.club.security.JwtAuthFilter;
import com.sos.club.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
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
 * STOMP sur WebSocket.
 *  - connexion : ws://host:8080/ws  (ou SockJS sur /ws-sockjs)
 *  - header CONNECT : Authorization: Bearer <jwt>
 *  - s'abonner a : /user/queue/notifications, /user/queue/messages, /user/queue/errors
 *  - envoyer un message : /app/chat.send  { "conversationId": 1, "content": "salut" }
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwt;

    public WebSocketConfig(JwtService jwt) { this.jwt = jwt; }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws-sockjs").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc != null && StompCommand.CONNECT.equals(acc.getCommand())) {
                    String header = acc.getFirstNativeHeader("Authorization");
                    String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : header;
                    AuthUser user = token == null ? null : jwt.parse(token).orElse(null);
                    if (user == null) throw new MessagingException("Token manquant ou invalide");
                    acc.setUser(JwtAuthFilter.toAuthentication(user));
                }
                return message;
            }
        });
    }
}
