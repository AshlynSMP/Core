package nl.ashlyn.core.arrival;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.RealNames;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * The release arrival. New players get a paper with their real name and wait together in the hok,
 * until a leader spreads them over the forest and they see the Act 1 title from the resource pack.
 * Who already got there is kept in arrivals.yml.
 * Only touch this from the main thread.
 */
public class Arrival {

    private static final Random RANDOM = new Random();
    private static final int ATTEMPTS = 300;
    private static final long TICKS_BETWEEN = 2L;
    private static final long NIGHT = 14000L;
    private static final String TITLE = "{\"text\":\"幕\uDAC0\uDC00\uDAC0\uDC00\uDAC0\uDC00\uDAFF\uDFFF壱\",\"shadow_color\":false}";
    private static final Set<Material> UNSAFE = Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.POINTED_DRIPSTONE);

    private static File file() {
        return new File(Core.getInstance().getDataFolder(), "arrivals.yml");
    }

    public static boolean hasArrived(UUID uuid) {
        return list("arrived").contains(uuid.toString());
    }

    public static void setArrived(UUID uuid, boolean arrived) {
        mark("arrived", uuid, arrived);
    }

    public static boolean hasPaper(UUID uuid) {
        return list("paper").contains(uuid.toString());
    }

    public static List<Player> waiting() {
        Set<String> arrived = list("arrived");
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!arrived.contains(player.getUniqueId().toString())) players.add(player);
        }
        return players;
    }

    public static void givePaper(Player player) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + RealNames.get(player));
        paper.setItemMeta(meta);
        player.getInventory().addItem(paper).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        mark("paper", player.getUniqueId(), true);
    }

    public static void sendToHok(Player player) {
        Location hok = CoreConfig.getHok();
        if (hok != null) player.teleport(hok);
    }

    public static int start() {
        Location center = CoreConfig.getForestCenter();
        if (center != null) center.getWorld().setTime(NIGHT);
        List<Player> players = waiting();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Bukkit.getScheduler().runTaskLater(Core.getInstance(), () -> {
                if (player.isOnline() && !hasArrived(player.getUniqueId())) sendToForest(player);
            }, i * TICKS_BETWEEN);
        }
        return players.size();
    }

    public static boolean sendToForest(Player player) {
        Location center = CoreConfig.getForestCenter();
        if (center == null) return false;

        Location spot = findSpot(center, player);
        if (spot == null) {
            Logger.logCore(ChatColor.RED + "No safe spot found in the forest for " + player.getName() + ", using the center");
            spot = center.getWorld().getHighestBlockAt(center).getLocation().add(0.5, 1, 0.5);
        }

        player.teleport(spot);
        setArrived(player.getUniqueId(), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        sendTitle(player);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1, 1);
        return true;
    }

    private static void sendTitle(Player player) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title " + player.getName() + " times 20 80 30");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title " + player.getName() + " title " + TITLE);
    }

    private static Location findSpot(Location center, Player player) {
        World world = center.getWorld();
        Set<String> arrived = list("arrived");
        List<Location> others = new ArrayList<>();
        for (Player other : world.getPlayers()) {
            if (!other.equals(player) && arrived.contains(other.getUniqueId().toString())) others.add(other.getLocation());
        }

        double radius = CoreConfig.getForestRadius();
        double minDistance = Math.pow(CoreConfig.getForestMinDistance(), 2);
        Location best = null;
        double bestDistance = -1;

        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = radius * Math.sqrt(RANDOM.nextDouble());
            Location spot = ground(world, (int) Math.floor(center.getX() + Math.cos(angle) * distance),
                    (int) Math.floor(center.getZ() + Math.sin(angle) * distance));
            if (spot == null) continue;

            double nearest = Double.MAX_VALUE;
            for (Location other : others) {
                double dx = other.getX() - spot.getX();
                double dz = other.getZ() - spot.getZ();
                nearest = Math.min(nearest, dx * dx + dz * dz);
            }
            if (nearest >= minDistance) return spot;
            if (nearest > bestDistance) {
                best = spot;
                bestDistance = nearest;
            }
        }
        return best;
    }

    private static Location ground(World world, int x, int z) {
        if (!world.getWorldBorder().isInside(new Location(world, x, 0, z))) return null;
        Block block = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Material type = block.getType();
        if (block.isLiquid() || !type.isSolid() || Tag.LOGS.isTagged(type) || UNSAFE.contains(type)) return null;
        if (!block.getRelative(BlockFace.UP).isPassable() || !block.getRelative(BlockFace.UP, 2).isPassable()) return null;
        if (block.getRelative(BlockFace.UP).isLiquid()) return null;
        return new Location(world, x + 0.5, block.getY() + 1, z + 0.5, RANDOM.nextFloat() * 360, 0);
    }

    private static Set<String> list(String key) {
        return new HashSet<>(YamlConfiguration.loadConfiguration(file()).getStringList(key));
    }

    private static void mark(String key, UUID uuid, boolean on) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        Set<String> set = new HashSet<>(yaml.getStringList(key));
        if (on ? !set.add(uuid.toString()) : !set.remove(uuid.toString())) return;
        yaml.set(key, new ArrayList<>(set));
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save arrivals.yml: " + e.getMessage());
        }
    }
}
