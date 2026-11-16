package ro.upt.exchange.s1.platform;

/** Thrown by {@link AccountLedger#reserveShares}. Part of the ledger contract (owner: Cezar). */
public class InsufficientSharesException extends RuntimeException {
    private final long accountId;
    private final long stockId;
    private final long requested;

    public InsufficientSharesException(long accountId, long stockId, long requested) {
        super("Insufficient shares: account " + accountId + " cannot reserve " + requested + " of stock " + stockId);
        this.accountId = accountId;
        this.stockId = stockId;
        this.requested = requested;
    }

    public long getAccountId() { return accountId; }
    public long getStockId() { return stockId; }
    public long getRequested() { return requested; }
}
