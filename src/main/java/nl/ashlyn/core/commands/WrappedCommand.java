package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.wrapped.SeasonTracker;
import nl.ashlyn.core.wrapped.WrappedExporter;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * Ashlyn Wrapped, the end of season recap on the website.
 *   /wrapped                 show how many players are being tracked
 *   /wrapped export          write wrapped.json for the website
 *   /wrapped reset confirm   start counting from zero, for just before the season starts
 */
public class WrappedCommand implements CommandExecutor, TabCompleter {

    private final SeasonTracker tracker;
    private final WrappedExporter exporter;

    public WrappedCommand(SeasonTracker tracker, WrappedExporter exporter) {
        this.tracker = tracker;
        this.exporter = exporter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen Wrapped beheren.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "Wrapped houdt " + ChatColor.WHITE + tracker.getStats().size()
                    + ChatColor.LIGHT_PURPLE + " spelers bij." + ChatColor.GRAY + " Exporteer met /wrapped export");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "export" -> {
                try {
                    WrappedExporter.Result result = exporter.export();
                    sender.sendMessage(ChatColor.GREEN + "Wrapped geëxporteerd voor " + result.players() + " spelers naar "
                            + ChatColor.WHITE + "plugins/Core/" + result.file().getName()
                            + ChatColor.GREEN + ". Zet dit bestand in de map public/ van de website.");
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "Exporteren mislukt: " + e.getMessage());
                }
            }
            case "reset" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(ChatColor.RED + "Dit wist alles wat Wrapped tot nu toe heeft bijgehouden. Weet je het zeker? /wrapped reset confirm");
                    return true;
                }
                tracker.reset();
                sender.sendMessage(ChatColor.GREEN + "Wrapped begint opnieuw te tellen.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Gebruik: /wrapped [export | reset confirm]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("export", "reset").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) return List.of("confirm");
        return List.of();
    }
}
