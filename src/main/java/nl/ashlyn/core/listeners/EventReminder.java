package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.EventSignups;
import nl.ashlyn.core.api.ServerEvent;
import org.bukkit.Bukkit;
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
 * Tells players who join about the event that is going on now or comes next,
 * and tells everyone online when a new event is planned.
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

            player.sendMessage(ChatColor.LIGHT_PURPLE + "Volgend event: " + ChatColor.WHITE + next.name() + ChatColor.GRAY + " (" + format(next.time()) + ")");
            if (CoreConfig.isPortalEnabled() && EventSignups.answer(next.time(), player.getUniqueId()) == null) {
                player.sendMessage(ChatColor.GRAY + "Laat op het portaal weten of je komt: " + ChatColor.WHITE + eventsUrl());
            }
        }, 80L);
    }

    /**
     * Tell everyone online that a new event is planned
     * Safe to call from any thread
     * @param name The event name
     * @param time Epoch milliseconds the event starts
     */
    public static void announce(String name, long time) {
        Bukkit.getScheduler().runTask(Core.getInstance(), () -> {
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "Nieuw event gepland: " + ChatColor.WHITE + name + ChatColor.GRAY + " (" + format(time) + ")");
            if (CoreConfig.isPortalEnabled()) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "Laat op het portaal weten of je komt: " + ChatColor.WHITE + eventsUrl());
            }
        });
    }

    private static String format(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.of(CoreConfig.getEventTimezone())).format(OUTPUT);
    }

    private static String eventsUrl() {
        return CoreConfig.getPortalUrl() + "/events";
    }
}
