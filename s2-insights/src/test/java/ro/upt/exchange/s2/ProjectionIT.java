package ro.upt.exchange.s2;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.upt.exchange.s2.projector.EventProjector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Replays the sample events from contracts/events/examples through the projector and reads them back over REST. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectionIT {

    @Container
    static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("insights").withUsername("insights").withPassword("insights");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    static final Path EXAMPLES = Path.of("..", "contracts", "events", "examples");
    static final String SECRET = "test-secret-test-secret-test-secret-test-secret";

    @Autowired EventProjector projector;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    static String jwt(String role) {
        return Jwts.builder().subject("7").claim("role", role).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private void replayExamples() throws Exception {
        // bot config first so the bot's trades are attributed; then everything else
        for (String name : List.of("BotConfigChanged", "OrderPlaced", "OrderCancelled", "OrderRejected", "OrderUpdated",
                "TradeExecuted", "PriceUpdated", "IncidentRaised", "IncidentUpdated")) {
            Path f = EXAMPLES.resolve(name + ".json");
            if (Files.exists(f)) projector.project("exchange.test", Files.readString(f));
        }
    }

    @Test
    void examplesBuildTheReadModelIdempotently() throws Exception {
        replayExamples();
        replayExamples(); // second delivery must change nothing

        Integer trades = jdbc.queryForObject("SELECT COUNT(*) FROM trades_view", Integer.class);
        assertThat(trades).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT volume FROM daily_stock_stats WHERE symbol='ACME'", Long.class)).isEqualTo(60L);
        assertThat(jdbc.queryForObject("SELECT trade_count FROM daily_stock_stats WHERE symbol='ACME'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bot_configs_view", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incidents_view", Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void restIsProtectedAndServesTheReadModel() throws Exception {
        replayExamples();
        mvc.perform(get("/api/stats/daily?date=2026-11-16")).andExpect(status().isForbidden());
        mvc.perform(get("/api/stats/daily?date=2026-11-16").header("Authorization", "Bearer " + jwt("TRADER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].symbol").value("ACME"));
        mvc.perform(get("/api/bots/configs").header("Authorization", "Bearer " + jwt("TRADER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bots/configs").header("X-Api-Key", "test-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].spreadBps").value(80));
        mvc.perform(get("/api/incidents/summary?since=2026-01-01T00:00:00Z").header("X-Api-Key", "test-key"))
                .andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/agent/run'].post").exists());
    }
}
