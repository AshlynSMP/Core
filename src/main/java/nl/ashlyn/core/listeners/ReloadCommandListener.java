package nl.ashlyn.core.listeners;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.commands.JarReloadCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

/**
 * Created by Ashlyn on 27/10/2026
 *
 * Hooks up the /<plugin>reload command that each AshlynSMP plugin lists in its plugin.yml,
 * so the plugins themselves need no code for it.
 */
public class ReloadCommandListener implements Listener {

    public ReloadCommandListener() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) attach(plugin);
        }
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        attach(event.getPlugin());
    }

    private void attach(Plugin plugin) {
        if (plugin == Core.getInstance()) return;
        PluginCommand command = Bukkit.getPluginCommand(plugin.getName().toLowerCase() + "reload");
        if (command == null || command.getPlugin() != plugin) return;
        command.setExecutor(new JarReloadCommand(plugin));
    }
}
