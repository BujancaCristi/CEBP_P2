package ro.upt.exchange.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/** MCP server: tools for the nightly report agent. Reads go to S2; the only action is an email. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    ToolCallbackProvider exchangeToolCallbacks(ExchangeTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
