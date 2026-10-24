package nl.ashlyn.core.wrapped;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.web.StatusSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * Writes wrapped.json for the website: Minecraft's own statistics for every player,
 * read straight from the world's stats files, plus what the season tracker collected,
 * and the story and event timeline so the end page works without the server.
 */
public class WrappedExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String[] ON_FOOT = {
            "walk_one_cm", "sprint_one_cm", "crouch_one_cm", "swim_one_cm",
            "walk_on_water_one_cm", "walk_under_water_one_cm", "climb_one_cm"
    };

    private final Core plugin;
    private final SeasonTracker tracker;

    public WrappedExporter(Core plugin, SeasonTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    public Result export() throws IOException {
        Bukkit.savePlayers();

        File statsFolder = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "stats");
        Map<UUID, SeasonStats> season = tracker.getStats();
        JsonArray players = new JsonArray();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null || !player.hasPlayedBefore() && !player.isOnline()) continue;
            JsonObject vanilla = readStats(new File(statsFolder, player.getUniqueId() + ".json"));
            players.add(toJson(player, vanilla, season.get(player.getUniqueId())));
        }

        JsonObject json = new JsonObject();
        json.addProperty("exported", System.currentTimeMillis());
        json.addProperty("timezone", CoreConfig.getEventTimezone());
        json.add("players", players);
        json.add("story", StatusSnapshot.buildStory());
        json.add("timeline", StatusSnapshot.buildTimeline());

        File out = new File(plugin.getDataFolder(), "wrapped.json");
        try (Writer writer = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
        return new Result(out, players.size());
    }

    public record Result(File file, int players) {}

    private static JsonObject toJson(OfflinePlayer player, JsonObject vanilla, SeasonStats season) {
        JsonObject custom = section(vanilla, "minecraft:custom");

        JsonObject p = new JsonObject();
        p.addProperty("name", player.getName());
        p.addProperty("uuid", player.getUniqueId().toString());
        p.addProperty("leader", CoreConfig.isLeader(player.getName()));
        p.addProperty("firstPlayed", player.getFirstPlayed());
        p.addProperty("playtimeMinutes", number(custom, "play_time") / 1200);
        p.addProperty("deaths", number(custom, "deaths"));
        p.addProperty("mobKills", number(custom, "mob_kills"));
        p.addProperty("playerKills", number(custom, "player_kills"));
        p.addProperty("jumps", number(custom, "jump"));
        p.addProperty("fishCaught", number(custom, "fish_caught"));
        p.addProperty("animalsBred", number(custom, "animals_bred"));
        p.addProperty("villagerTrades", number(custom, "traded_with_villager"));
        long onFoot = 0;
        for (String key : ON_FOOT) onFoot += number(custom, key);
        p.addProperty("distanceKm", Math.round(onFoot / 1000.0) / 100.0);
        p.addProperty("blocksMined", total(section(vanilla, "minecraft:mined")));

        if (season != null) {
            JsonObject dimensions = new JsonObject();
            for (int i = 0; i < SeasonStats.DIMENSIONS.length; i++) {
                dimensions.addProperty(SeasonStats.DIMENSIONS[i], season.dimensionMinutes[i]);
            }
            p.add("dimensionMinutes", dimensions);
            JsonArray hours = new JsonArray();
            for (int minutes : season.hourMinutes) hours.add(minutes);
            p.add("hourMinutes", hours);
            p.addProperty("daysPlayed", season.daysPlayed);
            p.addProperty("longestSessionMinutes", season.longestSessionMinutes);
            p.addProperty("advancements", season.advancements);
            if (season.firstAdvancement != null) {
                JsonObject first = new JsonObject();
                first.addProperty("title", season.firstAdvancement);
                first.addProperty("time", season.firstAdvancementTime);
                p.add("firstAdvancement", first);
            }
        }
        return p;
    }

    private static JsonObject readStats(File file) {
        if (!file.isFile()) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject() && root.getAsJsonObject().has("stats")
                    ? root.getAsJsonObject().getAsJsonObject("stats") : new JsonObject();
        } catch (IOException | RuntimeException e) {
            return new JsonObject();
        }
    }

    private static JsonObject section(JsonObject stats, String key) {
        return stats.has(key) && stats.get(key).isJsonObject() ? stats.getAsJsonObject(key) : new JsonObject();
    }

    private static long number(JsonObject section, String key) {
        JsonElement value = section.get("minecraft:" + key);
        return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0;
    }

    private static long total(JsonObject section) {
        long sum = 0;
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) sum += entry.getValue().getAsLong();
        }
        return sum;
    }
}
