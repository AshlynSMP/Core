package nl.ashlyn.core.commands;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.reload.PluginReloader;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * Reloads config.yml and restarts the web API, so changes work without a server restart.
 * With "jar" it loads the new Core jar instead, together with the plugins that depend on Core.
 */
public class ReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen Core herladen.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("jar")) {
            sender.sendMessage(ChatColor.YELLOW + "Nieuwe jar van Core laden...");
            PluginReloader.reload(Core.getInstance(), sender);
            return true;
        }

        Core.getInstance().reload();
        sender.sendMessage(ChatColor.GREEN + "Core is herladen.");
        return true;
    }
}
