package ro.upt.exchange.s1.platform;

import java.math.BigDecimal;

/** Thrown by {@link AccountLedger#reserveCash}. Part of the ledger contract (owner: Cezar). */
public class InsufficientFundsException extends RuntimeException {
    private final long accountId;
    private final BigDecimal requested;

    public InsufficientFundsException(long accountId, BigDecimal requested) {
        super("Insufficient funds: account " + accountId + " cannot reserve " + requested);
        this.accountId = accountId;
        this.requested = requested;
    }

    public long getAccountId() { return accountId; }
    public BigDecimal getRequested() { return requested; }
}
