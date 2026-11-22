package ro.upt.exchange.s1.platform;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ro.upt.exchange.s1.domain.*;

import java.math.BigDecimal;

@Service
public class AuthService {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final PlatformProperties properties;
    private final JwtService jwt;

    public AuthService(UserRepository users, AccountRepository accounts, PasswordEncoder passwords,
                       PlatformProperties properties, JwtService jwt) {
        this.users = users;
        this.accounts = accounts;
        this.passwords = passwords;
        this.properties = properties;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(String username, String email, String password) {
        if (users.existsByUsername(username) || users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already registered");
        }
        User user = users.save(new User(username, email, passwords.encode(password), Role.TRADER));
        Account account = accounts.save(new Account(user.getId(), properties.startingCash()));
        return response(user, account);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String password) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Account account = accounts.findByUserId(user.getId()).orElseThrow();
        return response(user, account);
    }

    private AuthResponse response(User user, Account account) {
        return new AuthResponse(jwt.issue(user.getId(), account.getId(), user.getUsername(), user.getRole()),
                new UserSummary(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name()),
                account.getId());
    }

    public record AuthResponse(String token, UserSummary user, long accountId) {}
    public record UserSummary(long id, String username, String email, String role) {}
    public record LoginRequest(String username, String password) {}
    public record RegisterRequest(String username, String email, String password) {}
}
