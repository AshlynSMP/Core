package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.listeners.FinaleListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Moves the storyline forward. The website only shows the acts up to the current one,
 * so later act titles stay a secret until the leaders reveal them.
 *   /act                    show the current act
 *   /act <nummer> [titel]   go to an act, optionally (re)naming it
 *   /act info [nummer] <tekst>  set the short description of an act (the current one by default)
 *   /act uit|terug <speler>     mark a player as out in the last act, or bring them back
 */
public class ActCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            int act = CoreConfig.getAct();
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "Act " + act + ": " + ChatColor.WHITE + CoreConfig.getActTitle(act));
            String info = CoreConfig.getActInfo(act);
            if (info != null) sender.sendMessage(ChatColor.GRAY + info);
            return true;
        }

        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen het verhaal verder zetten.");
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            int target = CoreConfig.getAct();
            int from = 1;
            if (args.length > 2 && args[1].matches("\\d+")) {
                target = Integer.parseInt(args[1]);
                from = 2;
            }
            if (args.length <= from || target < 1) {
                sender.sendMessage(ChatColor.RED + "Gebruik: /act info [nummer] <tekst>");
                return true;
            }
            CoreConfig.setActInfo(target, String.join(" ", Arrays.copyOfRange(args, from, args.length)));
            CoreConfig.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Info van act " + target + " opgeslagen.");
            return true;
        }

        if (args[0].equalsIgnoreCase("uit") || args[0].equalsIgnoreCase("terug")) {
            boolean out = args[0].equalsIgnoreCase("uit");
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Gebruik: /act " + args[0].toLowerCase() + " <speler>");
                return true;
            }
            OfflinePlayer target = findPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Speler " + args[1] + " niet gevonden.");
                return true;
            }
            if (out) CoreConfig.setOut(target.getUniqueId(), System.currentTimeMillis());
            else CoreConfig.removeOut(target.getUniqueId());
            CoreConfig.saveConfig();
            if (target.getPlayer() != null && CoreConfig.isFinalAct()) {
                target.getPlayer().setGameMode(out ? GameMode.SPECTATOR : GameMode.SURVIVAL);
            }
            sender.sendMessage(ChatColor.GREEN + target.getName() + (out ? " is nu uitgeschakeld." : " doet weer mee."));
            return true;
        }

        int act;
        try {
            act = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Gebruik: /act <nummer> [titel] of /act info [nummer] <tekst>");
            return true;
        }
        if (act < 1) {
            sender.sendMessage(ChatColor.RED + "Een act begint bij 1.");
            return true;
        }

        if (args.length > 1) CoreConfig.setActTitle(act, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        CoreConfig.setAct(act);
        CoreConfig.saveConfig();
        FinaleListener.applyHardcore();

        List<String> titles = CoreConfig.getActTitles();
        String title = CoreConfig.getActTitle(act);
        sender.sendMessage(ChatColor.GREEN + "Het verhaal staat nu op act " + act + ": " + ChatColor.WHITE + title);
        if (act > titles.size()) {
            sender.sendMessage(ChatColor.GRAY + "Deze act heeft nog geen titel, de website toont ??? tot je er een geeft met /act " + act + " <titel>.");
        }
        return true;
    }

    private OfflinePlayer findPlayer(String name) {
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(player.getName())) return player;
        }
        return null;
    }
}
