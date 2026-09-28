package su.nightexpress.excellenteconomy.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.nightcore.util.Version;

public final class PlayerTasks {

    private PlayerTasks() {
    }

    public static void run(@NonNull EconomyPlugin plugin, @NonNull CommandSender sender,
                           @NonNull Runnable action) {
        if (sender instanceof Player player && Version.isFolia()) {
            plugin.runTask(player, () -> {
                if (player.isOnline()) action.run();
            });
        }
        else {
            action.run();
        }
    }
}
