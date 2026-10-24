package nl.ashlyn.core.api;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.utils.Logger;
import nl.ashlyn.core.web.WhitelistRequests;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * The real names of the players, saved in names.yml when a leader accepts their whitelist request.
 * Only touch this from the main thread.
 */
public class RealNames {

    private static File file() {
        return new File(Core.getInstance().getDataFolder(), "names.yml");
    }

    /**
     * Get the real name of a player
     * Falls back to an open whitelist request, then to the Minecraft name
     * @param player The player
     * @return The real name
     */
    public static String get(OfflinePlayer player) {
        String saved = YamlConfiguration.loadConfiguration(file()).getString(player.getUniqueId().toString());
        if (saved != null && !saved.isBlank()) return saved;
        if (player.getName() == null) return "???";
        WhitelistRequests.Request request = WhitelistRequests.find(player.getName());
        if (request != null && !request.realName().isEmpty()) return request.realName();
        return player.getName();
    }

    /**
     * Save the real name of a player
     * @param uuid     The player's UUID
     * @param realName The real name
     */
    public static void set(UUID uuid, String realName) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        yaml.set(uuid.toString(), realName);
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save names.yml: " + e.getMessage());
        }
    }
}
