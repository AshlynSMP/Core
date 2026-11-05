package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.limbo.Limbo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Created by Ashlyn on 05/11/2026
 *
 * Sets up the limbo players go to when they die.
 *   /limbo                     show how the limbo is set up
 *   /limbo aan | uit           turn the limbo on or off
 *   /limbo start               set the limbo start at your position
 *   /limbo uitgang [straal]    set the limbo exit at your position
 *   /limbo weg <speler>        send a player out of the limbo to their bed or the world spawn
 */
public class LimboCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aan", "uit", "start", "uitgang", "weg");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen de limbo regelen.");
            return true;
        }

        if (args.length == 0) {
            status(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "aan", "uit" -> {
                boolean on = args[0].equalsIgnoreCase("aan");
                CoreConfig.setLimboEnabled(on);
                CoreConfig.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "De limbo staat nu " + (on ? "aan." : "uit."));
            }
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Dit kan alleen in de game.");
                    return true;
                }
                CoreConfig.setLimboStart(player.getLocation());
                CoreConfig.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Het begin van de limbo staat nu op jouw plek.");
            }
            case "uitgang" -> exit(sender, args);
            case "weg" -> {
                Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /limbo weg <speler>, de speler moet online zijn.");
                    return true;
                }
                Limbo.leave(target);
                sender.sendMessage(ChatColor.GREEN + target.getName() + " is uit de limbo gehaald.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Gebruik: /limbo [" + String.join(" | ", SUBCOMMANDS) + "]");
        }
        return true;
    }

    private void exit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Dit kan alleen in de game.");
            return;
        }
        int radius;
        try {
            radius = args.length > 1 ? Integer.parseInt(args[1]) : CoreConfig.getLimboExitRadius();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Gebruik: /limbo uitgang [straal]");
            return;
        }
        if (radius < 1) {
            sender.sendMessage(ChatColor.RED + "De straal moet minstens 1 zijn.");
            return;
        }
        CoreConfig.setLimboExit(player.getLocation(), radius);
        CoreConfig.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "De uitgang van de limbo staat nu op jouw plek, met een straal van " + radius + " blokken.");
    }

    private void status(CommandSender sender) {
        Location start = CoreConfig.getLimboStart();
        Location exit = CoreConfig.getLimboExit();
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Limbo: " + ChatColor.WHITE + (CoreConfig.isLimboEnabled() ? "aan" : "uit")
                + (CoreConfig.isFinalAct() ? ChatColor.GRAY + " (niet in de laatste act)" : ""));
        sender.sendMessage(ChatColor.GRAY + "Begin: " + ChatColor.WHITE + describe(start));
        sender.sendMessage(ChatColor.GRAY + "Uitgang: " + ChatColor.WHITE + describe(exit)
                + (exit == null ? "" : ChatColor.GRAY + " straal " + CoreConfig.getLimboExitRadius()));
        sender.sendMessage(ChatColor.GRAY + "In de limbo: " + ChatColor.WHITE + Limbo.count());
    }

    private static String describe(Location location) {
        if (location == null) return "niet gezet";
        return location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("weg")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
