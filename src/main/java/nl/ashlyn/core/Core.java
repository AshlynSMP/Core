package nl.ashlyn.core;

import nl.ashlyn.core.commands.ActCommand;
import nl.ashlyn.core.commands.ArrivalCommand;
import nl.ashlyn.core.commands.EventCommand;
import nl.ashlyn.core.commands.PluginsCommand;
import nl.ashlyn.core.commands.ReloadCommand;
import nl.ashlyn.core.commands.RequestsCommand;
import nl.ashlyn.core.commands.WrappedCommand;
import nl.ashlyn.core.listeners.ArrivalListener;
import nl.ashlyn.core.listeners.RequestsReminder;
import nl.ashlyn.core.utils.Logger;
import nl.ashlyn.core.web.WebServer;
import nl.ashlyn.core.wrapped.SeasonTracker;
import nl.ashlyn.core.wrapped.WrappedExporter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Core extends JavaPlugin {

    private static Core instance;
    private WebServer webServer;
    private SeasonTracker seasonTracker;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        seasonTracker = new SeasonTracker(this);
        seasonTracker.start();
        registerCommands();
        registerListeners();

        webServer = new WebServer(this);
        webServer.start();

        Logger.logCore("+=========================================+");
        Logger.logCore("Ashlyn Core Initiated! (Build: v" + getDescription().getVersion() + ")");
        Logger.logCore("+=========================================+");
    }

    @Override
    public void onDisable() {
        if (webServer != null) webServer.stop();
        if (seasonTracker != null) seasonTracker.stop();

        Logger.logCore("+=========================================+");
        Logger.logCore("Ashlyn Core Disabled! (Build: v" + getDescription().getVersion() + ")");
        Logger.logCore("+=========================================+");
    }

    /**
     * Reload config.yml and restart the web API with the new settings
     */
    public void reload() {
        reloadConfig();
        if (webServer != null) webServer.stop();
        webServer = new WebServer(this);
        webServer.start();
        Logger.logCore("Reloaded config and web API");
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
        registerCommand("corereload", new ReloadCommand());
        EventCommand eventCommand = new EventCommand();
        registerCommand("event", eventCommand);
        getCommand("event").setTabCompleter(eventCommand);
        registerCommand("act", new ActCommand());
        RequestsCommand requestsCommand = new RequestsCommand();
        registerCommand("aanvragen", requestsCommand);
        getCommand("aanvragen").setTabCompleter(requestsCommand);
        WrappedCommand wrappedCommand = new WrappedCommand(seasonTracker, new WrappedExporter(this, seasonTracker));
        registerCommand("wrapped", wrappedCommand);
        getCommand("wrapped").setTabCompleter(wrappedCommand);
        ArrivalCommand arrivalCommand = new ArrivalCommand();
        registerCommand("aankomst", arrivalCommand);
        getCommand("aankomst").setTabCompleter(arrivalCommand);
    }

    private void registerListeners() {
        registerListener(new RequestsReminder(), this);
        registerListener(new ArrivalListener(), this);
    }

    private void registerCommand(String name, CommandExecutor executor) {
        this.getCommand(name).setExecutor(executor);
        Logger.logCore("Registered command: " + org.bukkit.ChatColor.GREEN + name);
    }

    private void registerListener(Listener listener, Plugin plugin) {
        getServer().getPluginManager().registerEvents(listener, plugin);
        Logger.logCore("Registered listener: " + org.bukkit.ChatColor.GREEN + listener.getClass().getSimpleName());}
}
