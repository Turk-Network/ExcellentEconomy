package su.nightexpress.excellenteconomy.tops.menu;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.nightcore.ui.inventory.viewer.MenuViewer;
import su.nightexpress.nightcore.util.Version;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoliaTopMenuTest {

    private FoliaTopMenu menu;
    private EconomyPlugin plugin;
    private Player player;
    private MenuViewer viewer;
    private ConcurrentHashMap<UUID, MenuViewer> viewers;

    @BeforeEach
    void setUp() throws Exception {
        // Avoid constructing an inventory: these tests exercise scheduling and viewer lifecycle.
        menu = mock(FoliaTopMenu.class, CALLS_REAL_METHODS);
        plugin = mock(EconomyPlugin.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        viewer = mock(MenuViewer.class);
        when(viewer.getPlayer()).thenReturn(player);
        when(viewer.canClickAgain()).thenReturn(true);
        viewers = new ConcurrentHashMap<>();
        viewers.put(player.getUniqueId(), viewer);
        field("owner", plugin);
        field("regionViewers", viewers);
    }

    private void field(String name, Object value) throws Exception {
        Field field = FoliaTopMenu.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(menu, value);
    }

    @Test
    void menuClickIsDeferredToThePlayersRegion() {
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        try (var version = mockStatic(Version.class)) {
            version.when(Version::isFolia).thenReturn(true);
            menu.handleClick(player, click);
            verify(click).setCancelled(true);
            verify(viewer, never()).handleClick(click);
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(plugin).runTask(eq((Entity) player), task.capture());
            task.getValue().run();
            verify(viewer).handleClick(click);
        }
    }

    @Test
    void closingMenuDiscardsAnAlreadyQueuedClick() {
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        FoliaMenuRegistry registry = mock(FoliaMenuRegistry.class);
        try (var version = mockStatic(Version.class)) {
            version.when(Version::isFolia).thenReturn(true);
            menu.handleClick(player, click);
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(plugin).runTask(eq((Entity) player), task.capture());
            menu.handleClose(player, mock(InventoryCloseEvent.class), registry);
            assertTrue(viewers.isEmpty());
            verify(registry).unregisterViewer(player);
            task.getValue().run();
            verify(viewer, never()).handleClick(click);
        }
    }

    @Test
    void menuRefreshRunsOnThePlayersRegion() {
        try (var version = mockStatic(Version.class)) {
            version.when(Version::isFolia).thenReturn(true);
            menu.refresh();
            verify(viewer, never()).refresh();
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(plugin).runTask(eq((Entity) player), task.capture());
            task.getValue().run();
            verify(viewer).refresh();
        }
    }
}
