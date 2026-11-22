package ro.upt.exchange.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ro.upt.exchange.mcp.config.McpProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpProperties.class)
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
