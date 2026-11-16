package ro.upt.exchange.s1.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.*;
import ro.upt.exchange.s1.ops.IncidentService;
import ro.upt.exchange.s1.platform.AccountLedger;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.platform.InsufficientFundsException;
import ro.upt.exchange.s1.trading.dto.BuyOfferRequest;
import ro.upt.exchange.s1.trading.dto.BuyOfferResponse;
import ro.upt.exchange.s1.trading.dto.OfferResponse;
import ro.upt.exchange.s1.trading.dto.OrderResponse;
import ro.upt.exchange.s1.trading.dto.TradeResponse;
import ro.upt.exchange.s1.trading.error.OfferConflictException;
import ro.upt.exchange.s1.trading.error.OfferConflictException.Reason;
import ro.upt.exchange.s1.trading.error.TradingException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Buy a specific offer at the price you saw (Application_Description.pdf §3.2; concurrency cases 1 and 2).
 *
 * <p>Everything happens in one transaction: the buyer's order is created and filled, the offer
 * is decremented by <em>one conditional UPDATE</em>, the trade is inserted and settled, and the
 * events go to the outbox. If the UPDATE changes 0 rows the transaction rolls back and the
 * caller gets 409 with the reason and the offer's current state.
 */
@Service
public class OfferService {

    private final OfferTransaction tx;
    private final IncidentService incidents;
    private final StockLookup stocks;

    public OfferService(OfferTransaction tx, IncidentService incidents, StockLookup stocks) {
        this.tx = tx;
        this.incidents = incidents;
        this.stocks = stocks;
    }

    public BuyOfferResponse buy(AuthenticatedUser buyer, long offerId, BuyOfferRequest req) {
        try {
            return tx.buy(buyer, offerId, req);
        } catch (OfferConflictException e) {
            // recorded outside the rolled-back transaction, for the nightly report
            Long stockId = e.getCurrentOffer() == null ? null : stocks.bySymbol(e.getCurrentOffer().symbol()).getId();
            incidents.raise(IncidentType.CONCURRENCY_CONFLICT, IncidentSeverity.LOW, stockId, offerId, buyer.userId(),
                    "buy-from-offer " + e.getReason() + ": " + e.getMessage(),
                    Map.of("reason", e.getReason().name(), "qty", req.qty(), "maxPrice", req.maxPrice(), "seenVersion", req.seenVersion()));
            throw e;
        }
    }

    /** The transactional part, separated so the conflict incident can be raised after the rollback. */
    @Service
    public static class OfferTransaction {

        private final OrderRepository orders;
        private final TradeRepository trades;
        private final StockRepository stockRepo;
        private final StockLookup stocks;
        private final AccountLedger ledger;
        private final OrderWriteStrategy writes;
        private final TradingEvents events;

        public OfferTransaction(OrderRepository orders, TradeRepository trades, StockRepository stockRepo,
                                StockLookup stocks, AccountLedger ledger, OrderWriteStrategy writes, TradingEvents events) {
            this.orders = orders;
            this.trades = trades;
            this.stockRepo = stockRepo;
            this.stocks = stocks;
            this.ledger = ledger;
            this.writes = writes;
            this.events = events;
        }

        @Transactional
        public BuyOfferResponse buy(AuthenticatedUser buyer, long offerId, BuyOfferRequest req) {
            if (!buyer.is(Role.TRADER)) {
                throw TradingException.forbidden("Only TRADER users can buy from an offer");
            }
            Order offer = orders.findById(offerId).orElseThrow(() -> TradingException.notFound("Offer", offerId));
            Stock stock = stocks.byId(offer.getStockId());
            String symbol = stock.getSymbol();

            if (offer.getSide() != OrderSide.SELL || !offer.getStatus().isLive()) {
                throw new OfferConflictException(Reason.NO_LONGER_AVAILABLE, OfferResponse.from(offer, symbol));
            }
            if (offer.getUserId().equals(buyer.userId())) {
                throw TradingException.badRequest("You cannot buy your own offer");
            }
            if (req.qty() > offer.getQty()) {
                throw TradingException.badRequest("qty exceeds the offer's size (" + offer.getQty() + ")");
            }

            // The price the trade executes at. SAFE: the UPDATE guarantees it is ≤ maxPrice and the
            // version the buyer saw. UNSAFE: whatever the row says right now.
            BigDecimal price = offer.getPrice();
            long qty = req.qty();
            BigDecimal total = Trade.totalOf(qty, price);

            Order buy = new Order(buyer.userId(), buyer.accountId(), stock.getId(), OrderSide.BUY, OrderType.LIMIT,
                    price, qty, OrderSource.WEB);
            orders.save(buy);

            try {
                ledger.reserveCash(buyer.accountId(), total);
            } catch (InsufficientFundsException e) {
                throw TradingException.insufficientFunds("Insufficient funds to buy " + qty + " × " + price,
                        Map.of("requested", total));
            }

            // ---- the one conditional UPDATE (cases 1 and 2) ----
            int taken = writes.takeOffer(offer, qty, req.seenVersion(), req.maxPrice());
            if (taken == 0) {
                Order now = orders.findById(offerId).orElse(null);
                Reason reason = (now == null || !now.getStatus().isLive() || now.getRemainingQty() < qty)
                        ? Reason.NO_LONGER_AVAILABLE
                        : Reason.PRICE_CHANGED;
                throw new OfferConflictException(reason, now == null ? null : OfferResponse.from(now, symbol));
            }

            if (orders.fill(buy.getId(), qty) != 1) {
                throw new IllegalStateException("Buyer order " + buy.getId() + " could not be filled");
            }
            Trade trade = trades.save(new Trade(buy, offer, qty, price));
            ledger.settleTrade(trade);
            stockRepo.updateValue(stock.getId(), price);

            Order buyNow = orders.findById(buy.getId()).orElseThrow();
            Order offerNow = orders.findById(offerId).orElseThrow();
            events.orderPlaced(buyNow, stock);
            events.orderChanged(buyNow, stock);
            events.orderChanged(offerNow, stock);
            events.tradeExecuted(trade, stock);
            events.priceUpdated(stock, price);
            events.bookChanged(stock);

            return new BuyOfferResponse(
                    TradeResponse.from(trade, symbol, buyer.userId()),
                    OrderResponse.from(buyNow, symbol),
                    OfferResponse.from(offerNow, symbol));
        }
    }
}
