package ro.upt.exchange.s1.trading.service;

import org.springframework.stereotype.Component;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.domain.StockRepository;
import ro.upt.exchange.s1.trading.error.TradingException;

/** Symbol ⇄ stock resolution with the trading error semantics (404 / 400 inactive). */
@Component
public class StockLookup {

    private final StockRepository stocks;

    public StockLookup(StockRepository stocks) {
        this.stocks = stocks;
    }

    public Stock bySymbol(String symbol) {
        return stocks.findBySymbolIgnoreCase(symbol.trim())
                .orElseThrow(() -> TradingException.notFound("Stock", symbol));
    }

    public Stock tradable(String symbol) {
        Stock s = bySymbol(symbol);
        if (!s.isActive()) {
            throw TradingException.badRequest("Stock " + s.getSymbol() + " is not active");
        }
        return s;
    }

    public Stock byId(long id) {
        return stocks.findById(id).orElseThrow(() -> TradingException.notFound("Stock", id));
    }
}
