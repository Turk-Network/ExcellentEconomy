package su.nightexpress.excellenteconomy.user;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import io.papermc.paper.ServerBuildInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.event.ChangeBalanceEvent;
import su.nightexpress.excellenteconomy.command.CommandManager;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.excellenteconomy.data.DataHandler;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class BalanceConcurrencyTest {

    private static PluginManager events;
    private ExcellentCurrency currency;

    @BeforeAll
    static void setUpServer() {
        Server server = mock(Server.class);
        events = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(events);
        when(server.getLogger()).thenReturn(Logger.getLogger("EconomyTests"));
        when(server.getName()).thenReturn("TestServer");
        when(server.getVersion()).thenReturn("26.1.2");
        when(server.getBukkitVersion()).thenReturn("26.1.2");
        try (var buildInfo = mockStatic(ServerBuildInfo.class)) {
            buildInfo.when(ServerBuildInfo::buildInfo).thenReturn(mock(ServerBuildInfo.class));
            Bukkit.setServer(server);
        }
    }

    @BeforeEach
    void setUpCurrency() {
        reset(events);
        currency = mock(ExcellentCurrency.class);
        when(currency.getId()).thenReturn("coins");
        when(currency.floorIfNeeded(anyDouble())).thenAnswer(call -> call.getArgument(0));
        when(currency.floorAndLimit(anyDouble())).thenAnswer(call -> call.getArgument(0));
    }

    private CoinsUser user(double balance) {
        return new CoinsUser(UUID.randomUUID(), "Player", new UserBalance(Map.of("coins", balance)),
            Map.of(), 0L, false);
    }

    @Test
    void concurrentCancelledChangesDoNotEraseAcceptedDeposits() throws Exception {
        CoinsUser user = user(0);
        AtomicInteger changes = new AtomicInteger();
        doAnswer(call -> {
            ChangeBalanceEvent event = call.getArgument(0);
            assertEquals(1D, event.getNewAmount() - event.getOldAmount());
            if (changes.getAndIncrement() % 2 == 0) event.setCancelled(true);
            Thread.yield();
            return null;
        }).when(events).callEvent(any(ChangeBalanceEvent.class));

        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 1000; i++) {
                tasks.add(() -> {
                    user.addBalance(currency, 1);
                    return null;
                });
            }
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        assertEquals(500D, user.getBalance(currency));
        assertEquals(1000, changes.get());
    }

    @Test
    void parallelPaymentsCannotSpendTheSameBalanceTwice() throws Exception {
        CoinsUser source = user(100);
        CoinsUser target = user(0);
        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(source.getId());
        when(sender.getName()).thenReturn("Sender");
        CurrencyManager manager = manager(sender, source);

        int successful = 0;
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 200; i++) tasks.add(() -> manager.send(sender, target, currency, 1));
            for (var result : executor.invokeAll(tasks)) if (result.get()) successful++;
        }
        assertEquals(100, successful);
        assertEquals(0D, source.getBalance(currency));
        assertEquals(100D, target.getBalance(currency));
    }

    @Test
    void cancelledRecipientChangeRefundsTheSender() {
        CoinsUser source = user(100);
        CoinsUser target = user(0);
        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(source.getId());
        when(sender.getName()).thenReturn("Sender");
        doAnswer(call -> {
            ChangeBalanceEvent event = call.getArgument(0);
            if (event.getUser() == target) event.setCancelled(true);
            return null;
        }).when(events).callEvent(any(ChangeBalanceEvent.class));

        assertFalse(manager(sender, source).send(sender, target, currency, 25));
        assertEquals(100D, source.getBalance(currency));
        assertEquals(0D, target.getBalance(currency));
    }

    @Test
    void cancelledWithdrawalDoesNotCreditTheRecipient() {
        CoinsUser source = user(100);
        CoinsUser target = user(0);
        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(source.getId());
        doAnswer(call -> {
            ChangeBalanceEvent event = call.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(events).callEvent(any(ChangeBalanceEvent.class));

        assertFalse(manager(sender, source).send(sender, target, currency, 25));
        assertEquals(100D, source.getBalance(currency));
        assertEquals(0D, target.getBalance(currency));
    }

    private CurrencyManager manager(Player sender, CoinsUser source) {
        UserManager users = mock(UserManager.class);
        when(users.getOrFetch(sender)).thenAnswer(call -> {
            assertFalse(Thread.holdsLock(BalanceTransactions.LOCK), "User fetch must not hold the balance monitor");
            return source;
        });
        return new CurrencyManager(mock(EconomyPlugin.class), new CurrencyRegistry(), mock(CommandManager.class),
            mock(DataHandler.class), users);
    }

    @Test
    void parallelExchangesCannotSpendTheSameBalanceTwice() throws Exception {
        CoinsUser source = user(100);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(source.getId());
        when(currency.isExchangeAllowed()).thenReturn(true);
        ExcellentCurrency target = mock(ExcellentCurrency.class);
        when(target.getId()).thenReturn("tokens");
        when(target.isUnderLimit(anyDouble())).thenReturn(true);
        when(currency.canExchangeTo(target)).thenReturn(true);
        when(currency.getExchangeResult(eq(target), anyDouble())).thenReturn(2D);
        CurrencyManager manager = manager(player, source);

        int successful = 0;
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 200; i++) tasks.add(() -> manager.exchange(player, currency, target, 1));
            for (var result : executor.invokeAll(tasks)) if (result.get()) successful++;
        }
        assertEquals(100, successful);
        assertEquals(0D, source.getBalance(currency));
        assertEquals(200D, source.getBalance(target));
    }

    @Test
    void cancelledExchangeDepositRefundsTheSourceCurrency() {
        CoinsUser source = user(100);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(source.getId());
        when(currency.isExchangeAllowed()).thenReturn(true);
        ExcellentCurrency target = mock(ExcellentCurrency.class);
        when(target.getId()).thenReturn("tokens");
        when(target.isUnderLimit(anyDouble())).thenReturn(true);
        when(currency.canExchangeTo(target)).thenReturn(true);
        when(currency.getExchangeResult(eq(target), anyDouble())).thenReturn(50D);
        doAnswer(call -> {
            ChangeBalanceEvent event = call.getArgument(0);
            if (event.getCurrency() == target) event.setCancelled(true);
            return null;
        }).when(events).callEvent(any(ChangeBalanceEvent.class));

        assertFalse(manager(player, source).exchange(player, currency, target, 25));
        assertEquals(100D, source.getBalance(currency));
        assertEquals(0D, source.getBalance(target));
    }

    @Test
    void concurrentMaintenanceRequestsHaveOnlyOneWinner() throws Exception {
        CurrencyManager manager = manager(mock(Player.class), user(0));
        int successful = 0;
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 100; i++) tasks.add(manager::tryDisableOperations);
            for (var result : executor.invokeAll(tasks)) if (result.get()) successful++;
        }
        assertEquals(1, successful);
        assertFalse(manager.canPerformOperations());
        manager.allowOperations();
        assertTrue(manager.tryDisableOperations());
    }

    @Test
    void recipientBonusDoesNotRefundAnAcceptedPayment() {
        CoinsUser source = user(100);
        CoinsUser target = user(0);
        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(source.getId());
        doAnswer(call -> {
            ChangeBalanceEvent event = call.getArgument(0);
            if (event.getUser() == target) target.getBalance().add(currency, 1);
            return null;
        }).when(events).callEvent(any(ChangeBalanceEvent.class));

        assertTrue(manager(sender, source).send(sender, target, currency, 25));
        assertEquals(75D, source.getBalance(currency));
        assertEquals(26D, target.getBalance(currency));
    }

    @Test
    void acceptedDebitAdjustmentSurvivesACancelledRecipientDeposit() {
        CoinsUser source = user(100);
        CoinsUser target = user(0);
        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(source.getId());
        doAnswer(call -> {
            ChangeBalanceEvent event = call.getArgument(0);
            if (event.getUser() == source) source.getBalance().add(currency, 1);
            else event.setCancelled(true);
            return null;
        }).when(events).callEvent(any(ChangeBalanceEvent.class));

        assertFalse(manager(sender, source).send(sender, target, currency, 25));
        assertEquals(101D, source.getBalance(currency));
        assertEquals(0D, target.getBalance(currency));
    }

    @Test
    void exchangeBonusDoesNotRefundAnAcceptedExchange() {
        CoinsUser source = user(100);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(source.getId());
        when(currency.isExchangeAllowed()).thenReturn(true);
        ExcellentCurrency target = mock(ExcellentCurrency.class);
        when(target.getId()).thenReturn("tokens");
        when(target.isUnderLimit(anyDouble())).thenReturn(true);
        when(currency.canExchangeTo(target)).thenReturn(true);
        when(currency.getExchangeResult(eq(target), anyDouble())).thenReturn(50D);
        doAnswer(call -> {
            ChangeBalanceEvent event = call.getArgument(0);
            if (event.getCurrency() == target) source.getBalance().add(target, 1);
            return null;
        }).when(events).callEvent(any(ChangeBalanceEvent.class));

        assertTrue(manager(player, source).exchange(player, currency, target, 25));
        assertEquals(75D, source.getBalance(currency));
        assertEquals(51D, source.getBalance(target));
    }
}
