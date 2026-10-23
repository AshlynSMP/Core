package nl.ashlyn.core.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Builds the JSON status the website reads. Must be called on the main thread,
 * the web server only ever serves the last built snapshot.
 */
public class StatusSnapshot {

    private static final String[] ASHLYN_PLUGINS = {"Core", "Radio", "Credits", "Propaganda"};

    public static JsonObject build(boolean showPlayers, JsonArray roster, ActivityFeed activity) {
        JsonObject json = new JsonObject();
        json.addProperty("online", true);
        json.addProperty("version", Bukkit.getBukkitVersion().split("-")[0]);
        json.addProperty("updated", System.currentTimeMillis());

        json.add("players", buildPlayers(showPlayers));
        json.add("roster", showPlayers ? roster : new JsonArray());
        json.add("activity", activity != null ? activity.toJson() : new JsonArray());
        json.addProperty("lore", CoreConfig.isLoreEnabled());
        json.add("story", buildStory());
        json.add("event", buildEvent());
        json.add("timeline", buildTimeline());
        json.add("ceasefire", buildCeasefire());
        json.add("radio", buildRadio());
        json.add("world", buildWorld());
        json.add("plugins", buildPlugins());

        return json;
    }

    private static JsonObject buildPlayers(boolean showPlayers) {
        JsonObject players = new JsonObject();
        players.addProperty("online", Bukkit.getOnlinePlayers().size());
        players.addProperty("max", Bukkit.getMaxPlayers());

        JsonArray list = new JsonArray();
        if (showPlayers) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                JsonObject p = new JsonObject();
                p.addProperty("name", player.getName());
                p.addProperty("uuid", player.getUniqueId().toString());
                p.addProperty("leader", CoreConfig.isLeader(player.getName()));
                p.addProperty("op", player.isOp());
                p.addProperty("health", Math.round(player.getHealth() * 10) / 10.0);
                p.addProperty("food", player.getFoodLevel());
                p.addProperty("level", player.getLevel());
                p.addProperty("dimension", dimension(player.getWorld()));
                p.addProperty("ping", player.getPing());
                addStats(p, player);
                list.add(p);
            }
        }
        players.add("list", list);
        return players;
    }

    public static JsonArray buildRoster() {
        JsonArray roster = new JsonArray();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null || !player.hasPlayedBefore() && !player.isOnline()) continue;

            JsonObject p = new JsonObject();
            p.addProperty("name", player.getName());
            p.addProperty("uuid", player.getUniqueId().toString());
            p.addProperty("online", player.isOnline());
            p.addProperty("leader", CoreConfig.isLeader(player.getName()));
            p.addProperty("firstPlayed", player.getFirstPlayed());
            p.addProperty("lastSeen", player.isOnline() ? System.currentTimeMillis() : player.getLastPlayed());
            addStats(p, player);
            roster.add(p);
        }
        return roster;
    }

    private static void addStats(JsonObject json, OfflinePlayer player) {
        json.addProperty("playtimeMinutes", stat(player, Statistic.PLAY_ONE_MINUTE) / 20 / 60);
        json.addProperty("deaths", stat(player, Statistic.DEATHS));
        json.addProperty("mobKills", stat(player, Statistic.MOB_KILLS));
        json.addProperty("playerKills", stat(player, Statistic.PLAYER_KILLS));
    }

    private static int stat(OfflinePlayer player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static String dimension(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "overworld";
        };
    }

    private static JsonObject buildStory() {
        JsonObject story = new JsonObject();
        int act = CoreConfig.getAct();
        story.addProperty("act", act);
        story.addProperty("total", CoreConfig.getTotalActs());

        JsonArray acts = new JsonArray();
        for (int i = 1; i <= act; i++) {
            JsonObject json = new JsonObject();
            json.addProperty("number", i);
            json.addProperty("title", CoreConfig.getActTitle(i));
            String info = CoreConfig.getActInfo(i);
            if (info != null) json.addProperty("info", info);
            acts.add(json);
        }
        story.add("acts", acts);
        return story;
    }

    private static JsonObject buildEvent() {
        ServerEvent next = CoreConfig.getNextEvent();
        if (next == null) return null;

        JsonObject event = new JsonObject();
        event.addProperty("name", next.name());
        event.addProperty("time", next.time());
        event.addProperty("liveMs", CoreConfig.getEventLiveMillis());
        return event;
    }

    private static JsonArray buildTimeline() {
        JsonArray timeline = new JsonArray();
        List<ServerEvent> events = CoreConfig.getEvents();
        for (int i = events.size() - 1; i >= 0; i--) {
            ServerEvent e = events.get(i);
            JsonObject json = new JsonObject();
            json.addProperty("name", e.name());
            json.addProperty("time", e.time());
            if (e.recap() != null) json.addProperty("recap", e.recap());
            timeline.add(json);
        }
        return timeline;
    }

    private static JsonObject buildCeasefire() {
        JsonObject ceasefire = new JsonObject();
        FileConfiguration config = getPluginConfig("Propaganda");
        ceasefire.addProperty("available", config != null);
        ceasefire.addProperty("active", config != null && config.getBoolean("pvp-disabled", false));
        return ceasefire;
    }

    private static JsonObject buildRadio() {
        JsonObject radio = new JsonObject();
        FileConfiguration config = getPluginConfig("Radio");
        radio.addProperty("available", config != null);

        boolean noord = config == null || config.getBoolean("tower-noord", true);
        boolean zuid = config == null || config.getBoolean("tower-zuid", true);

        JsonObject towers = new JsonObject();
        towers.addProperty("noord", noord);
        towers.addProperty("zuid", zuid);
        radio.add("towers", towers);

        String signal = noord && zuid ? "clear" : (noord || zuid ? "scrambled" : "offline");
        radio.addProperty("signal", signal);
        return radio;
    }

    private static JsonObject buildWorld() {
        JsonObject json = new JsonObject();
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) return json;

        json.addProperty("time", world.getTime());
        json.addProperty("day", world.getFullTime() / 24000L + 1);
        json.addProperty("storm", world.hasStorm());
        json.addProperty("thunder", world.isThundering());
        json.addProperty("border", world.getWorldBorder().getSize());
        return json;
    }

    private static JsonArray buildPlugins() {
        JsonArray plugins = new JsonArray();
        for (String name : ASHLYN_PLUGINS) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            JsonObject p = new JsonObject();
            p.addProperty("name", name);
            p.addProperty("installed", plugin != null);
            p.addProperty("enabled", plugin != null && plugin.isEnabled());
            if (plugin != null) p.addProperty("version", plugin.getDescription().getVersion());
            plugins.add(p);
        }
        return plugins;
    }

    private static FileConfiguration getPluginConfig(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin.getConfig() : null;
    }
}
