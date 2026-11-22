package ro.upt.exchange.s1.realtime;

import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import ro.upt.exchange.s1.platform.JwtService;

import java.security.Principal;

/**
 * STOMP over WebSocket at {@code /ws} (owner: Paul). Topics are public to any connected client;
 * {@code /user/queue/*} needs a JWT on the CONNECT frame ({@code Authorization: Bearer ...}), and the
 * principal name is the user id, which is what {@link StompLivePublisher} sends to.
 */
@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwt;

    public StompConfig(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
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
                    if (header != null && header.startsWith("Bearer ")) {
                        try {
                            Claims claims = jwt.parse(header.substring(7));
                            String userId = claims.getSubject();
                            acc.setUser((Principal) () -> userId);
                        } catch (RuntimeException ignored) {
                            // anonymous connection: topics only, no user queues
                        }
                    }
                }
                return message;
            }
        });
    }
}
