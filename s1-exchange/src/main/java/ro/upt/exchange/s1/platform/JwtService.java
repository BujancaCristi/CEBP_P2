package ro.upt.exchange.s1.platform;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import ro.upt.exchange.s1.domain.Role;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(PlatformProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String issue(long userId, long accountId, String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("accountId", accountId)
                .claim("username", username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(12))))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
