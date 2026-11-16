package ro.upt.exchange.s2.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Two ways in:
 * <ul>
 *   <li>{@code Authorization: Bearer <JWT>} issued by S1 (HS256, same {@code JWT_SECRET}); claims
 *       {@code sub} = user id, {@code role} = TRADER | ADMIN | MARKET_MAKER.</li>
 *   <li>{@code X-Api-Key: <SERVICE_API_KEY>} for the MCP server: role SERVICE, allowed only on the
 *       read endpoints marked "MCP" in the application description.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final InsightsProperties props;

    public SecurityConfig(InsightsProperties props) {
        this.props = props;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new TokenFilter(props), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/agent/**").hasRole("ADMIN")
                .requestMatchers("/api/bots/**", "/api/incidents/**").hasAnyRole("ADMIN", "SERVICE")
                .requestMatchers("/api/trades/**").hasAnyRole("TRADER", "ADMIN", "MARKET_MAKER", "SERVICE")
                .requestMatchers("/api/**").hasAnyRole("TRADER", "ADMIN", "MARKET_MAKER")
                .anyRequest().denyAll());
        return http.build();
    }

    static final class TokenFilter extends OncePerRequestFilter {
        private final SecretKey key;
        private final String apiKey;

        TokenFilter(InsightsProperties props) {
            this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
            this.apiKey = props.serviceApiKey();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String auth = req.getHeader("Authorization");
            String serviceKey = req.getHeader("X-Api-Key");
            try {
                if (auth != null && auth.startsWith("Bearer ")) {
                    Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.substring(7)).getPayload();
                    String role = String.valueOf(c.get("role"));
                    var token = new UsernamePasswordAuthenticationToken(c.getSubject(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(token);
                } else if (serviceKey != null && serviceKey.equals(apiKey)) {
                    var token = new UsernamePasswordAuthenticationToken("mcp-server", null,
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
                    SecurityContextHolder.getContext().setAuthentication(token);
                }
            } catch (Exception ignored) {
                // invalid token -> anonymous -> 401/403 from the authorization rules
            }
            chain.doFilter(req, res);
        }
    }
}
