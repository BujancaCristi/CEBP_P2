package ro.upt.exchange.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ro.upt.exchange.mcp.config.McpProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST client for S2 (Insights) endpoints. Uses the SERVICE_API_KEY header.
 */
@Service
public class S2Client {

    private static final Logger log = LoggerFactory.getLogger(S2Client.class);

    private final RestClient restClient;

    public S2Client(McpProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.s2Url())
                .defaultHeader("X-Api-Key", props.serviceApiKey())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public String getMarketSummary(String date) {
        log.info("Fetching market summary for date={}", date);
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/stats/daily");
                        if (date != null && !date.isBlank()) {
                            uriBuilder.queryParam("date", date);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to fetch market summary: {}", e.getMessage());
            return "[]";
        }
    }

    public String getIncidents(String since, String type) {
        log.info("Fetching incidents since={} type={}", since, type);
        try {
            if (type != null && !type.isBlank()) {
                return restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/api/incidents");
                            if (since != null && !since.isBlank()) uriBuilder.queryParam("since", since);
                            uriBuilder.queryParam("type", type);
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(String.class);
            } else {
                return restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/api/incidents/summary");
                            if (since != null && !since.isBlank()) uriBuilder.queryParam("since", since);
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(String.class);
            }
        } catch (Exception e) {
            log.error("Failed to fetch incidents: {}", e.getMessage());
            return "[]";
        }
    }

    public String getBotPerformance(String date, String symbol) {
        log.info("Fetching bot performance date={} symbol={}", date, symbol);
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/bots/stats");
                        if (date != null && !date.isBlank()) uriBuilder.queryParam("date", date);
                        if (symbol != null && !symbol.isBlank()) uriBuilder.queryParam("symbol", symbol);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to fetch bot performance: {}", e.getMessage());
            return "[]";
        }
    }

    public String getBotConfigs(String symbol) {
        log.info("Fetching bot configs symbol={}", symbol);
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/bots/configs");
                        if (symbol != null && !symbol.isBlank()) uriBuilder.queryParam("symbol", symbol);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to fetch bot configs: {}", e.getMessage());
            return "[]";
        }
    }

    public String getTrades(String symbol, String since, Integer limit) {
        log.info("Fetching trades symbol={} since={} limit={}", symbol, since, limit);
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/trades");
                        if (symbol != null && !symbol.isBlank()) uriBuilder.queryParam("symbol", symbol);
                        if (since != null && !since.isBlank()) uriBuilder.queryParam("since", since);
                        if (limit != null) uriBuilder.queryParam("limit", limit);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to fetch trades: {}", e.getMessage());
            return "[]";
        }
    }
}
