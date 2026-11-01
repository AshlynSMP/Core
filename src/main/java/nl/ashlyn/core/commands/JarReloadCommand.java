package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.reload.PluginReloader;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Created by Ashlyn on 27/10/2026
 *
 * The /<plugin>reload command of every AshlynSMP plugin: loads the new jar of that plugin.
 */
public class JarReloadCommand implements CommandExecutor {

    private final Plugin plugin;

    public JarReloadCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen " + plugin.getName() + " herladen.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Nieuwe jar van " + plugin.getName() + " laden...");
        PluginReloader.reload(plugin, sender);
        return true;
    }
}
