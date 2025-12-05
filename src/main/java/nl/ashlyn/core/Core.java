package nl.ashlyn.core;

import nl.ashlyn.core.commands.PluginsCommand;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Core extends JavaPlugin {

    private static Core instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        registerCommands();
        registerListeners();

        Logger.logCore("+=========================================+");
        Logger.logCore("Ashlyn Core Initiated! (Build: v" + getDescription().getVersion() + ")");
        Logger.logCore("+=========================================+");
    }

    @Override
    public void onDisable() {
        Logger.logCore("+=========================================+");
        Logger.logCore("Ashlyn Core Disabled! (Build: v" + getDescription().getVersion() + ")");
        Logger.logCore("+=========================================+");
    }

    /**
     * Get the instance of the Core plugin
     * @return Core instance
     */
    public static Core getInstance() {
        return instance;
    }

    /**
     * Get the Core configuration
     * This method allows other plugins to access the Core config
     * @return The Core plugin's configuration
     */
    public static FileConfiguration getCoreConfig() {
        return instance.getConfig();
    }

    private void registerCommands() {
        registerCommand("ashlynplugins", new PluginsCommand());
    }

    private void registerListeners() {
        // Future listener registrations will go here
    }

    private void registerCommand(String name, CommandExecutor executor) {
        this.getCommand(name).setExecutor(executor);
        Logger.logCore("Registered command: " + org.bukkit.ChatColor.GREEN + name);
    }

    private void registerListener(Listener listener, Plugin plugin) {
        getServer().getPluginManager().registerEvents(listener, plugin);
        Logger.logCore("Registered listener: " + org.bukkit.ChatColor.GREEN + listener.getClass().getSimpleName());}
}
