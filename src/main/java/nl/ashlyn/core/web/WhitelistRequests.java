package nl.ashlyn.core.web;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Whitelist requests sent from the website, kept in requests.yml until a leader handles them.
 * Only touch this from the main thread.
 */
public class WhitelistRequests {

    public enum Result { OK, INVALID_NAME, MISSING_REAL_NAME, ALREADY_REQUESTED, ALREADY_WHITELISTED, CLOSED, FULL }

    public record Request(String name, String realName, long time) {}

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int MAX_REAL_NAME_LENGTH = 40;

    private static File file() {
        return new File(Core.getInstance().getDataFolder(), "requests.yml");
    }

    public static List<Request> getRequests() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        List<Request> requests = new ArrayList<>();
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            if (section == null) continue;
            requests.add(new Request(name, section.getString("real-name", ""), section.getLong("time")));
        }
        return requests;
    }

    public static Request find(String name) {
        return getRequests().stream().filter(r -> r.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static Result submit(String name, String realName) {
        if (!CoreConfig.isWhitelistRequestsOpen()) return Result.CLOSED;
        if (name == null || !NAME.matcher(name).matches()) return Result.INVALID_NAME;
        String who = realName == null ? "" : realName.strip();
        if (who.isEmpty()) return Result.MISSING_REAL_NAME;
        if (who.length() > MAX_REAL_NAME_LENGTH) who = who.substring(0, MAX_REAL_NAME_LENGTH);
        if (isWhitelisted(name)) return Result.ALREADY_WHITELISTED;
        if (find(name) != null) return Result.ALREADY_REQUESTED;
        if (getRequests().size() >= CoreConfig.getMaxWhitelistRequests()) return Result.FULL;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        yaml.set(name + ".real-name", who);
        yaml.set(name + ".time", System.currentTimeMillis());
        save(yaml);

        notifyLeaders(name, who);
        return Result.OK;
    }

    public static void remove(String name) {
        Request request = find(name);
        if (request == null) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        yaml.set(request.name(), null);
        save(yaml);
    }

    public static boolean isWhitelisted(String name) {
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            if (name.equalsIgnoreCase(player.getName())) return true;
        }
        return false;
    }

    private static void notifyLeaders(String name, String realName) {
        String message = ChatColor.LIGHT_PURPLE + "Nieuwe whitelist-aanvraag: " + ChatColor.WHITE + name
                + (realName.isEmpty() ? "" : ChatColor.GRAY + " (" + realName + ")")
                + ChatColor.GRAY + ". Bekijk ze met /aanvragen";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || CoreConfig.isLeader(player.getName())) player.sendMessage(message);
        }
        Logger.logCore("New whitelist request from " + ChatColor.GREEN + name);
    }

    private static void save(YamlConfiguration yaml) {
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save requests.yml: " + e.getMessage());
        }
    }
}
