package ro.upt.exchange.s2.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.upt.exchange.s2.agent.ReportAgent;
import ro.upt.exchange.s2.agent.ReportAgent.ReportResult;
import ro.upt.exchange.s2.config.InsightsProperties;

import java.time.LocalDate;

@RestController
@Tag(name = "Agent")
public class AgentController {

    private final ReportAgent agent;
    private final InsightsProperties props;

    public AgentController(ReportAgent agent, InsightsProperties props) {
        this.agent = agent;
        this.props = props;
    }

    @Operation(operationId = "runReport", summary = "Run the nightly report now (ADMIN). Emails it and returns it; nothing is stored.")
    @PostMapping("/api/agent/run")
    public ReportResult run(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return agent.run(date != null ? date : LocalDate.now(props.zoneId()).minusDays(1));
    }
}
