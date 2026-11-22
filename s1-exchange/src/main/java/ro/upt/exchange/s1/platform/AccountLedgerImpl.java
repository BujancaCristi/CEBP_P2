package ro.upt.exchange.s1.platform;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.AccountRepository;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.OrderRepository;
import ro.upt.exchange.s1.domain.PortfolioRepository;
import ro.upt.exchange.s1.domain.Trade;

import java.math.BigDecimal;

@Service
@Profile("!local & !test")
@Transactional(propagation = Propagation.MANDATORY,
        noRollbackFor = {InsufficientFundsException.class, InsufficientSharesException.class})
public class AccountLedgerImpl implements AccountLedger {
    private final AccountRepository accounts;
    private final PortfolioRepository portfolios;
    private final OrderRepository orders;

    public AccountLedgerImpl(AccountRepository accounts, PortfolioRepository portfolios, OrderRepository orders) {
        this.accounts = accounts;
        this.portfolios = portfolios;
        this.orders = orders;
    }

    @Override
    public void reserveCash(long accountId, BigDecimal amount) {
        if (amount.signum() != 0 && accounts.reserveCash(accountId, amount) == 0) {
            throw new InsufficientFundsException(accountId, amount);
        }
    }

    @Override
    public void releaseCash(long accountId, BigDecimal amount) {
        if (amount.signum() != 0 && accounts.releaseCash(accountId, amount) == 0) {
            throw new IllegalStateException("Cash release exceeds the account reservation");
        }
    }

    @Override
    public void reserveShares(long accountId, long stockId, long qty) {
        if (qty != 0 && portfolios.reserveShares(accountId, stockId, qty) == 0) {
            throw new InsufficientSharesException(accountId, stockId, qty);
        }
    }

    @Override
    public void releaseShares(long accountId, long stockId, long qty) {
        if (qty != 0 && portfolios.releaseShares(accountId, stockId, qty) == 0) {
            throw new IllegalStateException("Share release exceeds the portfolio reservation");
        }
    }

    @Override
    public void settleTrade(Trade trade) {
        Order buy = orders.getReferenceById(trade.getBuyOrderId());
        Order sell = orders.getReferenceById(trade.getSellOrderId());
        if (accounts.debitReserved(buy.getAccountId(), trade.getTotalValue()) == 0) {
            throw new IllegalStateException("Buyer has insufficient reserved cash");
        }
        if (portfolios.deliverReserved(sell.getAccountId(), trade.getStockId(), trade.getQty()) == 0) {
            throw new IllegalStateException("Seller has insufficient reserved shares");
        }
        if (accounts.credit(sell.getAccountId(), trade.getTotalValue()) == 0
                || portfolios.receive(buy.getAccountId(), trade.getStockId(), trade.getQty(), trade.getPrice()) == 0) {
            throw new IllegalStateException("Trade settlement failed");
        }
    }
}
