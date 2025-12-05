package nl.ashlyn.core.api;

import nl.ashlyn.core.Core;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Public API for other plugins to interact with AshlynCore
 */
public class CoreAPI {

    /**
     * Get the Core plugin's configuration
     * @return FileConfiguration of the Core plugin
     */
    public static FileConfiguration getConfig() {
        return Core.getCoreConfig();
    }

    public static String getVersion() {
        return Core.getInstance().getDescription().getVersion();
    }
}

