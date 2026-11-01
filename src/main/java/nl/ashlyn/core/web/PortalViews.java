package nl.ashlyn.core.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.EventSignups;
import nl.ashlyn.core.api.PlayerLinks;
import nl.ashlyn.core.api.RealNames;
import nl.ashlyn.core.api.ServerEvent;
import nl.ashlyn.core.wrapped.SeasonTracker;
import nl.ashlyn.core.wrapped.StatsTable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Builds what the portal shows a signed-in player. Must be called on the main thread.
 */
public class PortalViews {

    private static final int MAX_GOING_NAMES = 12;

    private final Core plugin;
    private final ActivityFeed activity;
    private final StatsTable stats;

    public PortalViews(Core plugin, ActivityFeed activity, StatsTable stats) {
        this.plugin = plugin;
        this.activity = activity;
        this.stats = stats;
    }

    JsonObject me(WatchdogAuth.Identity identity) {
        JsonObject json = new JsonObject();
        JsonObject account = new JsonObject();
        account.addProperty("name", identity.name());
        json.add("watchdog", account);

        PlayerLinks.Link link = PlayerLinks.get(identity.subject());
        json.addProperty("linked", link != null);
        if (link == null) {
            PlayerLinks.Code code = PlayerLinks.openCode(identity.subject());
            if (code != null) json.add("code", codeJson(code));
            return json;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(link.uuid());
        boolean leader = isLeader(player);
        json.add("player", player(player));
        json.addProperty("leader", leader);
        if (leader) json.addProperty("openRequests", WhitelistRequests.getRequests().size());

        JsonObject table = stats != null ? stats.toJson(link.uuid()) : null;
        if (table != null) json.add("table", table);
        json.add("story", StatusSnapshot.buildStory());
        json.add("online", online());

        ServerEvent next = CoreConfig.getNextEvent();
        if (next != null) json.add("event", event(next, link.uuid()));
        return json;
    }

    JsonObject events(WatchdogAuth.Identity identity) {
        PlayerLinks.Link link = PlayerLinks.get(identity.subject());
        if (link == null) return PortalHandler.error("not-linked");

        long liveUntil = System.currentTimeMillis() - CoreConfig.getEventLiveMillis();
        JsonArray upcoming = new JsonArray();
        JsonArray past = new JsonArray();
        List<ServerEvent> events = CoreConfig.getEvents();
        for (ServerEvent e : events) {
            if (e.time() > liveUntil) upcoming.add(event(e, link.uuid()));
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            ServerEvent e = events.get(i);
            if (e.time() > liveUntil) continue;
            JsonObject json = new JsonObject();
            json.addProperty("name", e.name());
            json.addProperty("time", e.time());
            if (e.recap() != null) json.addProperty("recap", e.recap());
            json.addProperty("attended", EventSignups.attended(e.time(), link.uuid()));
            past.add(json);
        }

        JsonObject json = new JsonObject();
        json.add("upcoming", upcoming);
        json.add("past", past);
        return json;
    }

    static boolean isLeader(OfflinePlayer player) {
        return player.isOp() || player.getName() != null && CoreConfig.isLeader(player.getName());
    }

    static JsonObject codeJson(PlayerLinks.Code code) {
        JsonObject json = new JsonObject();
        json.addProperty("code", code.display());
        json.addProperty("expires", code.expires());
        return json;
    }

    private JsonObject player(OfflinePlayer player) {
        Player online = player.getPlayer();
        JsonObject p = new JsonObject();
        p.addProperty("name", player.getName() != null ? player.getName() : "?");
        p.addProperty("uuid", player.getUniqueId().toString());
        p.addProperty("realName", RealNames.get(player));
        p.addProperty("online", online != null);
        p.addProperty("firstPlayed", player.getFirstPlayed());
        p.addProperty("lastSeen", online != null ? System.currentTimeMillis() : player.getLastPlayed());
        StatusSnapshot.addStats(p, player);
        if (online != null) {
            JsonObject live = new JsonObject();
            live.addProperty("dimension", StatusSnapshot.dimension(online.getWorld()));
            live.addProperty("health", Math.round(online.getHealth() * 10) / 10.0);
            live.addProperty("level", online.getLevel());
            live.addProperty("ping", online.getPing());
            p.add("live", live);
        }

        SeasonTracker tracker = plugin.getSeasonTracker();
        JsonObject season = tracker != null ? tracker.toJson(player.getUniqueId()) : null;
        if (season != null) p.add("season", season);
        if (activity != null && player.getName() != null) p.add("activity", activity.toJson(player.getName()));
        return p;
    }

    private JsonArray online() {
        JsonArray list = new JsonArray();
        if (!plugin.getConfig().getBoolean("web.show-players", true)) return list;
        for (Player player : Bukkit.getOnlinePlayers()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", player.getName());
            p.addProperty("uuid", player.getUniqueId().toString());
            list.add(p);
        }
        return list;
    }

    private static JsonObject event(ServerEvent event, UUID uuid) {
        JsonObject json = new JsonObject();
        json.addProperty("name", event.name());
        json.addProperty("time", event.time());
        json.addProperty("liveMs", CoreConfig.getEventLiveMillis());
        json.addProperty("live", event.hasStarted());

        List<UUID> going = EventSignups.going(event.time());
        json.addProperty("goingCount", going.size());
        JsonArray players = new JsonArray();
        for (UUID other : going.subList(0, Math.min(going.size(), MAX_GOING_NAMES))) {
            String name = Bukkit.getOfflinePlayer(other).getName();
            if (name == null) continue;
            JsonObject p = new JsonObject();
            p.addProperty("name", name);
            p.addProperty("uuid", other.toString());
            players.add(p);
        }
        json.add("going", players);
        Boolean answer = EventSignups.answer(event.time(), uuid);
        if (answer != null) json.addProperty("answer", answer);
        return json;
    }
}
