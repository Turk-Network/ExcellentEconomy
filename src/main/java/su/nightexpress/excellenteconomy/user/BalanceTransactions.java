package su.nightexpress.excellenteconomy.user;

/**
 * Serializes balance changes and multi-user transactions across region and async threads.
 * A single reentrant monitor also lets balance event listeners call the economy API
 * without acquiring user locks in a different order.
 */
public final class BalanceTransactions {

    public static final Object LOCK = new Object();

    private BalanceTransactions() {
    }
}
