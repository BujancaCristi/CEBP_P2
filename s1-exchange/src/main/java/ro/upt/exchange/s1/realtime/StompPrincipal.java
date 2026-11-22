package ro.upt.exchange.s1.realtime;

import java.security.Principal;

/**
 * Principal representation for STOMP authenticated WebSocket sessions.
 * getName() returns the userId as string so that convertAndSendToUser(userId, "/queue/...", ...)
 * routes accurately to the targeted user.
 */
public record StompPrincipal(String name, Long userId) implements Principal {
    @Override
    public String getName() {
        return name;
    }
}
