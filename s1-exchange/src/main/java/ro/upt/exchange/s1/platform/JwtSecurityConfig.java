package ro.upt.exchange.s1.platform;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import ro.upt.exchange.s1.domain.AccountRepository;
import ro.upt.exchange.s1.domain.Role;
import ro.upt.exchange.s1.domain.UserRepository;

import java.io.IOException;
import java.util.List;

@Configuration
@Profile("!local & !test")
public class JwtSecurityConfig {
    @Bean
    SecurityFilterChain jwtFilterChain(HttpSecurity http, JwtFilter filter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtFilter jwtFilter(JwtService jwt, UserRepository users, AccountRepository accounts) {
        return new JwtFilter(jwt, users, accounts);
    }

    static final class JwtFilter extends OncePerRequestFilter {
        private final JwtService jwt;
        private final UserRepository users;
        private final AccountRepository accounts;

        JwtFilter(JwtService jwt, UserRepository users, AccountRepository accounts) {
            this.jwt = jwt;
            this.users = users;
            this.accounts = accounts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    Claims claims = jwt.parse(header.substring(7));
                    long userId = Long.parseLong(claims.getSubject());
                    users.findById(userId).flatMap(u -> accounts.findByUserId(userId).map(a ->
                            new AuthenticatedUser(u.getId(), a.getId(), u.getUsername(), u.getRole())))
                            .ifPresent(user -> {
                                var auth = new UsernamePasswordAuthenticationToken(user, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                                org.springframework.security.core.context.SecurityContextHolder.getContext()
                                        .setAuthentication(auth);
                            });
                } catch (JwtException | NumberFormatException ignored) {
                    // Invalid credentials remain anonymous and are rejected by the security chain.
                }
            }
            chain.doFilter(request, response);
        }
    }
}
