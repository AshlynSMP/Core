package nl.ashlyn.core.api;

import nl.ashlyn.core.Core;

import java.util.List;

/**
 * Created by Ashlyn on 05/12/2025
 */
public class CoreConfig {
    /**
     * Get the list of leaders from the config
     * @return List of leader usernames
     */
    public static List<String> getLeaders() {
        return Core.getCoreConfig().getStringList("leaders");
    }

    /**
     * Check if a player is a leader
     * @param username The username to check
     * @return true if the player is a leader, false otherwise
     */
    public static boolean isLeader(String username) {
        return getLeaders().contains(username);
    }

    /**
     * Check if lore feature is enabled
     * @return true if lore is enabled, false otherwise
     */
    public static boolean isLoreEnabled() {
        return Core.getCoreConfig().getBoolean("lore-enabled", false);
    }

    /**
     * Reload the Core configuration
     */
    public static void reloadConfig() {
        Core.getInstance().reloadConfig();
    }
}
