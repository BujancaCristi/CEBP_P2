package ro.upt.exchange.s1.testsupport;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.*;
import ro.upt.exchange.s1.platform.AuthenticatedUser;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates users, accounts, stocks, positions and resting orders straight in the database so
 * tests start from a known state without going through the matcher.
 */
@TestComponent
public class Fixtures {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    private final UserRepository users;
    private final AccountRepository accounts;
    private final CompanyRepository companies;
    private final StockRepository stocks;
    private final JdbcTemplate jdbc;

    public Fixtures(UserRepository users, AccountRepository accounts, CompanyRepository companies,
                    StockRepository stocks, JdbcTemplate jdbc) {
        this.users = users;
        this.accounts = accounts;
        this.companies = companies;
        this.stocks = stocks;
        this.jdbc = jdbc;
    }

    /** A user with an account; returned as the principal the controllers expect. */
    @Transactional
    public AuthenticatedUser trader(String prefix, BigDecimal cash) {
        return user(prefix, Role.TRADER, cash);
    }

    @Transactional
    public AuthenticatedUser user(String prefix, Role role, BigDecimal cash) {
        long n = SEQ.incrementAndGet();
        User u = users.save(new User(prefix + "_" + n, prefix + "_" + n + "@test.local", "x", role));
        Account a = accounts.save(new Account(u.getId(), cash));
        return new AuthenticatedUser(u.getId(), a.getId(), u.getUsername(), role);
    }

    /** A fresh stock (own company) so tests never share a book. */
    @Transactional
    public Stock stock(BigDecimal value) {
        long n = SEQ.incrementAndGet();
        Company c = companies.save(new Company("Company " + n, "Test", "RO", null));
        return stocks.save(new Stock(c.getId(), "T" + Long.toString(n, 36).toUpperCase(), value, 1_000_000));
    }

    /** Position with {@code quantity} shares of which {@code reserved} are locked by (other) open SELL orders. */
    public void position(AuthenticatedUser who, Stock stock, long quantity, long reserved) {
        jdbc.update("""
                INSERT INTO portfolio (account_id, stock_id, quantity, reserved_qty, avg_cost)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (account_id, stock_id) DO UPDATE
                   SET quantity = EXCLUDED.quantity, reserved_qty = EXCLUDED.reserved_qty
                """, who.accountId(), stock.getId(), quantity, reserved, stock.getValue());
    }

    /**
     * A resting (OPEN) SELL order = an offer, with its shares reserved on the seller's position.
     * The seller is given {@code qty} extra shares (quantity and reserved both grow by qty).
     */
    public long offer(AuthenticatedUser seller, Stock stock, BigDecimal price, long qty) {
        jdbc.update("""
                INSERT INTO portfolio (account_id, stock_id, quantity, reserved_qty, avg_cost)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (account_id, stock_id) DO UPDATE
                   SET quantity = portfolio.quantity + EXCLUDED.quantity,
                       reserved_qty = portfolio.reserved_qty + EXCLUDED.reserved_qty
                """, seller.accountId(), stock.getId(), qty, qty, price);
        return restingOrder(seller, stock, OrderSide.SELL, price, qty);
    }

    /** A resting (OPEN) BUY order with its cash reserved. */
    public long bid(AuthenticatedUser buyer, Stock stock, BigDecimal price, long qty) {
        BigDecimal total = Trade.totalOf(qty, price);
        int rows = jdbc.update("UPDATE accounts SET reserved_cash = reserved_cash + ? WHERE id = ? AND cash_balance - reserved_cash >= ?",
                total, buyer.accountId(), total);
        if (rows != 1) throw new IllegalStateException("fixture: buyer has no cash for the bid");
        return restingOrder(buyer, stock, OrderSide.BUY, price, qty);
    }

    private long restingOrder(AuthenticatedUser who, Stock stock, OrderSide side, BigDecimal price, long qty) {
        return jdbc.queryForObject("""
                INSERT INTO orders (user_id, account_id, stock_id, side, type, price, qty, remaining_qty, status, source)
                VALUES (?, ?, ?, ?, 'LIMIT', ?, ?, ?, 'OPEN', 'WEB') RETURNING id
                """, Long.class, who.userId(), who.accountId(), stock.getId(), side.name(), price, qty, qty);
    }

    /** The seller changes the price of an open order: same statement as {@code OrderRepository#reprice}, auto-committed. */
    public boolean reprice(long orderId, BigDecimal newPrice, long expectedVersion) {
        return jdbc.update("""
                UPDATE orders SET price = ?, version = version + 1, updated_at = now()
                 WHERE id = ? AND status IN ('OPEN', 'PARTIALLY_FILLED') AND version = ?
                """, newPrice, orderId, expectedVersion) == 1;
    }

    public BigDecimal cash(AuthenticatedUser who) {
        return jdbc.queryForObject("SELECT cash_balance FROM accounts WHERE id = ?", BigDecimal.class, who.accountId());
    }

    public BigDecimal reservedCash(AuthenticatedUser who) {
        return jdbc.queryForObject("SELECT reserved_cash FROM accounts WHERE id = ?", BigDecimal.class, who.accountId());
    }

    public long shares(AuthenticatedUser who, Stock stock) {
        Long q = jdbc.query("SELECT quantity FROM portfolio WHERE account_id = ? AND stock_id = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, who.accountId(), stock.getId());
        return q == null ? 0 : q;
    }

    public long reservedShares(AuthenticatedUser who, Stock stock) {
        Long q = jdbc.query("SELECT reserved_qty FROM portfolio WHERE account_id = ? AND stock_id = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, who.accountId(), stock.getId());
        return q == null ? 0 : q;
    }

    public long soldFrom(long sellOrderId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(qty), 0) FROM trades WHERE sell_order_id = ?", Long.class, sellOrderId);
    }

    public long remaining(long orderId) {
        return jdbc.queryForObject("SELECT remaining_qty FROM orders WHERE id = ?", Long.class, orderId);
    }

    public String status(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    public long version(long orderId) {
        return jdbc.queryForObject("SELECT version FROM orders WHERE id = ?", Long.class, orderId);
    }

    public BigDecimal maxTradePrice(long sellOrderId) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(price), 0) FROM trades WHERE sell_order_id = ?", BigDecimal.class, sellOrderId);
    }

    public long outboxCount(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE type = ?", Long.class, type);
    }
}
