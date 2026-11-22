package ro.upt.exchange.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties("mcp")
public record McpProperties(String s2Url, String serviceApiKey, String webUrl, String reportRecipients,
                            String reportFrom) {

    public List<String> recipients() {
        return Arrays.stream(reportRecipients.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
