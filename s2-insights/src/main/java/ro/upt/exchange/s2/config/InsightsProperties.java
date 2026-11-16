package ro.upt.exchange.s2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.List;

@ConfigurationProperties(prefix = "insights")
public record InsightsProperties(String jwtSecret, String serviceApiKey, List<String> topics,
                                 String zone, Agent agent) {
    public record Agent(int maxToolCalls, String cron) {}

    public ZoneId zoneId() {
        return ZoneId.of(zone == null ? "UTC" : zone);
    }
}
