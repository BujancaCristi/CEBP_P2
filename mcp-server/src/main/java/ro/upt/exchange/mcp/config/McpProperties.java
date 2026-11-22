package ro.upt.exchange.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp")
public record McpProperties(
        String s2Url,
        String serviceApiKey,
        String reportRecipients,
        String webUrl
) {
}
