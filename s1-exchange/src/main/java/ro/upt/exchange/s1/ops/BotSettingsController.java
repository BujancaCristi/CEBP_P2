package ro.upt.exchange.s1.ops;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ro.upt.exchange.s1.config.ExchangeTopics;
import ro.upt.exchange.s1.domain.BotConfig;
import ro.upt.exchange.s1.domain.BotConfigRepository;
import ro.upt.exchange.s1.domain.Role;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.domain.StockRepository;
import ro.upt.exchange.s1.events.BotConfigChangedPayload;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.platform.EventOutbox;
import ro.upt.exchange.s1.realtime.LivePublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Controller for Bot configurations. <b>Owner: Paul.</b>
 * Provides endpoints for market makers to retrieve their configs and admins to view/update them.
 */
@RestController
public class BotSettingsController {

    private final BotConfigRepository botConfigRepository;
    private final StockRepository stockRepository;
    private final EventOutbox outbox;
    private final LivePublisher livePublisher;
    private final ExchangeTopics topics;

    public BotSettingsController(BotConfigRepository botConfigRepository,
                                 StockRepository stockRepository,
                                 EventOutbox outbox,
                                 LivePublisher livePublisher,
                                 ExchangeTopics topics) {
        this.botConfigRepository = botConfigRepository;
        this.stockRepository = stockRepository;
        this.outbox = outbox;
        this.livePublisher = livePublisher;
        this.topics = topics;
    }

    @GetMapping("/api/bots/me/configs")
    public List<BotConfigDto> getMyConfigs(AuthenticatedUser user) {
        if (!user.is(Role.MARKET_MAKER) && !user.is(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only MARKET_MAKER or ADMIN can view bot configs");
        }
        Map<Long, String> stocksById = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol));

        return botConfigRepository.findByBotUserIdOrderByStockId(user.userId()).stream()
                .map(cfg -> BotConfigDto.from(cfg, stocksById.getOrDefault(cfg.getStockId(), "UNKNOWN")))
                .toList();
    }

    @GetMapping("/api/admin/bots")
    public List<BotConfigDto> listAdminConfigs(AuthenticatedUser user) {
        if (!user.is(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can list all bot configs");
        }
        Map<Long, String> stocksById = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol));

        return botConfigRepository.findAll().stream()
                .map(cfg -> BotConfigDto.from(cfg, stocksById.getOrDefault(cfg.getStockId(), "UNKNOWN")))
                .toList();
    }

    @PutMapping("/api/admin/bots/{id}")
    @Transactional
    public BotConfigDto updateConfig(@PathVariable("id") long id,
                                     @Valid @RequestBody UpdateBotConfigRequest req,
                                     AuthenticatedUser user) {
        if (!user.is(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can update bot configs");
        }
        BotConfig config = botConfigRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot config not found: " + id));

        if (req.enabled() != null) {
            config.setEnabled(req.enabled());
        }
        if (req.spreadBps() != null) {
            config.setSpreadBps(req.spreadBps());
        }
        if (req.quoteQty() != null) {
            config.setQuoteQty(req.quoteQty());
        }
        if (req.maxInventory() != null) {
            config.setMaxInventory(req.maxInventory());
        }
        if (req.skewFactor() != null) {
            config.setSkewFactor(req.skewFactor());
        }
        if (req.refreshMs() != null) {
            config.setRefreshMs(req.refreshMs());
        }

        config.setUpdatedBy(user.username());
        config.setUpdatedAt(Instant.now());
        botConfigRepository.save(config);

        String symbol = stockRepository.findById(config.getStockId())
                .map(Stock::getSymbol)
                .orElse("UNKNOWN");

        BotConfigChangedPayload payload = new BotConfigChangedPayload(
                config.getId(),
                config.getBotUserId(),
                symbol,
                config.isEnabled(),
                config.getSpreadBps(),
                config.getQuoteQty(),
                config.getMaxInventory(),
                config.getSkewFactor(),
                config.getRefreshMs(),
                config.getUpdatedBy(),
                config.getUpdatedAt()
        );

        outbox.add(topics.bots(), symbol, EventTypes.BOT_CONFIG_CHANGED, payload);

        afterCommit(() -> livePublisher.botConfigChanged(payload));

        return BotConfigDto.from(config, symbol);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception ignored) {
                    }
                }
            });
        } else {
            action.run();
        }
    }
}
