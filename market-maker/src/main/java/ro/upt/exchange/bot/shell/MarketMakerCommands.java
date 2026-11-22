package ro.upt.exchange.bot.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import ro.upt.exchange.bot.service.MarketMakerService;

import java.util.Map;

/**
 * Spring Shell interactive commands for the Market Maker bot.
 * <b>Owner: Paul.</b>
 * Commands: start, stop, status, config.
 */
@ShellComponent
public class MarketMakerCommands {

    private final MarketMakerService service;

    public MarketMakerCommands(MarketMakerService service) {
        this.service = service;
    }

    @ShellMethod(key = "start", value = "Start the market maker quoting loop")
    public String start() {
        boolean started = service.start();
        return started ? "Market Maker quoting loop started." : "Failed to start Market Maker.";
    }

    @ShellMethod(key = "stop", value = "Stop the market maker quoting loop and cancel quotes")
    public String stop() {
        service.stop();
        return "Market Maker quoting loop stopped and quotes cancelled.";
    }

    @ShellMethod(key = "status", value = "Display current status, active stocks, and inventory")
    public String status() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Market Maker Status ===\n");
        sb.append("Running: ").append(service.isRunning() ? "YES" : "NO").append("\n");

        Map<String, Map<String, Object>> configs = service.getConfigs();
        sb.append("Configured Stocks: ").append(configs.size()).append("\n");

        Map<String, Long> inventory = service.getInventory();
        for (Map.Entry<String, Map<String, Object>> entry : configs.entrySet()) {
            String sym = entry.getKey();
            Map<String, Object> cfg = entry.getValue();
            boolean enabled = Boolean.TRUE.equals(cfg.get("enabled"));
            long inv = inventory.getOrDefault(sym, 0L);
            sb.append(String.format(" - %-6s [Enabled: %-5s] Spread: %4s bps, QuoteQty: %4s, MaxInv: %5s, Holdings: %d\n",
                    sym, enabled, cfg.get("spreadBps"), cfg.get("quoteQty"), cfg.get("maxInventory"), inv));
        }

        return sb.toString();
    }

    @ShellMethod(key = "config", value = "Display or reload bot configurations")
    public String config() {
        service.reloadConfigs();
        return "Reloaded bot configurations from S1:\n" + status();
    }
}
