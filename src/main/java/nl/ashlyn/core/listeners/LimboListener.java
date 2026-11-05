package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.arrival.Arrival;
import nl.ashlyn.core.limbo.Limbo;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Created by Ashlyn on 05/11/2026
 *
 * Sends players who die to the limbo start in adventure mode instead of their bed or the spawn,
 * and back to their bed or the world spawn once they walk into the limbo exit.
 * Does nothing in the last act, there the FinaleListener takes over.
 */
public class LimboListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!CoreConfig.isLimboEnabled() || CoreConfig.isFinalAct()) return;
        if (CoreConfig.isArrivalEnabled() && !Arrival.hasArrived(player.getUniqueId())) return;
        Location start = CoreConfig.getLimboStart();
        if (start == null) return;

        event.setRespawnLocation(start);
        Core plugin = Core.getInstance();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) Limbo.enter(player);
        });
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        player.playSound(start, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1, 0.6f);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (Limbo.isIn(player.getUniqueId()) && player.getGameMode() == GameMode.SURVIVAL) player.setGameMode(GameMode.ADVENTURE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || event.getFrom().getBlock().equals(to.getBlock())) return;
        Player player = event.getPlayer();
        if (!Limbo.isIn(player.getUniqueId()) || !Limbo.atExit(to)) return;

        Limbo.leave(player);
    }
}
