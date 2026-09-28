package su.nightexpress.excellenteconomy.user;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.event.ChangeBalanceEvent;
import su.nightexpress.excellenteconomy.user.data.CurrencySettings;
import su.nightexpress.nightcore.user.UserTemplate;

public class CoinsUser extends UserTemplate {

    private final UserBalance                   balance;
    private final Map<String, CurrencySettings> settingsMap;

    private volatile long    lastSeen;
    private volatile boolean hiddenFromTops;
    private volatile boolean dirtyState;

    public CoinsUser(@NonNull UUID uuid,
                     @NonNull String name,
                     @NonNull UserBalance balance,
                     @NonNull Map<String, CurrencySettings> settingsMap,
                     long lastSeen,
                     boolean hiddenFromTops) {
        super(uuid, name);
        this.balance = balance;
        this.settingsMap = new ConcurrentHashMap<>(settingsMap);

        this.setLastSeen(lastSeen);
        this.setHiddenFromTops(hiddenFromTops);
    }

    @NonNull
    public UserBalance getBalance() {
        return this.balance;
    }

    /**
     * Edits user's balance of specific currency and fires the ChangeBalanceEvent event. If event was cancelled, the
     * balance is set back to previous (old) value.
     *
     * @param currency Currency to edit balance of.
     * @param consumer balance function.
     */
    public void editBalance(@NonNull ExcellentCurrency currency, @NonNull Consumer<UserBalance> consumer) {
        synchronized (BalanceTransactions.LOCK) {
            double oldBalance = this.getBalance(currency);

            consumer.accept(this.balance);

            ChangeBalanceEvent event = new ChangeBalanceEvent(this, currency, oldBalance, this.getBalance(currency));
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                this.balance.set(currency.getId(), oldBalance);
            }
        }
    }

    @Override
    public void markDirty() {
        this.dirtyState = true;
    }

    @Override
    public void markClean() {
        this.dirtyState = false;
    }

    @Override
    public boolean isDirty() {
        return this.dirtyState;
    }

    public void resetBalance(@NonNull Collection<ExcellentCurrency> currencies) {
        currencies.forEach(this::resetBalance);
    }

    public void resetBalance(@NonNull ExcellentCurrency currency) {
        this.editBalance(currency, bal -> bal.set(currency, currency.getStartValue()));
    }

    public boolean hasEnough(@NonNull ExcellentCurrency currency, double amount) {
        return this.balance.has(currency, amount);
    }

    public double getBalance(@NonNull ExcellentCurrency currency) {
        return this.balance.get(currency);
    }

    public void addBalance(@NonNull ExcellentCurrency currency, double amount) {
        this.editBalance(currency, bal -> bal.add(currency, amount));
    }

    public void removeBalance(@NonNull ExcellentCurrency currency, double amount) {
        this.editBalance(currency, lookup -> lookup.remove(currency, amount));
    }

    public void setBalance(@NonNull ExcellentCurrency currency, double amount) {
        this.editBalance(currency, lookup -> lookup.set(currency, amount));
    }

    @NonNull
    public Map<String, CurrencySettings> getSettingsMap() {
        return this.settingsMap;
    }

    @NonNull
    public CurrencySettings getSettings(@NonNull ExcellentCurrency currency) {
        return this.settingsMap.computeIfAbsent(currency.getId(), k -> CurrencySettings.create(currency));
    }

    public long getLastSeen() {
        return this.lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isHiddenFromTops() {
        return this.hiddenFromTops;
    }

    public void setHiddenFromTops(boolean hiddenFromTops) {
        this.hiddenFromTops = hiddenFromTops;
    }
}
