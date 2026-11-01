package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.EventSignups;
import nl.ashlyn.core.api.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Remembers who was online while an event went on, so the portal can show who was there.
 * Checks everyone once a minute and every player who joins during an event.
 */
public class EventAttendance implements Listener {

    private static final long MINUTE_TICKS = 20L * 60;

    public EventAttendance() {
        Bukkit.getScheduler().runTaskTimer(Core.getInstance(), () -> {
            ServerEvent event = liveEvent();
            if (event == null || Bukkit.getOnlinePlayers().isEmpty()) return;
            EventSignups.markAttended(event.time(), Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());
        }, MINUTE_TICKS, MINUTE_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ServerEvent live = liveEvent();
        if (live != null) EventSignups.markAttended(live.time(), List.of(event.getPlayer().getUniqueId()));
    }

    private static ServerEvent liveEvent() {
        ServerEvent next = CoreConfig.getNextEvent();
        return next != null && next.hasStarted() ? next : null;
    }
}
