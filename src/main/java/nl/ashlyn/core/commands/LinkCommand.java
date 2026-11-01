package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.PlayerLinks;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Links a Minecraft account to a Watchdog account for the player portal.
 *   /koppel                  show which Watchdog account you're linked to
 *   /koppel <code>           link with the code from the portal
 *   /koppel uit              remove your link
 *   /koppel reset <speler>   remove the link of a player (leaders)
 */
public class LinkCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            reset(sender, args[1]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Gebruik: /koppel reset <speler>");
            return true;
        }

        if (args.length == 0) {
            PlayerLinks.Link link = PlayerLinks.find(player.getUniqueId());
            sender.sendMessage(link == null
                    ? ChatColor.GRAY + "Je bent nog niet gekoppeld. Log in op het portaal voor een code."
                    : ChatColor.LIGHT_PURPLE + "Gekoppeld aan Watchdog: " + ChatColor.WHITE + link.watchdog());
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Gebruik: /koppel <code> | uit");
            return true;
        }

        if (args[0].equalsIgnoreCase("uit")) {
            sender.sendMessage(PlayerLinks.unlink(player.getUniqueId())
                    ? ChatColor.GREEN + "Je account is ontkoppeld."
                    : ChatColor.GRAY + "Je account was niet gekoppeld.");
            return true;
        }

        PlayerLinks.Link link = PlayerLinks.redeem(args[0], player.getUniqueId());
        if (link == null) {
            sender.sendMessage(ChatColor.RED + "Die code klopt niet of is verlopen.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Gekoppeld aan Watchdog: " + ChatColor.WHITE + link.watchdog());
        return true;
    }

    private void reset(CommandSender sender, String name) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen koppelingen weghalen.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        sender.sendMessage(PlayerLinks.unlink(target.getUniqueId())
                ? ChatColor.GREEN + "Koppeling van " + name + " weggehaald."
                : ChatColor.RED + name + " is niet gekoppeld.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            boolean leader = !(sender instanceof Player player) || player.isOp() || CoreConfig.isLeader(player.getName());
            List<String> options = leader ? List.of("uit", "reset") : List.of("uit");
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
