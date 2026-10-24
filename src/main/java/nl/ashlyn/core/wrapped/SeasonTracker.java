package nl.ashlyn.core.wrapped;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * Keeps track of the season stats Minecraft doesn't store itself, for Ashlyn Wrapped:
 * time per dimension, which hours people play, days played, longest session and advancements.
 * Everything runs on the main thread and is saved to wrapped.yml.
 */
public class SeasonTracker implements Listener {

    private static final long MINUTE_TICKS = 20L * 60;
    private static final long SAVE_TICKS = MINUTE_TICKS * 5;

    private final Core plugin;
    private final Map<UUID, SeasonStats> stats = new HashMap<>();
    private final Map<UUID, Long> sessionStarts = new HashMap<>();
    private BukkitTask minuteTask;
    private BukkitTask saveTask;

    public SeasonTracker(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        load();
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) sessionStarts.put(player.getUniqueId(), now);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        minuteTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, MINUTE_TICKS, MINUTE_TICKS);
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::save, SAVE_TICKS, SAVE_TICKS);
    }

    public void stop() {
        if (minuteTask != null) minuteTask.cancel();
        if (saveTask != null) saveTask.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) endSession(player);
        save();
    }

    public Map<UUID, SeasonStats> getStats() {
        long now = System.currentTimeMillis();
        sessionStarts.forEach((uuid, start) -> {
            SeasonStats s = stats.get(uuid);
            if (s != null) s.longestSessionMinutes = Math.max(s.longestSessionMinutes, minutesSince(start, now));
        });
        return Collections.unmodifiableMap(stats);
    }

    public void reset() {
        stats.clear();
        long now = System.currentTimeMillis();
        sessionStarts.replaceAll((uuid, start) -> now);
        for (Player player : plugin.getServer().getOnlinePlayers()) stats.put(player.getUniqueId(), new SeasonStats(player.getName()));
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        get(player);
        sessionStarts.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        Player player = event.getPlayer();
        if (display == null || !display.shouldAnnounceChat()) return;
        if (Boolean.FALSE.equals(player.getWorld().getGameRuleValue(GameRule.ANNOUNCE_ADVANCEMENTS))) return;

        SeasonStats s = get(player);
        s.advancements++;
        if (s.firstAdvancement == null) {
            s.firstAdvancement = display.getTitle();
            s.firstAdvancementTime = System.currentTimeMillis();
        }
    }

    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(CoreConfig.getEventTimezone()));
        String today = now.toLocalDate().toString();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            SeasonStats s = get(player);
            s.dimensionMinutes[dimensionIndex(player.getWorld())]++;
            s.hourMinutes[now.getHour()]++;
            if (!today.equals(s.lastDay)) {
                s.daysPlayed++;
                s.lastDay = today;
            }
        }
    }

    private void endSession(Player player) {
        Long start = sessionStarts.remove(player.getUniqueId());
        if (start == null) return;
        SeasonStats s = get(player);
        s.longestSessionMinutes = Math.max(s.longestSessionMinutes, minutesSince(start, System.currentTimeMillis()));
    }

    private SeasonStats get(Player player) {
        SeasonStats s = stats.computeIfAbsent(player.getUniqueId(), uuid -> new SeasonStats(player.getName()));
        s.name = player.getName();
        return s;
    }

    private static int dimensionIndex(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> 1;
            case THE_END -> 2;
            default -> 0;
        };
    }

    private static int minutesSince(long start, long now) {
        return (int) ((now - start) / 60000);
    }

    private File file() {
        return new File(plugin.getDataFolder(), "wrapped.yml");
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) continue;
            SeasonStats s = new SeasonStats(section.getString("name", key));
            fill(s.dimensionMinutes, section.getIntegerList("dimension-minutes"));
            fill(s.hourMinutes, section.getIntegerList("hour-minutes"));
            s.daysPlayed = section.getInt("days-played");
            s.lastDay = section.getString("last-day");
            s.longestSessionMinutes = section.getInt("longest-session-minutes");
            s.advancements = section.getInt("advancements");
            s.firstAdvancement = section.getString("first-advancement");
            s.firstAdvancementTime = section.getLong("first-advancement-time");
            stats.put(UUID.fromString(key), s);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        stats.forEach((uuid, s) -> {
            String key = uuid.toString();
            yaml.set(key + ".name", s.name);
            yaml.set(key + ".dimension-minutes", toList(s.dimensionMinutes));
            yaml.set(key + ".hour-minutes", toList(s.hourMinutes));
            yaml.set(key + ".days-played", s.daysPlayed);
            yaml.set(key + ".last-day", s.lastDay);
            yaml.set(key + ".longest-session-minutes", s.longestSessionMinutes);
            yaml.set(key + ".advancements", s.advancements);
            yaml.set(key + ".first-advancement", s.firstAdvancement);
            yaml.set(key + ".first-advancement-time", s.firstAdvancementTime);
        });
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save wrapped.yml: " + e.getMessage());
        }
    }

    private static void fill(int[] target, List<Integer> values) {
        for (int i = 0; i < Math.min(target.length, values.size()); i++) target[i] = values.get(i);
    }

    private static List<Integer> toList(int[] values) {
        return IntStream.of(values).boxed().toList();
    }
}
