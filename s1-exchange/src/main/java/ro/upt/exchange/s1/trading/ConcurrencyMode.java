package ro.upt.exchange.s1.trading;

/**
 * {@code exchange.concurrency-mode}. Selects the SAFE mechanisms or the deliberately broken
 * UNSAFE variants for the three concurrency cases (Application_Description.pdf §6):
 *
 * <table>
 *   <tr><th>Case</th><th>SAFE</th><th>UNSAFE</th></tr>
 *   <tr><td>1 two buyers</td><td>conditional UPDATE … AND remaining_qty &gt;= :q</td><td>read, check in Java, write computed value</td></tr>
 *   <tr><td>2 price changed</td><td>… AND version = :seenVersion</td><td>version ignored</td></tr>
 *   <tr><td>3 demand &gt; supply</td><td>commands keyed by symbol → one matcher per stock</td><td>keyed by order id → parallel matchers, stale read-check-write</td></tr>
 * </table>
 */
public enum ConcurrencyMode { SAFE, UNSAFE }
