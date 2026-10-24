package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.arrival.Arrival;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * Sends players who have not reached the forest yet to the hok, with their name paper.
 * Once the arrival has started, they move on to the forest after a few seconds.
 */
public class ArrivalListener implements Listener {

    private static final long FOREST_DELAY = 100L;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!CoreConfig.isArrivalEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (Arrival.hasArrived(uuid)) return;

        if (!Arrival.hasPaper(uuid)) Arrival.givePaper(player);

        Core plugin = Core.getInstance();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) Arrival.sendToHok(player);
        });
        if (!CoreConfig.isArrivalStarted()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !Arrival.hasArrived(uuid)) Arrival.sendToForest(player);
        }, FOREST_DELAY);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!CoreConfig.isArrivalEnabled() || Arrival.hasArrived(event.getPlayer().getUniqueId())) return;
        Location hok = CoreConfig.getHok();
        if (hok != null) event.setRespawnLocation(hok);
    }
}
