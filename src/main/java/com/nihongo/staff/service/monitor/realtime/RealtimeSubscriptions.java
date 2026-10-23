package com.nihongo.staff.service.monitor.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/** Answers whether any local STOMP session listens on a destination, so nobody pays for frames nobody receives. */
@Component @RequiredArgsConstructor
public class RealtimeSubscriptions {
    private final SimpUserRegistry users;

    public boolean hasSubscribers(String destination) {
        for (SimpUser user : users.getUsers())
            for (SimpSession session : user.getSessions())
                for (SimpSubscription subscription : session.getSubscriptions())
                    if (destination.equals(subscription.getDestination())) return true;
        return false;
    }
}
