package ro.upt.exchange.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ro.upt.exchange.mcp.service.EmailService;
import ro.upt.exchange.mcp.service.S2Client;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class McpToolsTest {

    @Test
    @DisplayName("propose_bot_config_change generates valid recommendation and applyUrl without DB writes")
    void testProposeBotConfigChange() throws Exception {
        S2Client mockS2 = Mockito.mock(S2Client.class);
        EmailService mockEmail = Mockito.mock(EmailService.class);
        ObjectMapper mapper = new ObjectMapper();

        String existingConfigsJson = """
                [
                  {
                    "configId": 3,
                    "botUserId": 2,
                    "symbol": "ACME",
                    "enabled": true,
                    "spreadBps": 80,
                    "quoteQty": 50,
                    "maxInventory": 2000,
                    "skewFactor": 0.5,
                    "refreshMs": 2000,
                    "updatedAt": "2026-11-20T12:00:00Z"
                  }
                ]
                """;
        when(mockS2.getBotConfigs(any())).thenReturn(existingConfigsJson);

        ExchangeMcpTools tools = new ExchangeMcpTools(mockS2, mockEmail, mapper);

        String resultJson = tools.proposeBotConfigChange(
                3L,
                120, // widen spread by 1.5x
                null,
                null,
                null,
                null,
                null,
                "Bot lost money on stock that moved > 5%"
        );

        assertNotNull(resultJson);
        JsonNode root = mapper.readTree(resultJson);

        assertEquals(3, root.get("configId").asLong());
        assertEquals("ACME", root.get("symbol").asText());
        assertEquals(80, root.get("current").get("spreadBps").asInt());
        assertEquals(120, root.get("proposed").get("spreadBps").asInt());
        assertEquals("Bot lost money on stock that moved > 5%", root.get("reason").asText());

        String applyUrl = root.get("applyUrl").asText();
        assertTrue(applyUrl.contains("/admin/bots?"));
        assertTrue(applyUrl.contains("configId=3"));
        assertTrue(applyUrl.contains("spreadBps=120"));
    }
}
