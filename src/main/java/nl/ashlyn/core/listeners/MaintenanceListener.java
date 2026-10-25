package nl.ashlyn.core.listeners;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.commands.MaintenanceCommand;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Ashlyn on 25/10/2026
 *
 * Keeps everyone but leaders and ops out while the server is in maintenance,
 * and shows it in the server list.
 */
public class MaintenanceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!CoreConfig.isMaintenance()) return;
        Player player = event.getPlayer();
        if (MaintenanceCommand.canJoin(player.getName(), player.isOp())) return;
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, MaintenanceCommand.kickMessage());
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!CoreConfig.isMaintenance()) return;
        String[] lines = event.getMotd().split("\n", 2);
        if (lines.length < 2) return;
        Matcher prefix = Pattern.compile("^(?:\\s|§.)*").matcher(lines[1]);
        prefix.find();
        event.setMotd(lines[0] + "\n" + prefix.group() + " ".repeat(7) + ChatColor.RED + "In onderhoud");
    }
}
