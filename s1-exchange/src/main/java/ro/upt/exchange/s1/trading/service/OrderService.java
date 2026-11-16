package ro.upt.exchange.s1.trading.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.*;
import ro.upt.exchange.s1.ops.IncidentService;
import ro.upt.exchange.s1.platform.AccountLedger;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.platform.InsufficientFundsException;
import ro.upt.exchange.s1.platform.InsufficientSharesException;
import ro.upt.exchange.s1.trading.dto.ModifyOrderRequest;
import ro.upt.exchange.s1.trading.dto.OrderResponse;
import ro.upt.exchange.s1.trading.dto.PageResponse;
import ro.upt.exchange.s1.trading.dto.PlaceOrderRequest;
import ro.upt.exchange.s1.trading.error.TradingException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The API side of orders (Application_Description.pdf §3.1, steps 1-2).
 *
 * <p>Place: validate, reserve (cash for a LIMIT BUY, shares for a SELL), save as PENDING, write
 * OrderPlaced to the outbox, and after commit send PlaceOrder to the matcher. Modify and cancel
 * only validate and send a command: the change itself is applied by the matcher thread of that
 * stock, so it is serialised with the fills (see {@link MatchingEngine}).
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final StockLookup stocks;
    private final AccountLedger ledger;
    private final IncidentService incidents;
    private final TradingEvents events;
    private final OrderCommandPublisher commands;

    public OrderService(OrderRepository orders, StockLookup stocks, AccountLedger ledger,
                        IncidentService incidents, TradingEvents events, OrderCommandPublisher commands) {
        this.orders = orders;
        this.stocks = stocks;
        this.ledger = ledger;
        this.incidents = incidents;
        this.events = events;
        this.commands = commands;
    }

    /** Result of a place: the saved order, and whether it was rejected at placement (422). */
    public record PlaceResult(OrderResponse order, boolean rejected) {}

    @Transactional
    public PlaceResult place(AuthenticatedUser user, PlaceOrderRequest req) {
        requireTrader(user);
        Stock stock = stocks.tradable(req.symbol());
        BigDecimal price = req.type() == OrderType.LIMIT ? normalisePrice(req.price(), stock) : null;
        OrderSource source = user.is(Role.MARKET_MAKER) ? OrderSource.MARKET_MAKER : OrderSource.WEB;

        Order order = new Order(user.userId(), user.accountId(), stock.getId(), req.side(), req.type(), price, req.qty(), source);

        String rejection = reserve(order, stock);
        if (rejection != null) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectReason(rejection);
            order.setRemainingQty(order.getQty());
            orders.save(order);
            events.orderChanged(order, stock);
            IncidentType type = order.getSide() == OrderSide.BUY ? IncidentType.INSUFFICIENT_FUNDS : IncidentType.INSUFFICIENT_SHARES;
            incidents.raise(type, IncidentSeverity.LOW, stock.getId(), order.getId(), user.userId(),
                    rejection, Map.of("qty", req.qty(), "price", String.valueOf(price), "side", req.side().name()));
            return new PlaceResult(OrderResponse.from(order, stock.getSymbol()), true);
        }

        orders.save(order);
        events.orderPlaced(order, stock);
        commands.placeAfterCommit(order, stock.getSymbol());
        return new PlaceResult(OrderResponse.from(order, stock.getSymbol()), false);
    }

    /** @return null if reserved, else the reason the order is rejected */
    private String reserve(Order order, Stock stock) {
        try {
            if (order.getSide() == OrderSide.BUY) {
                if (order.getType() == OrderType.LIMIT) {
                    ledger.reserveCash(order.getAccountId(), Trade.totalOf(order.getQty(), order.getPrice()));
                }
                // MARKET BUY: the matcher reserves the exact trade total right before each fill
            } else {
                ledger.reserveShares(order.getAccountId(), stock.getId(), order.getQty());
            }
            return null;
        } catch (InsufficientFundsException e) {
            return "INSUFFICIENT_FUNDS: need " + e.getRequested() + " available";
        } catch (InsufficientSharesException e) {
            return "INSUFFICIENT_SHARES: need " + e.getRequested() + " free shares of " + stock.getSymbol();
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse modify(AuthenticatedUser user, long orderId, ModifyOrderRequest req) {
        Order order = own(user, orderId);
        Stock stock = stocks.byId(order.getStockId());
        if (order.getType() != OrderType.LIMIT) {
            throw TradingException.badRequest("Only LIMIT orders can be modified");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            throw TradingException.invalidState("Order is still being processed, retry in a moment");
        }
        if (!order.getStatus().isLive()) {
            throw TradingException.invalidState("Order is " + order.getStatus() + " and cannot be modified");
        }
        BigDecimal newPrice = req.price() == null ? null : normalisePrice(req.price(), stock);
        if (req.qty() != null && req.qty() <= order.filledQty()) {
            throw TradingException.badRequest("qty must be greater than the filled quantity (" + order.filledQty() + ")");
        }
        commands.modifyAfterCommit(order, stock.getSymbol(), newPrice, req.qty());
        return OrderResponse.from(order, stock.getSymbol());
    }

    @Transactional(readOnly = true)
    public OrderResponse cancel(AuthenticatedUser user, long orderId) {
        Order order = own(user, orderId);
        Stock stock = stocks.byId(order.getStockId());
        if (!OrderStatus.CANCELLABLE.contains(order.getStatus())) {
            throw TradingException.invalidState("Order is " + order.getStatus() + " and cannot be cancelled");
        }
        commands.cancelAfterCommit(order, stock.getSymbol());
        return OrderResponse.from(order, stock.getSymbol());
    }

    @Transactional(readOnly = true)
    public OrderResponse get(AuthenticatedUser user, long orderId) {
        Order order = own(user, orderId);
        return OrderResponse.from(order, stocks.byId(order.getStockId()).getSymbol());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> mine(AuthenticatedUser user, int page, int size) {
        var result = orders.findByUserIdOrderByCreatedTimeDesc(user.userId(), PageRequest.of(page, Math.min(size, 200)));
        return PageResponse.of(result, o -> OrderResponse.from(o, stocks.byId(o.getStockId()).getSymbol()));
    }

    // ------------------------------------------------------------------ helpers

    private Order own(AuthenticatedUser user, long orderId) {
        return orders.findByIdAndUserId(orderId, user.userId())
                .orElseThrow(() -> TradingException.notFound("Order", orderId));
    }

    private static void requireTrader(AuthenticatedUser user) {
        if (!user.canTrade()) {
            throw TradingException.forbidden("Only TRADER and MARKET_MAKER users can place orders");
        }
    }

    /** Price must be a positive multiple of the stock's tick size; stored with 4 decimals. */
    static BigDecimal normalisePrice(BigDecimal price, Stock stock) {
        BigDecimal p = price.setScale(4, java.math.RoundingMode.UNNECESSARY);
        if (p.remainder(stock.getTickSize()).signum() != 0) {
            throw TradingException.badRequest("price must be a multiple of the tick size " + stock.getTickSize().stripTrailingZeros().toPlainString());
        }
        return p;
    }
}
