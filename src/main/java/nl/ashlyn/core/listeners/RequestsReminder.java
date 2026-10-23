package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.web.WhitelistRequests;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Tells leaders who join how many whitelist requests are waiting for them.
 */
public class RequestsReminder implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() && !CoreConfig.isLeader(player.getName())) return;

        // A moment later, so the message doesn't drown in the join spam
        Core.getInstance().getServer().getScheduler().runTaskLater(Core.getInstance(), () -> {
            int open = WhitelistRequests.getRequests().size();
            if (open == 0 || !player.isOnline()) return;
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Er " + (open == 1 ? "staat 1 whitelist-aanvraag" : "staan " + open + " whitelist-aanvragen")
                    + " open." + ChatColor.GRAY + " Bekijk ze met /aanvragen");
        }, 60L);
    }
}
