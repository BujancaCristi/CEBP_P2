package ro.upt.exchange.s1.platform;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.AccountRepository;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.OrderRepository;
import ro.upt.exchange.s1.domain.PortfolioRepository;
import ro.upt.exchange.s1.domain.Trade;

import java.math.BigDecimal;

/**
 * STUB - owned by Cezar, delete this class when the real {@code AccountLedger} lands.
 *
 * <p>Patric ships it so that trading/ and the concurrency tests can settle trades before
 * platform/ is implemented. It is a working, minimal implementation built only on the
 * conditional UPDATEs in the domain repositories (so cash and shares can never go negative),
 * with none of the platform concerns (ledger history, audit, FROZEN handling beyond the WHERE).
 */
@Service
// Always inside the caller's transaction. Insufficient* are business outcomes the caller handles
// (reject the order, cancel the remainder), so they must NOT mark the transaction rollback-only.
@Transactional(propagation = Propagation.MANDATORY,
        noRollbackFor = {InsufficientFundsException.class, InsufficientSharesException.class})
public class StubAccountLedger implements AccountLedger {

    private final AccountRepository accounts;
    private final PortfolioRepository portfolios;
    private final OrderRepository orders;

    public StubAccountLedger(AccountRepository accounts, PortfolioRepository portfolios, OrderRepository orders) {
        this.accounts = accounts;
        this.portfolios = portfolios;
        this.orders = orders;
    }

    @Override
    public void reserveCash(long accountId, BigDecimal amount) {
        if (amount.signum() == 0) return;
        if (accounts.reserveCash(accountId, amount) == 0) {
            throw new InsufficientFundsException(accountId, amount);
        }
    }

    @Override
    public void releaseCash(long accountId, BigDecimal amount) {
        if (amount.signum() == 0) return;
        if (accounts.releaseCash(accountId, amount) == 0) {
            throw new IllegalStateException("Ledger: release of " + amount + " exceeds reservation on account " + accountId);
        }
    }

    @Override
    public void reserveShares(long accountId, long stockId, long qty) {
        if (qty == 0) return;
        if (portfolios.reserveShares(accountId, stockId, qty) == 0) {
            throw new InsufficientSharesException(accountId, stockId, qty);
        }
    }

    @Override
    public void releaseShares(long accountId, long stockId, long qty) {
        if (qty == 0) return;
        if (portfolios.releaseShares(accountId, stockId, qty) == 0) {
            throw new IllegalStateException("Ledger: release of " + qty + " shares exceeds reservation on account " + accountId);
        }
    }

    @Override
    public void settleTrade(Trade trade) {
        Order buy = orders.getReferenceById(trade.getBuyOrderId());
        Order sell = orders.getReferenceById(trade.getSellOrderId());
        long buyerAccount = buy.getAccountId();
        long sellerAccount = sell.getAccountId();

        if (accounts.debitReserved(buyerAccount, trade.getTotalValue()) == 0) {
            throw new IllegalStateException("Ledger: buyer account " + buyerAccount + " has less than " + trade.getTotalValue() + " reserved");
        }
        if (portfolios.deliverReserved(sellerAccount, trade.getStockId(), trade.getQty()) == 0) {
            throw new IllegalStateException("Ledger: seller account " + sellerAccount + " has less than " + trade.getQty() + " shares reserved");
        }
        accounts.credit(sellerAccount, trade.getTotalValue());
        portfolios.receive(buyerAccount, trade.getStockId(), trade.getQty(), trade.getPrice());
    }
}
