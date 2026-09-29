package com.nihongo.staff.config;

import com.nihongo.staff.service.monitor.realtime.PerformanceSocketAuthorization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import java.util.Map;
import java.util.concurrent.*;

@Configuration @EnableWebSocketMessageBroker
public class PerformanceWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final PerformanceSocketAuthorization authorization;
    private final String[] origins;
    public PerformanceWebSocketConfig(PerformanceSocketAuthorization authorization,
            @Value("${monitoring.websocket.allowed-origins:http://localhost:5173}") String[] origins) {
        this.authorization = authorization; this.origins = origins;
    }
    @Bean public ThreadPoolTaskScheduler performanceSocketScheduler() {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("perf-socket-"); scheduler.setRemoveOnCancelPolicy(true); return scheduler;
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/staff/vps-performance/ws").setAllowedOrigins(origins);
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic").setTaskScheduler(performanceSocketScheduler()).setHeartbeatValue(new long[]{10000, 10000});
        registry.setPreservePublishOrder(true);
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(authorization); }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(8192).setSendBufferSizeLimit(1024 * 1024).setSendTimeLimit(10000);
        registry.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            private final Map<String, ScheduledFuture<?>> expiry = new ConcurrentHashMap<>();
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                if (!(session.getPrincipal() instanceof JwtAuthenticationToken auth) || !PerformanceSocketAuthorization.allowed(auth)) {
                    session.close(CloseStatus.POLICY_VIOLATION); return;
                }
                super.afterConnectionEstablished(session);
                expiry.put(session.getId(), performanceSocketScheduler().schedule(() -> {
                    try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { }
                }, auth.getToken().getExpiresAt()));
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                var task = expiry.remove(session.getId()); if (task != null) task.cancel(false);
                super.afterConnectionClosed(session, status);
            }
        });
    }
}
