package ro.upt.exchange.s1.trading.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.upt.exchange.s1.domain.TradeRepository;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.trading.dto.PageResponse;
import ro.upt.exchange.s1.trading.dto.TradeResponse;
import ro.upt.exchange.s1.trading.service.StockLookup;

import java.util.List;

/** contracts/s1/trading.yaml - Trades. */
@RestController
@RequestMapping("/api/trades")
@Tag(name = "Trades", description = "Executed trades")
public class TradeController {

    private final TradeRepository trades;
    private final StockLookup stocks;

    public TradeController(TradeRepository trades, StockLookup stocks) {
        this.trades = trades;
        this.stocks = stocks;
    }

    @GetMapping("/mine")
    @Operation(summary = "My trades, newest first", description = "side is BUY or SELL from your point of view.")
    @Transactional(readOnly = true)
    public PageResponse<TradeResponse> mine(AuthenticatedUser user,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        var result = trades.findMine(user.userId(), PageRequest.of(page, Math.min(size, 200)));
        return PageResponse.of(result, t -> TradeResponse.from(t, stocks.byId(t.getStockId()).getSymbol(), user.userId()));
    }

    @GetMapping
    @Operation(summary = "Recent trades of a stock (public tape)")
    @Transactional(readOnly = true)
    public List<TradeResponse> recent(@RequestParam String symbol, @RequestParam(defaultValue = "50") int limit) {
        var stock = stocks.bySymbol(symbol);
        return trades.findByStockIdOrderByExecutedAtDesc(stock.getId(), PageRequest.of(0, Math.min(limit, 200)))
                .stream().map(t -> TradeResponse.from(t, stock.getSymbol(), null)).toList();
    }
}
