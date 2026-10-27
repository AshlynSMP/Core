package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ashlyn on 26/10/2026
 *
 * Runs the last act in hardcore. Players who die are marked as out.
 * and they come back as spectator. The website greys them out and counts who is left.
 */
public class FinaleListener implements Listener {

    private static final String TITLE = "{\"text\":\"敗󀀀󀀀󀀀󏿿退\",\"shadow_color\":false}";
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xFF1A1A), 2.2f);
    private static final Particle.DustOptions DARK_RED = new Particle.DustOptions(Color.fromRGB(0x8B0000), 1.6f);
    private static final int BURST_POINTS = 140;
    private static final double BURST_RADIUS = 3.5;

    public FinaleListener() {
        applyHardcore();
    }

    /**
     * Turn hardcore on in every world during the last act, and off before it
     */
    public static void applyHardcore() {
        boolean hardcore = CoreConfig.isFinalAct();
        for (World world : Bukkit.getWorlds()) world.setHardcore(hardcore);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!CoreConfig.isFinalAct() || player.getGameMode() == GameMode.SPECTATOR) return;
        if (event.getFinalDamage() < player.getHealth() || holdsTotem(player)) return;

        event.setCancelled(true);
        eliminate(player, event.getDamageSource());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!CoreConfig.isFinalAct() || player.getHealth() > 0) return;

        Location spot = player.getLocation();
        markOut(player);
        burst(spot);
        Core plugin = Core.getInstance();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.isDead()) player.spigot().respawn();
            player.teleport(spot);
            becomeSpectator(player);
        });
    }

    private void eliminate(Player player, DamageSource source) {
        Location spot = player.getLocation();
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) drops.add(item);
        }
        int exp = Math.min(100, player.getLevel() * 7);

        PlayerDeathEvent death = new PlayerDeathEvent(player, source, drops, exp, player.getName() + " died");
        Bukkit.getPluginManager().callEvent(death);

        World world = spot.getWorld();
        if (!death.getKeepInventory()) {
            inventory.clear();
            for (ItemStack item : death.getDrops()) world.dropItemNaturally(spot, item);
        }
        if (!death.getKeepLevel()) {
            player.setLevel(0);
            player.setExp(0);
            if (death.getDroppedExp() > 0) world.spawn(spot, ExperienceOrb.class, orb -> orb.setExperience(death.getDroppedExp()));
        }
        if (death.getDeathMessage() != null && !death.getDeathMessage().isEmpty()) Bukkit.broadcastMessage(death.getDeathMessage());

        player.incrementStatistic(Statistic.DEATHS);
        Player killer = source.getCausingEntity() instanceof Player p && !p.equals(player) ? p : null;
        if (killer != null) killer.incrementStatistic(Statistic.PLAYER_KILLS);

        markOut(player);
        burst(spot);
        becomeSpectator(player);
    }

    private void becomeSpectator(Player player) {
        player.setFireTicks(0);
        player.setFallDistance(0);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SPECTATOR);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1, 1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title " + player.getName() + " times 0 80 30");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title " + player.getName() + " title " + TITLE);
    }

    private void markOut(Player player) {
        if (CoreConfig.isOut(player.getUniqueId())) return;
        CoreConfig.setOut(player.getUniqueId(), System.currentTimeMillis());
        CoreConfig.saveConfig();
    }

    private boolean holdsTotem(Player player) {
        PlayerInventory inventory = player.getInventory();
        return inventory.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || inventory.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    private void burst(Location spot) {
        World world = spot.getWorld();
        if (world == null) return;
        Location center = spot.clone().add(0, 1, 0);
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 4f, 1f);
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 4f, 1f);

        for (int i = 0; i < BURST_POINTS; i++) {
            double y = 1 - 2 * (i + 0.5) / BURST_POINTS;
            double ring = Math.sqrt(1 - y * y);
            double angle = Math.PI * (3 - Math.sqrt(5)) * i;
            Vector direction = new Vector(Math.cos(angle) * ring, y, Math.sin(angle) * ring);
            for (double r = 0.6; r <= BURST_RADIUS; r += 1.1) {
                Location point = center.clone().add(direction.clone().multiply(r));
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, r > 2 ? DARK_RED : RED, true);
            }
        }
    }
}
