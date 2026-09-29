package com.nihongo.staff.service.monitor.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceSocketAuthorizationTest {
    final PerformanceSocketAuthorization guard = new PerformanceSocketAuthorization();
    JwtAuthenticationToken auth(String role, String type, Instant expiry) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS256").subject("staff")
                .claim("type", type).issuedAt(Instant.now().minusSeconds(60)).expiresAt(expiry).build(), List.of(new SimpleGrantedAuthority(role)));
    }
    void send(StompCommand command, String destination, JwtAuthenticationToken user) {
        var headers = StompHeaderAccessor.create(command); headers.setUser(user); headers.setDestination(destination);
        guard.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }
    @Test void onlyStaffAndAdminCanSubscribeToExactPerformanceTopics() {
        var user = auth("ROLE_STAFF", "access", Instant.now().plusSeconds(60));
        assertDoesNotThrow(() -> send(StompCommand.SUBSCRIBE, "/topic/vps-performance/1/CPU_USAGE", user));
        assertDoesNotThrow(() -> send(StompCommand.SUBSCRIBE, "/topic/vps-performance/2/LOAD_1M", auth("ROLE_ADMIN", "access", Instant.now().plusSeconds(60))));
        for (String topic : List.of("/topic/vps-performance/*/CPU_USAGE", "/topic/wallet-admin", "/topic/vps-performance/0/CPU_USAGE"))
            assertThrows(AccessDeniedException.class, () -> send(StompCommand.SUBSCRIBE, topic, user));
        assertThrows(AccessDeniedException.class, () -> send(StompCommand.SEND, "/topic/vps-performance/1/CPU_USAGE", user));
        assertThrows(AccessDeniedException.class, () -> send(StompCommand.SUBSCRIBE, "/topic/vps-performance/1/CPU_USAGE", auth("ROLE_USER", "access", Instant.now().plusSeconds(60))));
    }
    @Test void expiredAndRefreshTokensCannotUseSocket() {
        assertThrows(AccessDeniedException.class, () -> send(StompCommand.CONNECT, null, auth("ROLE_STAFF", "access", Instant.now().minusSeconds(1))));
        assertThrows(AccessDeniedException.class, () -> send(StompCommand.CONNECT, null, auth("ROLE_STAFF", "refresh", Instant.now().plusSeconds(60))));
        assertThrows(AccessDeniedException.class, () -> send(StompCommand.CONNECT, null, null));
    }
}
