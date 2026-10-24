package nl.ashlyn.core.commands;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * Reloads config.yml and restarts the web API, so changes work without a server restart.
 */
public class ReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen Core herladen.");
            return true;
        }

        Core.getInstance().reload();
        sender.sendMessage(ChatColor.GREEN + "Core is herladen.");
        return true;
    }
}
