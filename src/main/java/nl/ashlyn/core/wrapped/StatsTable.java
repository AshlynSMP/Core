package nl.ashlyn.core.wrapped;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nl.ashlyn.core.Core;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Every player's season stats in one table, so the portal can show ranks and averages.
 * Minecraft's own statistics are read from the world's stats files off the main thread,
 * and the whole table is rebuilt every few minutes.
 */
public class StatsTable {

    public static final String[] KEYS = {
            "playtimeMinutes", "daysPlayed", "longestSessionMinutes", "advancements", "deaths", "mobKills",
            "playerKills", "distanceKm", "blocksMined", "jumps", "fishCaught", "animalsBred", "villagerTrades"
    };
    private static final long REFRESH_TICKS = 20L * 60 * 5;

    private final Core plugin;
    private final SeasonTracker tracker;
    private BukkitTask task;
    private volatile Map<UUID, Row> rows = Map.of();

    private record Row(double[] values, int[] hourMinutes, int[] dimensionMinutes) {}

    private record Source(UUID uuid, int[] season, int[] hourMinutes, int[] dimensionMinutes) {}

    public StatsTable(Core plugin, SeasonTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 40L, REFRESH_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /**
     * Get one player's stats with their rank and the average of everyone
     * @param uuid The player
     * @return The stats, or null if the player isn't in the table yet
     */
    public JsonObject toJson(UUID uuid) {
        Map<UUID, Row> table = rows;
        Row row = table.get(uuid);
        if (row == null) return null;

        JsonObject json = new JsonObject();
        JsonObject stats = new JsonObject();
        for (int i = 0; i < KEYS.length; i++) {
            double value = row.values()[i];
            int rank = 1;
            double sum = 0;
            for (Row other : table.values()) {
                if (other.values()[i] > value) rank++;
                sum += other.values()[i];
            }
            JsonObject stat = new JsonObject();
            stat.addProperty("value", value);
            stat.addProperty("rank", rank);
            stat.addProperty("average", Math.round(sum / table.size() * 100) / 100.0);
            stats.add(KEYS[i], stat);
        }
        json.add("stats", stats);
        json.addProperty("players", table.size());

        JsonArray hours = new JsonArray();
        for (int minutes : row.hourMinutes()) hours.add(minutes);
        json.add("hourMinutes", hours);
        JsonObject dimensions = new JsonObject();
        for (int i = 0; i < SeasonStats.DIMENSIONS.length; i++) dimensions.addProperty(SeasonStats.DIMENSIONS[i], row.dimensionMinutes()[i]);
        json.add("dimensionMinutes", dimensions);
        return json;
    }

    private void refresh() {
        Bukkit.savePlayers();
        File statsFolder = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "stats");
        Map<UUID, SeasonStats> season = tracker.getStats();
        List<Source> sources = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null || !player.hasPlayedBefore() && !player.isOnline()) continue;
            SeasonStats s = season.get(player.getUniqueId());
            sources.add(s == null
                    ? new Source(player.getUniqueId(), new int[3], new int[24], new int[SeasonStats.DIMENSIONS.length])
                    : new Source(player.getUniqueId(), new int[]{s.daysPlayed, s.longestSessionMinutes, s.advancements},
                    s.hourMinutes.clone(), s.dimensionMinutes.clone()));
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Row> built = new HashMap<>();
            for (Source source : sources) {
                JsonObject vanilla = WrappedExporter.readStats(new File(statsFolder, source.uuid() + ".json"));
                JsonObject custom = WrappedExporter.section(vanilla, "minecraft:custom");
                long onFoot = 0;
                for (String key : WrappedExporter.ON_FOOT) onFoot += WrappedExporter.number(custom, key);
                double[] values = {
                        WrappedExporter.number(custom, "play_time") / 1200,
                        source.season()[0],
                        source.season()[1],
                        source.season()[2],
                        WrappedExporter.number(custom, "deaths"),
                        WrappedExporter.number(custom, "mob_kills"),
                        WrappedExporter.number(custom, "player_kills"),
                        Math.round(onFoot / 1000.0) / 100.0,
                        WrappedExporter.total(WrappedExporter.section(vanilla, "minecraft:mined")),
                        WrappedExporter.number(custom, "jump"),
                        WrappedExporter.number(custom, "fish_caught"),
                        WrappedExporter.number(custom, "animals_bred"),
                        WrappedExporter.number(custom, "traded_with_villager")
                };
                built.put(source.uuid(), new Row(values, source.hourMinutes(), source.dimensionMinutes()));
            }
            rows = Map.copyOf(built);
        });
    }
}
