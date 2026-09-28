package su.nightexpress.excellenteconomy.tops.menu;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PendingMenuClosesTest {

    @Test
    void clicksAndDragsStayCancelledUntilTheDeferredClose() {
        Plugin host = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(host.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        InventoryView view = mock(InventoryView.class);
        PendingMenuCloses guard = new PendingMenuCloses(host, Map.of(id, view));
        verify(plugins).registerEvents(guard, host);

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        guard.onClick(click);
        verify(click).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getView()).thenReturn(view);
        guard.onDrag(drag);
        verify(drag).setCancelled(true);

        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        when(close.getView()).thenReturn(view);
        guard.onClose(close);
        clearInvocations(click);
        guard.onClick(click);
        verify(click, never()).setCancelled(true);
    }

    @Test
    void guardDoesNotCancelANewerInventory() {
        Plugin host = mock(Plugin.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        InventoryView oldView = mock(InventoryView.class);
        InventoryView newView = mock(InventoryView.class);
        PendingMenuCloses guard = new PendingMenuCloses(host, Map.of(id, oldView));
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(newView);
        guard.onClick(click);
        verify(click, never()).setCancelled(true);
        guard.complete(id, oldView);
    }
}
