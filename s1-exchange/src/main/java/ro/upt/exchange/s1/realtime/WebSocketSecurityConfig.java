package ro.upt.exchange.s1.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ensures HTTP handshake to /ws and /ws/** is permitted without CSRF block or premature 401,
 * delegating STOMP authentication to StompAuthChannelInterceptor on CONNECT frames.
 */
@Configuration
public class WebSocketSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 50)
    SecurityFilterChain wsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/ws", "/ws/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
