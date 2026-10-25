package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Ashlyn on 25/10/2026
 *
 * Puts the server in maintenance, only leaders and ops can join then.
 *   /onderhoud              show if maintenance is on
 *   /onderhoud aan [reden]  turn maintenance on and kick everyone else
 *   /onderhoud uit          turn maintenance off
 */
public class MaintenanceCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aan", "uit");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen onderhoud aanzetten.");
            return true;
        }

        if (args.length == 0) {
            String reason = CoreConfig.getMaintenanceReason();
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "Onderhoud: " + ChatColor.WHITE + (CoreConfig.isMaintenance() ? "aan" : "uit")
                    + (reason != null ? ChatColor.GRAY + " (" + reason + ")" : ""));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "aan" -> {
                String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
                CoreConfig.setMaintenance(true);
                CoreConfig.setMaintenanceReason(reason);
                CoreConfig.saveConfig();
                int kicked = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (canJoin(player.getName(), player.isOp())) continue;
                    player.kickPlayer(kickMessage());
                    kicked++;
                }
                sender.sendMessage(ChatColor.GREEN + "Onderhoud staat nu aan."
                        + (kicked > 0 ? ChatColor.GRAY + " " + (kicked == 1 ? "1 speler is" : kicked + " spelers zijn") + " gekickt." : ""));
            }
            case "uit" -> {
                CoreConfig.setMaintenance(false);
                CoreConfig.setMaintenanceReason(null);
                CoreConfig.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Onderhoud staat nu uit, iedereen kan weer joinen.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Gebruik: /onderhoud [aan [reden] | uit]");
        }
        return true;
    }

    /**
     * Check if a player can join while the server is in maintenance
     * @param name The player name
     * @param op   Whether the player is op
     * @return true if the player can join
     */
    public static boolean canJoin(String name, boolean op) {
        return op || CoreConfig.isLeader(name);
    }

    /**
     * Get the message players see when maintenance keeps them out
     * @return The message
     */
    public static String kickMessage() {
        String reason = CoreConfig.getMaintenanceReason();
        return ChatColor.LIGHT_PURPLE + "Ashlyn SMP is in onderhoud" + ChatColor.GRAY + "\n\n"
                + (reason != null ? reason : "Probeer het later nog eens.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
