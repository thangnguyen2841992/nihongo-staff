package com.nihongo.staff.service.monitor.realtime;

import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class PerformanceSocketAuthorization implements ChannelInterceptor {
    public static boolean allowed(JwtAuthenticationToken auth) {
        return auth.isAuthenticated() && "access".equals(auth.getToken().getClaimAsString("type"))
                && auth.getToken().getExpiresAt() != null && auth.getToken().getExpiresAt().isAfter(Instant.now())
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw new AccessDeniedException("Missing STOMP headers");
        var command = headers.getCommand();
        if (command == StompCommand.DISCONNECT) return message;
        if (!(headers.getUser() instanceof JwtAuthenticationToken auth) || !allowed(auth))
            throw new AccessDeniedException("Staff authentication required");
        if (command == StompCommand.SUBSCRIBE) {
            String destination = headers.getDestination();
            if (destination == null || !destination.matches("/topic/vps-performance/[1-9][0-9]*/[A-Z][A-Z0-9_]{0,63}"))
                throw new AccessDeniedException("Subscription forbidden");
        } else if (command != null && command != StompCommand.CONNECT && command != StompCommand.STOMP && command != StompCommand.UNSUBSCRIBE)
            throw new AccessDeniedException("Client publishing forbidden");
        return message;
    }
}
