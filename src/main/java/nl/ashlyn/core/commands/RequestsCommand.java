package nl.ashlyn.core.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.web.WhitelistRequests;
import nl.ashlyn.core.web.WhitelistRequests.Request;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Handles the whitelist requests sent from the website.
 *   /aanvragen                  show all open requests
 *   /aanvragen accept <naam>    put the player on the whitelist
 *   /aanvragen deny <naam>      drop the request
 */
public class RequestsCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen aanvragen bekijken.");
            return true;
        }

        if (args.length == 0) {
            list(sender);
            return true;
        }
        if (args.length != 2 || !(args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            sender.sendMessage(ChatColor.RED + "Gebruik: /aanvragen [accept | deny <naam>]");
            return true;
        }

        Request request = WhitelistRequests.find(args[1]);
        if (request == null) {
            sender.sendMessage(ChatColor.RED + "Geen aanvraag gevonden van " + args[1] + ".");
            return true;
        }

        if (args[0].equalsIgnoreCase("deny")) {
            WhitelistRequests.remove(request.name());
            sender.sendMessage(ChatColor.GREEN + "Aanvraag van " + request.name() + " afgewezen.");
            return true;
        }

        accept(sender, request);
        return true;
    }

    // Looks the account up at Mojang first (off the main thread), so a typo doesn't end up on the whitelist
    private void accept(CommandSender sender, Request request) {
        Bukkit.createPlayerProfile(request.name()).update().whenComplete((profile, error) ->
                Bukkit.getScheduler().runTask(Core.getInstance(), () -> {
                    if (error != null || profile == null || profile.getUniqueId() == null || profile.getName() == null) {
                        sender.sendMessage(ChatColor.RED + "Kon " + request.name() + " niet op de whitelist zetten. Bestaat dit Minecraft-account wel?");
                        return;
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + profile.getName());
                    WhitelistRequests.remove(request.name());
                    sender.sendMessage(ChatColor.GREEN + profile.getName() + " staat nu op de whitelist.");
                }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("accept", "deny").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2) {
            return WhitelistRequests.getRequests().stream().map(Request::name)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }

    private void list(CommandSender sender) {
        List<Request> requests = WhitelistRequests.getRequests();
        if (requests.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Er zijn geen open aanvragen.");
            return;
        }

        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Whitelist-aanvragen (" + requests.size() + "):");
        for (Request request : requests) {
            String line = ChatColor.WHITE + request.name()
                    + (request.realName().isEmpty() ? "" : ChatColor.GRAY + " (" + request.realName() + ")")
                    + ChatColor.DARK_GRAY + " · " + ago(request.time());

            if (sender instanceof Player player) {
                TextComponent message = new TextComponent(TextComponent.fromLegacy(ChatColor.GRAY + "- " + line + " "));
                message.addExtra(button("[✔]", ChatColor.GREEN, "/aanvragen accept " + request.name(), "Op de whitelist zetten"));
                message.addExtra(" ");
                message.addExtra(button("[✘]", ChatColor.RED, "/aanvragen deny " + request.name(), "Afwijzen"));
                player.spigot().sendMessage(message);
            } else {
                sender.sendMessage(ChatColor.GRAY + "- " + line);
            }
        }
    }

    private static TextComponent button(String text, ChatColor color, String command, String hover) {
        TextComponent button = new TextComponent(text);
        button.setColor(color.asBungee());
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new ComponentBuilder(hover).create())));
        return button;
    }

    private static String ago(long time) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - time);
        if (minutes < 60) return minutes <= 1 ? "zojuist" : minutes + " min geleden";
        long hours = minutes / 60;
        if (hours < 24) return hours + " uur geleden";
        long days = hours / 24;
        return days == 1 ? "gisteren" : days + " dagen geleden";
    }
}
