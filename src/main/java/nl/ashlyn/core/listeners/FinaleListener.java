package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Created by Ashlyn on 26/10/2026
 *
 * Runs the last act in hardcore. Players who die are marked as out.
 * and they come back as spectator. The website greys them out and counts who is left.
 */
public class FinaleListener implements Listener {

    private static final String TITLE = "{\"text\":\"敗󀀀󀀀󀀀󏿿退\",\"shadow_color\":false}";
    private static final int FIREWORKS = 3;

    private final NamespacedKey fireworkKey = new NamespacedKey(Core.getInstance(), "finale_firework");

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!CoreConfig.isFinalAct()) return;
        Player player = event.getEntity();
        if (CoreConfig.isOut(player.getUniqueId())) return;

        CoreConfig.setOut(player.getUniqueId(), System.currentTimeMillis());
        CoreConfig.saveConfig();
        launchFireworks(player.getLocation());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!CoreConfig.isFinalAct() || !CoreConfig.isOut(player.getUniqueId())) return;

        Core plugin = Core.getInstance();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.setGameMode(GameMode.SPECTATOR);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title " + player.getName() + " times 20 80 30");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title " + player.getName() + " title " + TITLE);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private void launchFireworks(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        for (int i = 0; i < FIREWORKS; i++) {
            Location spot = location.clone().add(Math.random() - 0.5, 0.5, Math.random() - 0.5);
            world.spawn(spot, Firework.class, firework -> {
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.RED, Color.fromRGB(0x8B0000))
                        .withFade(Color.fromRGB(0x3A0000))
                        .flicker(true)
                        .build());
                meta.setPower(1);
                firework.setFireworkMeta(meta);
                firework.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
            });
        }
    }
}
