package ro.upt.exchange.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-maker")
public record BotProperties(
        String s1Url,
        String username,
        String password,
        boolean autostart,
        long connectRetryIntervalMs
) {
}
