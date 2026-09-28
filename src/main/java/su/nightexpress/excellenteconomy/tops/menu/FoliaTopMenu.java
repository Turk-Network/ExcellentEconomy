package su.nightexpress.excellenteconomy.tops.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.tops.TopManager;
import su.nightexpress.excellenteconomy.util.PlayerTasks;
import su.nightexpress.nightcore.NightCore;
import su.nightexpress.nightcore.core.CoreConfig;
import su.nightexpress.nightcore.ui.inventory.MenuRegistry;
import su.nightexpress.nightcore.ui.inventory.viewer.MenuViewer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FoliaTopMenu extends TopMenu {

    private final EconomyPlugin owner;
    private final FoliaMenuRegistry registry;
    private final Map<UUID, MenuViewer> regionViewers = new ConcurrentHashMap<>();

    public FoliaTopMenu(@NonNull EconomyPlugin plugin, @NonNull TopManager manager,
                        @NonNull FoliaMenuRegistry registry) {
        super(plugin, manager);
        this.owner = plugin;
        this.registry = registry;
    }

    @Override
    public boolean show(@NonNull Player player, @NonNull ExcellentCurrency currency,
                        @Nullable Consumer<MenuViewer> preRender) {
        PlayerTasks.run(this.owner, player, () -> this.showMenu(this.registry, player, viewer -> {
            viewer.setCurrentObject(currency);
            if (preRender != null) preRender.accept(viewer);
        }));
        return true;
    }

    @Override
    @NonNull
    protected MenuViewer getOrCreateViewer(@NonNull Player player) {
        return this.regionViewers.computeIfAbsent(player.getUniqueId(), id -> new MenuViewer(player));
    }

    @Override
    @Nullable
    public MenuViewer getViewer(@NonNull UUID id) {
        return this.regionViewers.get(id);
    }

    @Override
    @NonNull
    public Set<MenuViewer> getViewers() {
        return Set.copyOf(this.regionViewers.values());
    }

    @Override
    public boolean hasViewers() {
        return !this.regionViewers.isEmpty();
    }

    @Override
    public void handleClick(@NonNull Player player, @NonNull InventoryClickEvent event) {
        MenuViewer viewer = this.getViewer(player);
        if (viewer == null) return;
        event.setCancelled(true);
        if (!viewer.canClickAgain()) return;
        viewer.setNextClickIn(System.currentTimeMillis() + CoreConfig.MENU_CLICK_COOLDOWN.get());
        this.onClick(viewer.createContext(), event);
        PlayerTasks.run(this.owner, player, () -> {
            if (this.getViewer(player) == viewer) viewer.handleClick(event);
        });
    }

    @Override
    public void handleClose(@NonNull Player player, @NonNull InventoryCloseEvent event,
                            @NonNull MenuRegistry registry) {
        MenuViewer viewer = this.getViewer(player);
        if (viewer == null || viewer.isRefreshing()) return;
        this.regionViewers.remove(player.getUniqueId(), viewer);
        this.onClose(viewer.createContext(), event);
        viewer.handleClose(event);
        registry.unregisterViewer(player);
    }

    @Override
    public void refresh() {
        this.getViewers().forEach(viewer -> this.refresh(viewer.getPlayer()));
    }

    @Override
    public void refresh(@NonNull Player player) {
        PlayerTasks.run(this.owner, player, () -> {
            MenuViewer viewer = this.getViewer(player);
            if (viewer != null) viewer.refresh();
        });
    }

    @Override
    public void close(@NonNull UUID id) {
        MenuViewer viewer = this.getViewer(id);
        if (viewer == null) return;
        // NightCore remains enabled during a child plugin's disable/reload.
        NightCore core = NightCore.get();
        if (!core.isEnabled()) return;
        core.runTask(viewer.getPlayer(), () -> {
            if (this.regionViewers.remove(id, viewer)) {
                this.registry.unregisterViewer(viewer.getPlayer());
                viewer.closeMenu();
            }
        });
    }
}
