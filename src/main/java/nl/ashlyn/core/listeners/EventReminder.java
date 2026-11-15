package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.EventSignups;
import nl.ashlyn.core.api.ServerEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Created by Ashlyn on 15/11/2026
 *
 * Tells players who join about the event that is going on now or comes next.
 */
public class EventReminder implements Listener {

    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("EEEE d MMMM 'om' HH:mm", Locale.of("nl", "NL"));

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Core.getInstance().getServer().getScheduler().runTaskLater(Core.getInstance(), () -> {
            ServerEvent next = CoreConfig.getNextEvent();
            if (next == null || !player.isOnline()) return;

            if (next.hasStarted()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Het event " + ChatColor.WHITE + next.name() + ChatColor.LIGHT_PURPLE + " is nu bezig!");
                return;
            }

            String when = Instant.ofEpochMilli(next.time()).atZone(ZoneId.of(CoreConfig.getEventTimezone())).format(OUTPUT);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Volgend event: " + ChatColor.WHITE + next.name() + ChatColor.GRAY + " (" + when + ")");
            if (CoreConfig.isPortalEnabled() && EventSignups.answer(next.time(), player.getUniqueId()) == null) {
                player.sendMessage(ChatColor.GRAY + "Laat op het portaal weten of je komt: " + ChatColor.WHITE + CoreConfig.getPortalUrl() + "/events");
            }
        }, 80L);
    }
}
