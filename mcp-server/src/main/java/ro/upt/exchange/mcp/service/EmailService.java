package ro.upt.exchange.mcp.service;

import jakarta.mail.internet.MimeMessage;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import ro.upt.exchange.mcp.config.McpProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service to format and send HTML report emails with recommendation action links.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final McpProperties props;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public EmailService(JavaMailSender mailSender, McpProperties props) {
        this.mailSender = mailSender;
        this.props = props;
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
    }

    public String sendReport(String subject, String markdown, List<Map<String, Object>> recommendations) {
        String messageId = "msg-" + UUID.randomUUID();
        log.info("Sending report email '{}' (id: {}) to {}", subject, messageId, props.reportRecipients());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("noreply@exchange.local", "Stock Exchange AI Analyst");
            String[] recipients = props.reportRecipients().split(",");
            for (int i = 0; i < recipients.length; i++) {
                recipients[i] = recipients[i].trim();
            }
            helper.setTo(recipients);
            helper.setSubject(subject);

            String htmlBody = buildHtmlBody(subject, markdown, recommendations);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Report email delivered successfully: {}", messageId);
            return messageId;
        } catch (Exception e) {
            log.error("Failed to send report email: {}", e.getMessage(), e);
            throw new RuntimeException("Email delivery failed: " + e.getMessage(), e);
        }
    }

    private String buildHtmlBody(String subject, String markdown, List<Map<String, Object>> recommendations) {
        Node document = markdownParser.parse(markdown != null ? markdown : "");
        String renderedMarkdown = htmlRenderer.render(document);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 20px; background-color: #f8f9fa; }");
        html.append(".container { max-width: 800px; margin: 0 auto; background: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        html.append("h1 { color: #1c7ed6; font-size: 24px; border-bottom: 2px solid #e9ecef; padding-bottom: 10px; margin-top: 0; }");
        html.append("h2 { color: #212529; font-size: 20px; margin-top: 25px; }");
        html.append(".report-content { margin-bottom: 30px; }");
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 15px; }");
        html.append("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; }");
        html.append("th { background-color: #f1f3f5; font-weight: 600; color: #495057; }");
        html.append(".btn-apply { display: inline-block; background-color: #228be6; color: white !important; padding: 6px 14px; text-decoration: none; border-radius: 4px; font-weight: 600; font-size: 13px; }");
        html.append(".btn-apply:hover { background-color: #1c7ed6; }");
        html.append(".footer { margin-top: 30px; padding-top: 15px; border-top: 1px solid #e9ecef; font-size: 12px; color: #868e96; text-align: center; }");
        html.append("</style></head><body>");

        html.append("<div class='container'>");
        html.append("<h1>").append(escapeHtml(subject)).append("</h1>");
        html.append("<div class='report-content'>").append(renderedMarkdown).append("</div>");

        if (recommendations != null && !recommendations.isEmpty()) {
            html.append("<h2>Recommended Bot Configuration Changes</h2>");
            html.append("<p>Click 'Apply' to review and save the proposed changes in the exchange administration:</p>");
            html.append("<table>");
            html.append("<thead><tr><th>Symbol</th><th>Reason</th><th>Proposed Changes</th><th>Action</th></tr></thead>");
            html.append("<tbody>");

            String baseWebUrl = props.webUrl() != null ? props.webUrl() : "http://localhost";
            for (Map<String, Object> rec : recommendations) {
                String symbol = String.valueOf(rec.getOrDefault("symbol", "-"));
                String reason = String.valueOf(rec.getOrDefault("reason", "-"));
                Object proposed = rec.get("proposed");
                String applyUrl = String.valueOf(rec.getOrDefault("applyUrl", ""));

                String fullUrl = applyUrl.startsWith("http") ? applyUrl : baseWebUrl + applyUrl;

                html.append("<tr>");
                html.append("<td><strong>").append(escapeHtml(symbol)).append("</strong></td>");
                html.append("<td>").append(escapeHtml(reason)).append("</td>");
                html.append("<td><code>").append(escapeHtml(String.valueOf(proposed))).append("</code></td>");
                html.append("<td><a href='").append(escapeHtml(fullUrl)).append("' class='btn-apply' target='_blank'>Apply</a></td>");
                html.append("</tr>");
            }

            html.append("</tbody></table>");
        }

        html.append("<div class='footer'>");
        html.append("Automated Stock Exchange Nightly Analysis &bull; Generated via Spring AI &amp; Gemini");
        html.append("</div>");

        html.append("</div></body></html>");
        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
