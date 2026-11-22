package ro.upt.exchange.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

/** Read-only HTTP client for S2's endpoints marked "MCP", authenticated with the service API key. */
@Component
public class S2Client {

    private final RestClient http;

    public S2Client(McpProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.s2Url())
                .defaultHeader("X-Api-Key", props.serviceApiKey())
                .build();
    }

    public JsonNode get(Function<UriBuilder, URI> uri) {
        return http.get().uri(uri).retrieve().body(JsonNode.class);
    }
}
