package nl.ashlyn.core.arrival;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Ashlyn on 06/11/2026
 *
 * Draws a private particle trail for every player from the spot where they landed in the forest to the arrival target.
 * Only that player sees their own trail. The part they already walked disappears, and once they reach the target the trail is gone.
 * The trail follows a walkable path (see ArrivalPath), so it goes around trees and over hills instead of through them.
 * The path is found again from where the player is every 2 seconds, so it keeps up as they walk.
 * Where each trail starts is kept in arrivals.yml under "trail", so it survives logging out and restarts.
 * The arrival starts in peaceful; once the last player reaches the target the world goes to normal.
 * Only touch this from the main thread.
 */
public class ArrivalTrail {

    private static final long PERIOD_TICKS = 10L;
    /** End rods stay around for about 3 seconds, so the line only has to be redrawn every 2 seconds. */
    private static final int LINE_EVERY = 4;
    private static final double SPACING = 0.2;
    private static final double HEIGHT = 1.3;
    private static final int AHEAD = 2;
    private static final double VIEW_DISTANCE = 32;
    private static final double MOVED = 1.0;

    private static Map<UUID, Vector> starts;
    private static final Map<UUID, List<Vector>> paths = new HashMap<>();
    private static final Map<UUID, Vector> pathFrom = new HashMap<>();

    private BukkitTask task;
    private long runs;

    public void start() {
        Core plugin = Core.getInstance();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::draw, 20L, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /**
     * Give a player a trail from where they are now to the arrival target, if a target is set in their world
     */
    public static void begin(Player player) {
        Location target = CoreConfig.getArrivalTarget();
        if (target == null || !target.getWorld().equals(player.getWorld())) return;
        paths.remove(player.getUniqueId());
        pathFrom.remove(player.getUniqueId());
        starts().put(player.getUniqueId(), player.getLocation().toVector());
        save();
    }

    /**
     * Take away a player's trail
     */
    public static void end(UUID uuid) {
        paths.remove(uuid);
        pathFrom.remove(uuid);
        if (starts().remove(uuid) == null) return;
        save();
        if (!starts.isEmpty()) return;
        Location target = CoreConfig.getArrivalTarget();
        if (target != null) target.getWorld().setDifficulty(Difficulty.NORMAL);
    }

    private void draw() {
        runs++;
        if (!CoreConfig.isArrivalEnabled() || starts().isEmpty()) return;
        Location target = CoreConfig.getArrivalTarget();
        if (target == null) return;
        World world = target.getWorld();
        double radius = CoreConfig.getArrivalTargetRadius();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!starts().containsKey(uuid) || !player.getWorld().equals(world)) continue;
            Location location = player.getLocation();
            Vector position = location.toVector();

            double dx = target.getX() - position.getX();
            double dz = target.getZ() - position.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                end(uuid);
                continue;
            }

            // Spread the work: every player gets their line on a different run
            if ((runs + Math.floorMod(uuid.hashCode(), LINE_EVERY)) % LINE_EVERY == 0 || !paths.containsKey(uuid)) {
                Vector last = pathFrom.get(uuid);
                if (last == null || last.distanceSquared(position) >= MOVED * MOVED) {
                    paths.put(uuid, ArrivalPath.find(world, location, target));
                    pathFrom.put(uuid, position);
                }
                line(player, world, paths.get(uuid), position);
            }

            List<Vector> path = paths.get(uuid);
            if (path.size() > AHEAD + 1) {
                Vector pulse = path.get(AHEAD + (int) (runs % (path.size() - AHEAD)));
                show(player, world, pulse, position);
            }
        }
    }

    private static void line(Player player, World world, List<Vector> path, Vector position) {
        for (int i = AHEAD; i < path.size() - 1; i++) {
            Vector from = path.get(i);
            Vector step = path.get(i + 1).clone().subtract(from);
            double length = step.length();
            for (double d = 0; d < length; d += SPACING) {
                show(player, world, from.clone().add(step.clone().multiply(d / length)), position);
            }
        }
        if (path.size() > AHEAD) show(player, world, path.get(path.size() - 1), position);
    }

    private static void show(Player player, World world, Vector feet, Vector position) {
        double dx = feet.getX() - position.getX();
        double dz = feet.getZ() - position.getZ();
        if (dx * dx + dz * dz > VIEW_DISTANCE * VIEW_DISTANCE) return;
        player.spawnParticle(Particle.END_ROD, new Location(world, feet.getX(), feet.getY() + HEIGHT, feet.getZ()), 1, 0, 0, 0, 0);
    }

    private static File file() {
        return new File(Core.getInstance().getDataFolder(), "arrivals.yml");
    }

    private static Map<UUID, Vector> starts() {
        if (starts != null) return starts;
        starts = new HashMap<>();
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file()).getConfigurationSection("trail");
        if (section == null) return starts;
        for (String key : section.getKeys(false)) {
            ConfigurationSection spot = section.getConfigurationSection(key);
            if (spot == null) continue;
            try {
                starts.put(UUID.fromString(key), new Vector(spot.getDouble("x"), spot.getDouble("y"), spot.getDouble("z")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return starts;
    }

    private static void save() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        yaml.set("trail", null);
        for (Map.Entry<UUID, Vector> entry : starts.entrySet()) {
            String path = "trail." + entry.getKey();
            yaml.set(path + ".x", entry.getValue().getX());
            yaml.set(path + ".y", entry.getValue().getY());
            yaml.set(path + ".z", entry.getValue().getZ());
        }
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save arrivals.yml: " + e.getMessage());
        }
    }
}
