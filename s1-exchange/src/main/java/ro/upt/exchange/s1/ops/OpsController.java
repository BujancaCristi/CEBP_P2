package ro.upt.exchange.s1.ops;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ro.upt.exchange.s1.domain.*;
import ro.upt.exchange.s1.events.IncidentEventPayload;
import ro.upt.exchange.s1.ops.BotConfigService.BotConfigView;
import ro.upt.exchange.s1.platform.AuthenticatedUser;

import java.util.List;
import java.util.Map;

/** Bot settings and incidents (contracts/s1/ops.yaml). Owner: Paul. */
@RestController
@RequestMapping("/api")
public class OpsController {

    private final BotConfigService bots;
    private final IncidentServiceImpl incidentService;
    private final IncidentRepository incidents;
    private final StockRepository stocks;

    public OpsController(BotConfigService bots, IncidentServiceImpl incidentService, IncidentRepository incidents,
                         StockRepository stocks) {
        this.bots = bots;
        this.incidentService = incidentService;
        this.incidents = incidents;
        this.stocks = stocks;
    }

    // ---- the bot's own settings -------------------------------------------------------------

    @Tag(name = "Bots")
    @Operation(operationId = "myBotConfigs", summary = "The calling market maker's settings (MARKET_MAKER)")
    @GetMapping("/bots/me/configs")
    public List<BotConfigView> myConfigs(AuthenticatedUser user) {
        requireRole(user, Role.MARKET_MAKER);
        return bots.forBot(user.userId());
    }

    @Tag(name = "Bots")
    @Operation(operationId = "reportInventoryLimit",
            summary = "The bot reports that it reached max inventory on a stock (MARKET_MAKER); records BOT_INVENTORY_LIMIT")
    @PostMapping("/bots/me/inventory-limit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Long> inventoryLimit(AuthenticatedUser user, @RequestParam String symbol,
                                            @RequestParam long inventory, @RequestParam long maxInventory) {
        requireRole(user, Role.MARKET_MAKER);
        Stock stock = stocks.findBySymbol(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown symbol " + symbol));
        long id = incidentService.raise(IncidentType.BOT_INVENTORY_LIMIT, IncidentSeverity.MEDIUM, stock.getId(), null,
                user.userId(), "Market maker reached max inventory on " + symbol + " (" + inventory + " / "
                        + maxInventory + "); BUY quotes paused",
                Map.of("inventory", inventory, "maxInventory", maxInventory));
        return Map.of("incidentId", id);
    }

    // ---- admin: bot settings ----------------------------------------------------------------

    @Tag(name = "Admin")
    @Operation(operationId = "adminBots", summary = "All bot settings (ADMIN)")
    @GetMapping("/admin/bots")
    public List<BotConfigView> allBots(AuthenticatedUser user) {
        requireRole(user, Role.ADMIN);
        return bots.all();
    }

    @Tag(name = "Admin")
    @Operation(operationId = "adminBot", summary = "One bot setting (ADMIN)")
    @GetMapping("/admin/bots/{id}")
    public BotConfigView oneBot(AuthenticatedUser user, @PathVariable long id) {
        requireRole(user, Role.ADMIN);
        return bots.one(id);
    }

    @Tag(name = "Admin")
    @Operation(operationId = "updateBot", summary = "Change bot settings (ADMIN); publishes BotConfigChanged")
    @PutMapping("/admin/bots/{id}")
    public BotConfigView updateBot(AuthenticatedUser user, @PathVariable long id,
                                   @RequestBody BotConfigService.UpdateRequest body) {
        requireRole(user, Role.ADMIN);
        return bots.update(id, body, user.username());
    }

    // ---- admin: incidents -------------------------------------------------------------------

    public record StatusChange(IncidentStatus status) {}

    @Tag(name = "Admin")
    @Operation(operationId = "adminIncidents", summary = "Incidents, newest first, optional status/type filter (ADMIN)")
    @GetMapping("/admin/incidents")
    public List<IncidentEventPayload> listIncidents(AuthenticatedUser user,
                                                    @RequestParam(required = false) IncidentStatus status,
                                                    @RequestParam(required = false) IncidentType type,
                                                    @RequestParam(defaultValue = "200") int limit) {
        requireRole(user, Role.ADMIN);
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 1000));
        var rows = status != null ? incidents.findByStatusOrderByCreatedAtDesc(status, page)
                : type != null ? incidents.findByTypeOrderByCreatedAtDesc(type, page)
                : incidents.findAllByOrderByCreatedAtDesc(page);
        return rows.stream()
                .filter(i -> type == null || i.getType() == type)
                .map(incidentService::toPayload).toList();
    }

    @Tag(name = "Admin")
    @Operation(operationId = "changeIncident", summary = "Acknowledge or resolve an incident (ADMIN); publishes IncidentUpdated")
    @PatchMapping("/admin/incidents/{id}")
    public IncidentEventPayload changeIncident(AuthenticatedUser user, @PathVariable long id,
                                               @RequestBody StatusChange body) {
        requireRole(user, Role.ADMIN);
        if (body.status() == null) throw new IllegalArgumentException("status is required");
        return incidentService.toPayload(incidentService.changeStatus(id, body.status()));
    }

    // ---- errors -----------------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().endsWith("not found")
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setProperty("code", status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "VALIDATION");
        return pd;
    }

    private static void requireRole(AuthenticatedUser user, Role role) {
        if (user == null || user.role() != role) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires role " + role);
        }
    }
}
