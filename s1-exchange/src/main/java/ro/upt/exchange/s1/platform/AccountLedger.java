package ro.upt.exchange.s1.platform;

import ro.upt.exchange.s1.domain.Trade;

import java.math.BigDecimal;

/**
 * Money and shares ledger. <b>Owner: Cezar.</b> Signatures fixed in Phase 0 (Work_Split.pdf,
 * "Interfaces between owners"). All methods run inside the caller's transaction and must use
 * atomic conditional UPDATEs (see {@code AccountRepository} / {@code PortfolioRepository}).
 *
 * <p>Contract used by trading/ (Patric):
 * <ul>
 *   <li>A LIMIT BUY reserves {@code price × qty} at placement; after each fill trading releases
 *       the difference {@code (limit − execution) × filledQty}; on cancel it releases
 *       {@code remaining × limit}.</li>
 *   <li>A MARKET BUY reserves nothing at placement; the matcher calls {@link #reserveCash} for the
 *       exact trade total right before {@link #settleTrade}.</li>
 *   <li>A SELL reserves {@code qty} shares at placement; on cancel trading releases the remainder.</li>
 *   <li>{@link #settleTrade} assumes the buyer has at least {@code trade.totalValue} reserved and
 *       the seller has at least {@code trade.qty} shares reserved.</li>
 *   <li>{@link InsufficientFundsException} / {@link InsufficientSharesException} are business
 *       outcomes that the caller catches (it rejects the order, or cancels a MARKET remainder and
 *       keeps the fills). An implementation that is itself {@code @Transactional} must therefore
 *       declare {@code noRollbackFor} for both, or the caller's transaction becomes rollback-only.</li>
 * </ul>
 */
public interface AccountLedger {

    /** @throws InsufficientFundsException if available cash (balance − reserved) is below {@code amount} */
    void reserveCash(long accountId, BigDecimal amount);

    void releaseCash(long accountId, BigDecimal amount);

    /** @throws InsufficientSharesException if free shares (quantity − reserved) are below {@code qty} */
    void reserveShares(long accountId, long stockId, long qty);

    void releaseShares(long accountId, long stockId, long qty);

    /**
     * Buyer: cash_balance −= total, reserved_cash −= total, position += qty (avg cost updated).
     * Seller: quantity −= qty, reserved_qty −= qty, cash_balance += total.
     */
    void settleTrade(Trade trade);
}
