package nl.ashlyn.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Created by Ashlyn on 13/11/2026
 *
 * Opens the live inventory of an online player, only for ops.
 *   /invsee <speler>
 */
public class InvseeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Dit kan alleen in de game.");
            return true;
        }
        if (!player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen ops kunnen dit gebruiken.");
            return true;
        }

        Player target = args.length == 1 ? Bukkit.getPlayerExact(args[0]) : null;
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Gebruik: /invsee <speler>, de speler moet online zijn.");
            return true;
        }

        player.openInventory(target.getInventory());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp() || args.length != 1) return List.of();
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
    }
}
