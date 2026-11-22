package ro.upt.exchange.mcp;

import jakarta.mail.internet.MimeMessage;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import ro.upt.exchange.mcp.ExchangeTools.Recommendation;

import java.util.List;

/** Turns the agent's markdown and recommendations into an HTML email and sends it over SMTP (Mailpit in Docker). */
@Component
public class ReportMailer {

    private final JavaMailSender mail;
    private final McpProperties props;
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();

    public ReportMailer(JavaMailSender mail, McpProperties props) {
        this.mail = mail;
        this.props = props;
    }

    public String send(String subject, String markdown, List<Recommendation> recommendations) {
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.reportFrom());
            helper.setTo(props.recipients().toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(markdown, render(subject, markdown, recommendations));
            mail.send(message);
            return message.getMessageID();
        } catch (Exception e) {
            throw new IllegalStateException("Could not send the report: " + e.getMessage(), e);
        }
    }

    String render(String subject, String markdown, List<Recommendation> recommendations) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,sans-serif;max-width:760px;color:#1b1f24\">")
            .append("<h1 style=\"font-size:20px\">").append(HtmlUtils.htmlEscape(subject)).append("</h1>")
            .append(renderer.render(parser.parse(markdown == null ? "" : markdown)));
        html.append("<h2 style=\"font-size:16px\">Recommendations</h2>");
        if (recommendations.isEmpty()) {
            html.append("<p>No setting changes recommended.</p>");
        } else {
            html.append("<table cellpadding=\"6\" style=\"border-collapse:collapse;border:1px solid #ccc\">")
                .append("<tr style=\"background:#f2f4f7\"><th align=\"left\">Stock</th><th align=\"left\">Current</th>")
                .append("<th align=\"left\">Proposed</th><th align=\"left\">Reason</th><th></th></tr>");
            for (Recommendation r : recommendations) {
                html.append("<tr><td>").append(esc(r.symbol())).append("</td><td>").append(esc(String.valueOf(r.current())))
                    .append("</td><td>").append(esc(String.valueOf(r.proposed()))).append("</td><td>").append(esc(r.reason()))
                    .append("</td><td><a href=\"").append(esc(r.applyUrl())).append("\">Apply</a></td></tr>");
            }
            html.append("</table><p style=\"color:#666;font-size:12px\">Nothing has been changed. Apply opens the "
                    + "bot settings page with the values filled in; the change happens only when an admin saves it.</p>");
        }
        return html.append("</body></html>").toString();
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
