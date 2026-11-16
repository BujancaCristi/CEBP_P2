package ro.upt.exchange.s1.platform;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import ro.upt.exchange.s1.domain.AccountRepository;
import ro.upt.exchange.s1.domain.Role;
import ro.upt.exchange.s1.domain.UserRepository;

import java.io.IOException;
import java.util.List;

/**
 * STUB - owned by Cezar, delete when the real JWT {@code SecurityConfig} lands.
 *
 * <p>Active only under the {@code local} and {@code test} profiles. Authenticates a request from
 * the header {@code X-User-Id} (the user's row id) and puts an {@link AuthenticatedUser} principal
 * in the security context, which is exactly what Cezar's JWT filter will do. Requests without the
 * header are anonymous (Swagger UI, actuator and {@code /api/auth/**} stay reachable).
 */
@Configuration
@Profile({"local", "test"})
public class StubSecurityConfig {

    @Bean
    SecurityFilterChain stubFilterChain(HttpSecurity http, UserRepository users, AccountRepository accounts) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(new HeaderAuthFilter(users, accounts), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static final class HeaderAuthFilter extends OncePerRequestFilter {
        private final UserRepository users;
        private final AccountRepository accounts;

        HeaderAuthFilter(UserRepository users, AccountRepository accounts) {
            this.users = users;
            this.accounts = accounts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String header = req.getHeader("X-User-Id");
            if (header != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    long userId = Long.parseLong(header);
                    users.findById(userId).ifPresent(u -> {
                        long accountId = accounts.findByUserId(userId).map(a -> a.getId()).orElse(-1L);
                        Role role = u.getRole();
                        var principal = new AuthenticatedUser(userId, accountId, u.getUsername(), role);
                        var auth = new UsernamePasswordAuthenticationToken(principal, "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
                } catch (NumberFormatException ignored) {
                    // not a stub header we understand; stay anonymous
                }
            }
            chain.doFilter(req, res);
        }
    }
}
