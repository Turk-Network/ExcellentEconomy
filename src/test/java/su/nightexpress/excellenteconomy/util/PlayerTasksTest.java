package su.nightexpress.excellenteconomy.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.nightcore.util.Version;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerTasksTest {

    @Test
    void foliaPlayerActionsUseTheEntityScheduler() {
        EconomyPlugin plugin = mock(EconomyPlugin.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean();
        try (var version = mockStatic(Version.class)) {
            version.when(Version::isFolia).thenReturn(true);
            PlayerTasks.run(plugin, player, () -> ran.set(true));
            assertFalse(ran.get());
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(plugin).runTask(eq((Entity) player), task.capture());
            task.getValue().run();
            assertTrue(ran.get());
        }
    }

    @Test
    void disconnectedPlayersDoNotReceiveDeferredActions() {
        EconomyPlugin plugin = mock(EconomyPlugin.class);
        Player player = mock(Player.class);
        AtomicBoolean ran = new AtomicBoolean();
        try (var version = mockStatic(Version.class)) {
            version.when(Version::isFolia).thenReturn(true);
            PlayerTasks.run(plugin, player, () -> ran.set(true));
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(plugin).runTask(eq((Entity) player), task.capture());
            task.getValue().run();
            assertFalse(ran.get());
        }
    }

    @Test
    void ordinaryServerActionsRemainImmediate() {
        EconomyPlugin plugin = mock(EconomyPlugin.class);
        Player player = mock(Player.class);
        AtomicBoolean ran = new AtomicBoolean();
        try (var version = mockStatic(Version.class)) {
            version.when(Version::isFolia).thenReturn(false);
            PlayerTasks.run(plugin, player, () -> ran.set(true));
            assertTrue(ran.get());
            verifyNoInteractions(plugin);
        }
    }

    @Test
    void consoleActionsRemainImmediateOnFolia() {
        EconomyPlugin plugin = mock(EconomyPlugin.class);
        AtomicBoolean ran = new AtomicBoolean();
        PlayerTasks.run(plugin, mock(CommandSender.class), () -> ran.set(true));
        assertTrue(ran.get());
        verifyNoInteractions(plugin);
    }
}
