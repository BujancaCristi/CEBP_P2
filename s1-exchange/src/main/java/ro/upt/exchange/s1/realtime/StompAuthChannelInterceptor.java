package ro.upt.exchange.s1.realtime;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import ro.upt.exchange.s1.platform.JwtService;

import java.util.List;

/**
 * Intercepts incoming STOMP CONNECT frames to authenticate users via JWT or X-User-Id header.
 * Attaches StompPrincipal so that user-targeted destinations work seamlessly.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = getFirstHeader(accessor, "Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    Claims claims = jwtService.parse(token);
                    long userId = Long.parseLong(claims.getSubject());
                    accessor.setUser(new StompPrincipal(String.valueOf(userId), userId));
                    log.debug("STOMP CONNECT authenticated user: {}", userId);
                    return message;
                } catch (Exception e) {
                    log.warn("STOMP CONNECT invalid JWT: {}", e.getMessage());
                }
            }

            // Fallback for test / local environment headers
            String userIdHeader = getFirstHeader(accessor, "X-User-Id");
            if (userIdHeader != null) {
                try {
                    long userId = Long.parseLong(userIdHeader);
                    accessor.setUser(new StompPrincipal(String.valueOf(userId), userId));
                    log.debug("STOMP CONNECT authenticated via X-User-Id: {}", userId);
                    return message;
                } catch (NumberFormatException ignored) {
                }
            }

            // Allow anonymous connections for public topics like /topic/prices, /topic/book, etc.
            log.debug("STOMP CONNECT anonymous session");
        }
        return message;
    }

    private String getFirstHeader(StompHeaderAccessor accessor, String headerName) {
        List<String> values = accessor.getNativeHeader(headerName);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }
}
