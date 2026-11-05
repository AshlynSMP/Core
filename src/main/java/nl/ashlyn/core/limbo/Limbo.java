package nl.ashlyn.core.limbo;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Ashlyn on 05/11/2026
 *
 * The limbo. Players who die before the last act wake up at the limbo start,
 * in adventure mode so they can't break anything, and once they reach the exit they go back to their bed, or the world spawn if they have none.
 * Who is in the limbo is kept in limbo.yml, so it survives logging out and restarts.
 * Only touch this from the main thread.
 */
public class Limbo {

    private static Set<String> players;

    private static File file() {
        return new File(Core.getInstance().getDataFolder(), "limbo.yml");
    }

    public static boolean isIn(UUID uuid) {
        return players().contains(uuid.toString());
    }

    public static int count() {
        return players().size();
    }

    public static void enter(Player player) {
        if (players().add(player.getUniqueId().toString())) save();
        player.setGameMode(GameMode.ADVENTURE);
    }

    public static void leave(Player player) {
        if (players().remove(player.getUniqueId().toString())) save();
        player.setGameMode(GameMode.SURVIVAL);
        Location bed = player.getRespawnLocation();
        World world = Bukkit.getWorlds().get(0);
        player.teleport(bed != null ? bed : world.getSpawnLocation().add(0.5, 0, 0.5));
        player.setFallDistance(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1, 1);
    }

    public static boolean atExit(Location location) {
        Location exit = CoreConfig.getLimboExit();
        if (exit == null || !exit.getWorld().equals(location.getWorld())) return false;
        double radius = CoreConfig.getLimboExitRadius();
        return exit.distanceSquared(location) <= radius * radius;
    }

    private static Set<String> players() {
        if (players == null) players = new HashSet<>(YamlConfiguration.loadConfiguration(file()).getStringList("players"));
        return players;
    }

    private static void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("players", new ArrayList<>(players));
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save limbo.yml: " + e.getMessage());
        }
    }
}
