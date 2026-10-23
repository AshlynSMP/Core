package nl.ashlyn.core.commands;

import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.ServerEvent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Lets the leaders plan events. The website counts down to the next one and keeps
 * every event that has started on its timeline.
 *   /event                                   show the next event
 *   /event list                              show all planned events
 *   /event set <dd-mm-yyyy> <hh:mm> <naam>   plan an event on a date and time
 *   /event in <1d2h30m> <naam>               plan an event in a while from now
 *   /event recap <tekst>                     write what happened at the last event
 *   /event clear                             cancel the next planned event
 *   /event remove <naam>                     remove an event from the timeline
 */
public class EventCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("EEEE d MMMM 'om' HH:mm", Locale.of("nl", "NL"));
    private static final Pattern DURATION = Pattern.compile("(\\d+)([dhm])");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showEvent(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            listEvents(sender);
            return true;
        }

        if (sender instanceof Player player && !CoreConfig.isLeader(player.getName()) && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Alleen de leiders kunnen events aanpassen.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 4) return usage(sender);
                try {
                    LocalDateTime local = LocalDateTime.parse(args[1] + " " + args[2], INPUT);
                    setEvent(sender, join(args, 3), local.atZone(zone()).toInstant().toEpochMilli());
                } catch (DateTimeParseException e) {
                    sender.sendMessage(ChatColor.RED + "Ongeldige datum of tijd. Gebruik bijvoorbeeld: /event set 30-10-2026 20:00 De mist trekt op");
                }
            }
            case "in" -> {
                if (args.length < 3) return usage(sender);
                long millis = parseDuration(args[1]);
                if (millis <= 0) {
                    sender.sendMessage(ChatColor.RED + "Ongeldige duur. Gebruik bijvoorbeeld: 2h, 1d12h of 45m");
                    return true;
                }
                setEvent(sender, join(args, 2), System.currentTimeMillis() + millis);
            }
            case "recap" -> {
                if (args.length < 2) return usage(sender);
                ServerEvent last = CoreConfig.getLastStartedEvent();
                if (last == null) {
                    sender.sendMessage(ChatColor.RED + "Er is nog geen event geweest om een recap voor te schrijven.");
                    return true;
                }
                replace(last, last.withRecap(join(args, 1)));
                sender.sendMessage(ChatColor.GREEN + "Recap opgeslagen voor " + ChatColor.WHITE + last.name() + ChatColor.GREEN + ".");
            }
            case "clear" -> {
                ServerEvent next = CoreConfig.getEvents().stream().filter(e -> !e.hasStarted()).findFirst().orElse(null);
                if (next == null) {
                    sender.sendMessage(ChatColor.RED + "Er staat geen event gepland.");
                    return true;
                }
                replace(next, null);
                sender.sendMessage(ChatColor.GREEN + "Event " + ChatColor.WHITE + next.name() + ChatColor.GREEN + " afgezegd.");
            }
            case "remove" -> {
                if (args.length < 2) return usage(sender);
                String name = join(args, 1);
                ServerEvent event = CoreConfig.getEvents().stream().filter(e -> e.name().equalsIgnoreCase(name)).findFirst().orElse(null);
                if (event == null) {
                    sender.sendMessage(ChatColor.RED + "Geen event gevonden met de naam " + name + ".");
                    return true;
                }
                replace(event, null);
                sender.sendMessage(ChatColor.GREEN + "Event " + ChatColor.WHITE + event.name() + ChatColor.GREEN + " verwijderd.");
            }
            default -> {
                return usage(sender);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(new String[]{"list", "set", "in", "recap", "clear", "remove"}).filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
            String typed = join(args, 1).toLowerCase();
            return CoreConfig.getEvents().stream().map(ServerEvent::name).filter(n -> n.toLowerCase().startsWith(typed)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) return List.of(ZonedDateTime.now(zone()).format(DATE));
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return List.of("20:00");
        if (args.length == 2 && args[0].equalsIgnoreCase("in")) return List.of("30m", "1h", "1d");
        return List.of();
    }

    private void showEvent(CommandSender sender) {
        ServerEvent next = CoreConfig.getNextEvent();
        if (next == null) {
            sender.sendMessage(ChatColor.GRAY + "Er staat geen event gepland.");
            return;
        }
        sender.sendMessage(ChatColor.LIGHT_PURPLE + (next.hasStarted() ? "Nu bezig: " : "Volgend event: ") + ChatColor.WHITE + next.name()
                + ChatColor.GRAY + " (" + format(next.time()) + ")");
    }

    private void listEvents(CommandSender sender) {
        List<ServerEvent> planned = CoreConfig.getEvents().stream().filter(e -> !e.hasStarted()).toList();
        if (planned.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Er staan geen events gepland.");
            return;
        }
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Geplande events:");
        for (ServerEvent event : planned) {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + event.name() + ChatColor.GRAY + " (" + format(event.time()) + ")");
        }
    }

    // Swaps one event for another (or removes it when replacement is null) and saves
    private void replace(ServerEvent event, ServerEvent replacement) {
        List<ServerEvent> events = new ArrayList<>(CoreConfig.getEvents());
        int index = events.indexOf(event);
        if (replacement == null) events.remove(index);
        else events.set(index, replacement);
        CoreConfig.setEvents(events);
        CoreConfig.saveConfig();
    }

    private void setEvent(CommandSender sender, String name, long time) {
        CoreConfig.addEvent(name, time);
        CoreConfig.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Event gezet: " + ChatColor.WHITE + name
                + ChatColor.GREEN + " op " + format(time) + ". De website telt nu af.");
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Gebruik: /event [list | set <dd-mm-yyyy> <hh:mm> <naam> | in <duur> <naam> | recap <tekst> | clear | remove <naam>]");
        return true;
    }

    private static long parseDuration(String input) {
        Matcher matcher = DURATION.matcher(input.toLowerCase());
        long millis = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) return -1;
            long amount = Long.parseLong(matcher.group(1));
            millis += switch (matcher.group(2)) {
                case "d" -> amount * 86_400_000L;
                case "h" -> amount * 3_600_000L;
                default -> amount * 60_000L;
            };
            end = matcher.end();
        }
        return end == input.length() ? millis : -1;
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static String format(long time) {
        return Instant.ofEpochMilli(time).atZone(zone()).format(OUTPUT);
    }

    private static ZoneId zone() {
        return ZoneId.of(CoreConfig.getEventTimezone());
    }
}
