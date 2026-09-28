package su.nightexpress.excellenteconomy.tops.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.nightcore.NightCore;
import su.nightexpress.nightcore.bridge.scheduler.AdaptedTask;
import su.nightexpress.nightcore.ui.inventory.Menu;
import su.nightexpress.nightcore.ui.inventory.MenuRegistry;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Isolates leaderboard viewers from NightCore's shared, single-threaded menu registry. */
public class FoliaMenuRegistry extends MenuRegistry implements Listener {

    private final EconomyPlugin owner;
    private final Map<UUID, Menu> menus = new ConcurrentHashMap<>();
    private AdaptedTask ticker;
    private volatile boolean shuttingDown;
    private PendingMenuCloses pendingCloses;

    public FoliaMenuRegistry(@NonNull EconomyPlugin owner) {
        super(NightCore.get());
        this.owner = owner;
    }

    @Override
    protected void onLoad() {
        this.owner.getServer().getPluginManager().registerEvents(this, this.owner);
        this.ticker = this.owner.scheduler().runTaskTimer(this::tickMenus, 1L, 1L);
    }

    @Override
    protected void onShutdown() {
        this.shuttingDown = true;
        if (this.ticker != null) this.ticker.cancel();
        Map<UUID, InventoryView> closingViews = new HashMap<>();
        this.getActiveMenus().forEach(menu -> menu.getViewers().forEach(viewer -> {
            InventoryView view = viewer.getCurrentView();
            if (view != null) closingViews.put(viewer.getPlayer().getUniqueId(), view);
        }));
        // Host this guard on NightCore, which also owns the deferred close tasks.
        this.pendingCloses = new PendingMenuCloses(NightCore.get(), closingViews);
        HandlerList.unregisterAll(this);
        this.getActiveMenus().forEach(Menu::close);
        this.menus.clear();
    }

    boolean isShuttingDown() {
        return this.shuttingDown;
    }

    void completeClose(UUID id, @Nullable InventoryView view) {
        if (view != null && this.pendingCloses != null) this.pendingCloses.complete(id, view);
    }

    @Override
    public void registerViewer(@NonNull Player player, @NonNull Menu menu) {
        this.menus.put(player.getUniqueId(), menu);
    }

    @Override
    public void unregisterViewer(@NonNull Player player) {
        this.menus.remove(player.getUniqueId());
    }

    @Override
    @Nullable
    public Menu getActiveMenu(@NonNull UUID id) {
        return this.menus.get(id);
    }

    @Override
    @NonNull
    public Set<Menu> getActiveMenus() {
        return Set.copyOf(this.menus.values());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Menu menu = this.getActiveMenu(player);
        if (menu != null) {
            if (this.shuttingDown) event.setCancelled(true);
            else menu.handleClick(player, event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Menu menu = this.getActiveMenu(player);
        if (menu != null) {
            if (this.shuttingDown) event.setCancelled(true);
            else menu.handleDrag(player, event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Menu menu = this.getActiveMenu(player);
        if (menu != null) menu.handleClose(player, event, this);
    }
}
