package su.nightexpress.excellenteconomy.tops.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps closing GUI inventories protected even after ExcellentEconomy's listeners are disabled. */
final class PendingMenuCloses implements Listener {

    private final Map<UUID, InventoryView> views = new ConcurrentHashMap<>();

    PendingMenuCloses(Plugin host, Map<UUID, InventoryView> views) {
        this.views.putAll(views);
        if (!this.views.isEmpty()) host.getServer().getPluginManager().registerEvents(this, host);
    }

    void complete(UUID id, InventoryView view) {
        this.views.remove(id, view);
        if (this.views.isEmpty()) HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (this.views.get(event.getWhoClicked().getUniqueId()) == event.getView()) event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (this.views.get(event.getWhoClicked().getUniqueId()) == event.getView()) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        this.complete(event.getPlayer().getUniqueId(), event.getView());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        InventoryView view = this.views.get(player.getUniqueId());
        if (view != null) this.complete(player.getUniqueId(), view);
    }
}
