package ro.upt.exchange.s1.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("exchange.platform")
public record PlatformProperties(String jwtSecret, BigDecimal startingCash) {
    public PlatformProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("exchange.platform.jwt-secret must contain at least 32 characters");
        }
        if (startingCash == null || startingCash.signum() < 0) {
            throw new IllegalArgumentException("exchange.platform.starting-cash must be non-negative");
        }
    }
}
