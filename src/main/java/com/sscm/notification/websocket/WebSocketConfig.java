package com.sscm.notification.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /**
     * 메시지 브로커 설정.
     *
     * 현재: SimpleBroker (인메모리) — 단일 인스턴스에서만 동작.
     *
     * 다중 인스턴스(ECS Auto Scaling) 환경에서는 인스턴스 간 메시지 공유가 안 됨.
     * → A 인스턴스의 WebSocket 클라이언트가 B 인스턴스에서 발생한 알림을 못 받음.
     *
     * 해결 방안:
     * 1. STOMP Broker Relay (RabbitMQ/ActiveMQ) — Spring 공식 지원, 가장 안정적
     * 2. Redis Pub/Sub 커스텀 구현 — 기존 Redis 인프라 활용 가능하지만 직접 구현 필요
     * 3. Amazon MQ (관리형 RabbitMQ) — AWS 환경에 적합
     *
     * 현재는 ECS DesiredCount=1~3이고, WebSocket 사용 빈도가 낮아
     * SimpleBroker로 충분. 트래픽 증가 시 외부 브로커로 전환 예정.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue", "/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:5174")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
