package ro.upt.exchange.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ro.upt.exchange.mcp.ExchangeTools.Recommendation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeToolsTest {

    final ObjectMapper mapper = new ObjectMapper();
    final McpProperties props = new McpProperties("http://s2", "k", "http://localhost/", "a@x.ro", "n@x.ro");
    final ExchangeTools tools = new ExchangeTools(null, props, null, mapper);

    JsonNode config() throws Exception {
        return mapper.readTree("""
                {"configId":3,"symbol":"ACME","enabled":true,"spreadBps":50,"quoteQty":20,"maxInventory":1000}""");
    }

    @Test
    void proposalCarriesCurrentProposedAndAnApplyLink() throws Exception {
        Recommendation r = tools.buildRecommendation(config(), 75, null, null, null, "lost money on a 6% move");
        assertThat(r.configId()).isEqualTo(3);
        assertThat(r.current()).containsEntry("spreadBps", 50);
        assertThat(r.proposed()).containsEntry("spreadBps", 75);
        assertThat(r.applyUrl()).startsWith("http://localhost/admin/bots/3?spreadBps=75&reason=");
    }

    @Test
    void invalidProposalsAreRefused() throws Exception {
        assertThatThrownBy(() -> tools.buildRecommendation(config(), 5000, null, null, null, "x"))
                .hasMessageContaining("spreadBps");
        assertThatThrownBy(() -> tools.buildRecommendation(config(), null, 2000L, null, null, "x"))
                .hasMessageContaining("quoteQty cannot exceed");
        assertThatThrownBy(() -> tools.buildRecommendation(config(), null, null, null, null, "x"))
                .hasMessageContaining("Nothing to change");
    }

    @Test
    void emailRendersMarkdownAndEscapesHtml() throws Exception {
        ReportMailer mailer = new ReportMailer(null, props);
        Recommendation r = tools.buildRecommendation(config(), 75, null, null, null, "spread <too> tight");
        String html = mailer.render("Report", "## Overview\n**ACME** moved 6%", List.of(r));
        assertThat(html).contains("<h2>Overview</h2>").contains("<strong>ACME</strong>")
                .contains("spread &lt;too&gt; tight").contains(">Apply</a>");
    }
}
