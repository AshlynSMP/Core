package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.RealNames;
import nl.ashlyn.core.arrival.Arrival;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * Sets up and starts the release arrival.
 *   /aankomst                          show how the arrival is set up
 *   /aankomst aan | uit                turn the arrival on or off
 *   /aankomst hok                      set the hok at your position
 *   /aankomst bos <straal> [afstand]   set the forest center at your position
 *   /aankomst start                    spread everyone in the hok over the forest
 *   /aankomst naam <speler> <naam>     set the real name of a player
 *   /aankomst papier <speler>          give a player a new name paper
 *   /aankomst reset <speler>           let a player go through the arrival again
 */
public class ArrivalCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aan", "uit", "hok", "bos", "start", "naam", "papier", "reset");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen de aankomst regelen.");
            return true;
        }

        if (args.length == 0) {
            status(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "aan", "uit" -> {
                boolean on = args[0].equalsIgnoreCase("aan");
                CoreConfig.setArrivalEnabled(on);
                CoreConfig.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "De aankomst staat nu " + (on ? "aan." : "uit."));
            }
            case "hok" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Dit kan alleen in de game.");
                    return true;
                }
                CoreConfig.setHok(player.getLocation());
                CoreConfig.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Het hok staat nu op jouw plek.");
            }
            case "bos" -> forest(sender, args);
            case "start" -> start(sender);
            case "naam" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /aankomst naam <speler> <naam>");
                    return true;
                }
                OfflinePlayer target = find(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Speler " + args[1] + " niet gevonden.");
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                RealNames.set(target.getUniqueId(), name);
                sender.sendMessage(ChatColor.GREEN + "De naam van " + target.getName() + " is nu " + name + ".");
            }
            case "papier" -> {
                Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /aankomst papier <speler>, de speler moet online zijn.");
                    return true;
                }
                Arrival.givePaper(target);
                sender.sendMessage(ChatColor.GREEN + target.getName() + " heeft een papier met " + RealNames.get(target) + " gekregen.");
            }
            case "reset" -> {
                OfflinePlayer target = args.length == 2 ? find(args[1]) : null;
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /aankomst reset <speler>");
                    return true;
                }
                Arrival.setArrived(target.getUniqueId(), false);
                if (target instanceof Player online) Arrival.sendToHok(online);
                sender.sendMessage(ChatColor.GREEN + target.getName() + " gaat opnieuw door de aankomst.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Gebruik: /aankomst [" + String.join(" | ", SUBCOMMANDS) + "]");
        }
        return true;
    }

    private void forest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Dit kan alleen in de game.");
            return;
        }
        int radius;
        int distance;
        try {
            radius = Integer.parseInt(args[1]);
            distance = args.length > 2 ? Integer.parseInt(args[2]) : CoreConfig.getForestMinDistance();
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Gebruik: /aankomst bos <straal> [afstand]");
            return;
        }
        if (radius < 1 || distance < 0) {
            sender.sendMessage(ChatColor.RED + "De straal moet minstens 1 zijn.");
            return;
        }
        CoreConfig.setForest(player.getLocation(), radius, distance);
        CoreConfig.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Het bos is nu een cirkel van " + radius + " blokken om jouw plek, met minstens "
                + distance + " blokken tussen spelers.");
    }

    private void start(CommandSender sender) {
        if (CoreConfig.getHok() == null || CoreConfig.getForestCenter() == null) {
            sender.sendMessage(ChatColor.RED + "Zet eerst het hok en het bos met /aankomst hok en /aankomst bos.");
            return;
        }
        CoreConfig.setArrivalEnabled(true);
        CoreConfig.setArrivalStarted(true);
        CoreConfig.saveConfig();
        int count = Arrival.start();
        sender.sendMessage(ChatColor.GREEN + (count == 1 ? "1 speler wordt" : count + " spelers worden") + " over het bos verspreid."
                + ChatColor.GRAY + " Wie later voor het eerst joint, gaat na een paar seconden ook naar het bos.");
    }

    private void status(CommandSender sender) {
        Location hok = CoreConfig.getHok();
        Location forest = CoreConfig.getForestCenter();
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Aankomst: " + ChatColor.WHITE + (CoreConfig.isArrivalEnabled() ? "aan" : "uit")
                + (CoreConfig.isArrivalStarted() ? ChatColor.GRAY + " (gestart)" : ""));
        sender.sendMessage(ChatColor.GRAY + "Hok: " + ChatColor.WHITE + (hok == null ? "niet gezet"
                : hok.getWorld().getName() + " " + hok.getBlockX() + ", " + hok.getBlockY() + ", " + hok.getBlockZ()));
        sender.sendMessage(ChatColor.GRAY + "Bos: " + ChatColor.WHITE + (forest == null ? "niet gezet"
                : forest.getWorld().getName() + " " + forest.getBlockX() + ", " + forest.getBlockZ()
                + ChatColor.GRAY + " straal " + CoreConfig.getForestRadius() + ", afstand " + CoreConfig.getForestMinDistance()));
        sender.sendMessage(ChatColor.GRAY + "In het hok: " + ChatColor.WHITE + Arrival.waiting().size());
    }

    private static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            if (name.equalsIgnoreCase(player.getName())) return player;
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(player.getName())) return player;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && List.of("naam", "papier", "reset").contains(args[0].toLowerCase())) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
