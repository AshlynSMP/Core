package nl.ashlyn.core.limbo;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Created by Ashlyn on 06/11/2026
 *
 * Draws a particle trail from the limbo start to the limbo exit so players in the limbo know where to walk.
 * The trail is a line of sculk souls at foot height, with a denser pulse running along it towards the exit, so the direction is clear.
 * Only players in the limbo see it, and only the part of it near them.
 */
public class LimboTrail {

    private static final long PERIOD_TICKS = 5L;
    private static final double SPACING = 1;
    private static final double HEIGHT = 0.15;
    private static final double VIEW_DISTANCE = 24;
    private static final double PULSE_STEP = 1.0;
    private static final double PULSE_LENGTH = 2.0;

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

    private void draw() {
        runs++;
        if (!CoreConfig.isLimboEnabled() || Limbo.count() == 0) return;
        Location start = CoreConfig.getLimboStart();
        Location exit = CoreConfig.getLimboExit();
        if (start == null || exit == null || !start.getWorld().equals(exit.getWorld())) return;

        Vector from = start.toVector().add(new Vector(0, HEIGHT, 0));
        Vector path = exit.toVector().add(new Vector(0, HEIGHT, 0)).subtract(from);
        double length = path.length();
        if (length < SPACING) return;
        Vector direction = path.clone().multiply(1 / length);
        double pulse = (runs * PULSE_STEP) % length;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(start.getWorld()) || !Limbo.isIn(player.getUniqueId())) continue;
            Vector eye = player.getLocation().toVector();

            for (double d = 0; d <= length; d += SPACING) show(player, from, direction, d, eye, start);
            for (double d = pulse; d <= Math.min(length, pulse + PULSE_LENGTH); d += SPACING / 2) {
                show(player, from, direction, d, eye, start);
            }
        }
    }

    private static void show(Player player, Vector from, Vector direction, double distance, Vector eye, Location start) {
        Vector point = from.clone().add(direction.clone().multiply(distance));
        if (point.distanceSquared(eye) > VIEW_DISTANCE * VIEW_DISTANCE) return;
        player.spawnParticle(Particle.SCULK_SOUL, point.toLocation(start.getWorld()), 1, 0, 0, 0, 0);
    }
}
