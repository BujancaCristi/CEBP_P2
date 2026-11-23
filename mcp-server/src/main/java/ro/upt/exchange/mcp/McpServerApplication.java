package ro.upt.exchange.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ro.upt.exchange.mcp.config.McpProperties;
import ro.upt.exchange.mcp.tools.ExchangeMcpTools;

@SpringBootApplication
@EnableConfigurationProperties(McpProperties.class)
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    /**
     * Spring AI's MCP server (the /sse transport the S2 agent and MCP Inspector use) only publishes tools
     * registered through a ToolCallbackProvider; @Tool methods on a component are not picked up on their own.
     * The bean name must differ from the ExchangeMcpTools component's name.
     */
    @Bean
    ToolCallbackProvider exchangeToolCallbacks(ExchangeMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
