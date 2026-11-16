package ro.upt.exchange.s1.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.OrderRepository;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.events.BookSnapshot;
import ro.upt.exchange.s1.trading.dto.OfferResponse;

import java.time.Instant;
import java.util.List;

/** Read side of the book: price levels and the offers list. */
@Service
@Transactional(readOnly = true)
public class BookService {

    public static final int DEFAULT_DEPTH = 10;
    public static final int MAX_DEPTH = 50;

    private final OrderRepository orders;
    private final StockLookup stocks;

    public BookService(OrderRepository orders, StockLookup stocks) {
        this.orders = orders;
        this.stocks = stocks;
    }

    public BookSnapshot snapshot(String symbol, int depth) {
        return snapshot(stocks.bySymbol(symbol), depth);
    }

    public BookSnapshot snapshot(Stock stock, int depth) {
        int d = Math.max(1, Math.min(depth, MAX_DEPTH));
        long id = stock.getId();
        List<BookSnapshot.Level> bids = orders.bookSide(id, "BUY", d).stream()
                .map(l -> new BookSnapshot.Level(l.getPrice(), l.getQty(), l.getOrders())).toList();
        List<BookSnapshot.Level> asks = orders.bookSide(id, "SELL", d).stream()
                .map(l -> new BookSnapshot.Level(l.getPrice(), l.getQty(), l.getOrders())).toList();
        return new BookSnapshot(stock.getSymbol(), stock.getValue(),
                bids.isEmpty() ? null : bids.get(0).price(),
                asks.isEmpty() ? null : asks.get(0).price(),
                bids, asks, Instant.now());
    }

    /** Open SELL orders of a stock, best price first. */
    public List<OfferResponse> offers(String symbol) {
        Stock stock = stocks.bySymbol(symbol);
        return orders.findOffers(stock.getId()).stream().map(o -> OfferResponse.from(o, stock.getSymbol())).toList();
    }
}
